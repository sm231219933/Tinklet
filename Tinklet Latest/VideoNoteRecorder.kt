package com.tinklet.bharatdatingapp.utils

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File

class VideoNoteRecorder(
    private val context: Context,
    private val previewView: PreviewView,
    private val lifecycleOwner: LifecycleOwner
) {
    private var recording: Recording? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var startTime: Long = 0

    companion object {
        const val MAX_DURATION_SECONDS = 30 // Telegram jaisa short video note
    }

    fun setupCamera(onReady: () -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.SD))
                .build()

            videoCapture = VideoCapture.withOutput(recorder)

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner, cameraSelector, preview, videoCapture
                )
                onReady()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun startRecording(onFinished: (File?, Int) -> Unit) {
        val videoCap = videoCapture ?: return
        val fileName = "video_note_${System.currentTimeMillis()}.mp4"
        val outputFile = File(context.cacheDir, fileName)

        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        startTime = System.currentTimeMillis()

        recording = videoCap.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    val durationSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    if (!event.hasError()) {
                        onFinished(outputFile, durationSeconds)
                    } else {
                        onFinished(null, 0)
                    }
                }
            }
    }

    fun stopRecording() {
        recording?.stop()
        recording = null
    }
}
