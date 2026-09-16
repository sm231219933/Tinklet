package com.tinklet.bharatdatingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.VideocamOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import org.webrtc.SurfaceViewRenderer

@Composable
fun CallScreen(
    remoteUserId: String,
    onEndCall: () -> Unit
) {
    var isMicOn by remember { mutableStateOf(true) }
    var isVideoOn by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // REMOTE VIDEO (Full Screen)
        AndroidView(
            factory = { context ->
                SurfaceViewRenderer(context).apply {
                    init(null, null) 
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // LOCAL VIDEO (Floating Window)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
                .size(width = 120.dp, height = 180.dp)
                .background(Color.DarkGray, RoundedCornerShape(16.dp))
        ) {
            AndroidView(
                factory = { context ->
                    SurfaceViewRenderer(context).apply {
                        init(null, null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // BOTTOM CONTROLS
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 60.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { isMicOn = !isMicOn },
                modifier = Modifier.size(56.dp).background(if (isMicOn) Color.White.copy(0.2f) else Color.Red, CircleShape)
            ) {
                Icon(if (isMicOn) Icons.Rounded.Mic else Icons.Rounded.MicOff, null, tint = Color.White)
            }

            IconButton(
                onClick = onEndCall,
                modifier = Modifier.size(72.dp).background(Color.Red, CircleShape)
            ) {
                Icon(Icons.Rounded.CallEnd, null, tint = Color.White, modifier = Modifier.size(32.dp))
            }

            IconButton(
                onClick = { isVideoOn = !isVideoOn },
                modifier = Modifier.size(56.dp).background(if (isVideoOn) Color.White.copy(0.2f) else Color.Red, CircleShape)
            ) {
                Icon(if (isVideoOn) Icons.Rounded.Videocam else Icons.Rounded.VideocamOff, null, tint = Color.White)
            }
        }
        
        Text(
            text = "Calling $remoteUserId...",
            color = Color.White.copy(0.7f),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 80.dp),
            fontSize = 18.sp
        )
    }
}
