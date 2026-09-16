package com.tinklet.bharatdatingapp.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Camera
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun PhotoCaptureScreen(
    isUpdate: Boolean = false,
    onPhotoCaptured: (Uri) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var showOnboarding by remember { mutableStateOf(true) }
    
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }

    if (showOnboarding) {
        PhotoVerificationOnboarding(onContinue = { showOnboarding = false })
        return
    }

    if (!hasCameraPermission) {
        Column(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Camera permission is required for identity verification", color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(24.dp))
            Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                Text("Grant Permission")
            }
        }
        return
    }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var isFaceDetected by remember { mutableStateOf(false) }
    var isBlinked by remember { mutableStateOf(false) } // Liveness check
    var isCapturing by remember { mutableStateOf(false) }
    var countdown by remember { mutableStateOf(-1) }
    var isCaptureSuccess by remember { mutableStateOf(false) } 
    var statusMessage by remember { mutableStateOf("Step 1: Fit your face in the OVAL") }

    val faceDetector = remember {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .build()
        FaceDetection.getClient(options)
    }

    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val scope = rememberCoroutineScope()

    fun takePhoto(capture: ImageCapture) {
        if (isCapturing) return
        
        // FINAL SECURITY CHECK: Must be detected right NOW
        if (!isFaceDetected) {
            statusMessage = "Face moved! Align again"
            isBlinked = false
            countdown = -1
            return
        }

        isCapturing = true
        
        val photoFile = File(context.cacheDir, "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(outputOptions, ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                isCaptureSuccess = true
                scope.launch {
                    delay(2000)
                    onPhotoCaptured(Uri.fromFile(photoFile))
                }
            }
            override fun onError(e: ImageCaptureException) { 
                Log.e("Camera", "Capture fail", e)
                isCapturing = false
                countdown = -1
            }
        })
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor = ContextCompat.getMainExecutor(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder().build()
                    imageCapture = capture

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                        val image = imageProxy.image
                        if (image != null) {
                            val inputImage = InputImage.fromMediaImage(image, rotationDegrees)
                            faceDetector.process(inputImage)
                                .addOnSuccessListener { faces ->
                                    if (faces.isNotEmpty()) {
                                        val face = faces[0]
                                        val bounds = face.boundingBox
                                        val imageWidth = inputImage.width
                                        val imageHeight = inputImage.height
                                        
                                        // Face must be large enough and centered
                                        val isCentered = bounds.centerX() > imageWidth * 0.25 && 
                                                       bounds.centerX() < imageWidth * 0.75 &&
                                                       bounds.centerY() > imageHeight * 0.25 &&
                                                       bounds.centerY() < imageHeight * 0.75
                                        
                                        isFaceDetected = isCentered
                                        
                                        if (isCentered && countdown == -1) {
                                            val leftEye = face.leftEyeOpenProbability ?: 1.0f
                                            val rightEye = face.rightEyeOpenProbability ?: 1.0f
                                            
                                            if (leftEye < 0.25f && rightEye < 0.25f && !isBlinked) {
                                                isBlinked = true
                                                scope.launch {
                                                    // Start 3-2-1 Countdown
                                                    for (i in 3 downTo 1) {
                                                        countdown = i
                                                        statusMessage = "Ready! Capturing in $i..."
                                                        delay(1000)
                                                    }
                                                    countdown = 0
                                                    statusMessage = "Smiling? CLICK!"
                                                    takePhoto(capture)
                                                }
                                            } else if (!isBlinked) {
                                                statusMessage = "Step 2: Now BLINK both eyes"
                                            }
                                        } else if (countdown == -1) {
                                            statusMessage = "Step 1: Fit face inside the OVAL"
                                            isBlinked = false
                                        }
                                    } else {
                                        isFaceDetected = false
                                        isBlinked = false
                                        if (countdown == -1) statusMessage = "No face detected"
                                    }
                                }
                                .addOnCompleteListener { imageProxy.close() }
                        } else {
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, capture, imageAnalysis)
                    } catch (e: Exception) { Log.e("Camera", "Binding failed", e) }
                }, executor)
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // OVAL GUIDE OVERLAY
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val ovalWidth = canvasWidth * 0.75f
            val ovalHeight = ovalWidth * 1.3f
            
            drawOval(
                color = when {
                    isBlinked -> Color.Green
                    isFaceDetected -> Color.Yellow
                    else -> Color.White.copy(alpha = 0.5f)
                },
                topLeft = androidx.compose.ui.geometry.Offset(
                    (canvasWidth - ovalWidth) / 2,
                    (canvasHeight - ovalHeight) / 2 - 50.dp.toPx()
                ),
                size = androidx.compose.ui.geometry.Size(ovalWidth, ovalHeight),
                style = Stroke(width = 4.dp.toPx())
            )
        }

        if (!isCaptureSuccess) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Color.Black.copy(0.7f),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text(
                        statusMessage, 
                        color = Color.White,
                        fontWeight = FontWeight.Bold, 
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
                
                if (countdown > 0) {
                    Text(
                        countdown.toString(),
                        fontSize = 120.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(top = 100.dp)
                    )
                }
            }
        }

        if (isCaptureSuccess) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.8f)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(120.dp))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = if (isUpdate) "Profile Photo Change Successful!" else "Identity Verified!", 
                        color = Color.White, 
                        fontSize = 28.sp, 
                        fontWeight = FontWeight.Black,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                    if (!isUpdate) {
                        Spacer(Modifier.height(8.dp))
                        Text("Creating your premium profile...", color = Color.White.copy(0.7f))
                    }
                }
            }
        }
    }
}
