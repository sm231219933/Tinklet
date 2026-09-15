package com.tinklet.bharatdatingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun PhotoVerificationOnboarding(onContinue: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1A1A1A), Color(0xFF000000))))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Face,
            contentDescription = null,
            tint = Color(0xFFFE3C72),
            modifier = Modifier.size(80.dp)
        )
        
        Spacer(Modifier.height(24.dp))
        
        Text(
            "Selfie Verification",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        
        Text(
            "Complete these 3 simple steps to verify your identity and protect our community.",
            fontSize = 14.sp,
            color = Color.White.copy(0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )

        Spacer(Modifier.height(40.dp))

        VerificationStepItem(
            number = "1",
            title = "Align Your Face",
            description = "Place your face inside the oval frame shown on the camera.",
            icon = Icons.Rounded.Face
        )

        VerificationStepItem(
            number = "2",
            title = "Blink to Verify",
            description = "Blink both eyes when prompted to prove you are a real person.",
            icon = Icons.Rounded.Visibility
        )

        VerificationStepItem(
            number = "3",
            title = "Auto-Capture",
            description = "Stay still for 3 seconds after the blink for a high-quality photo.",
            icon = Icons.Rounded.CheckCircle
        )

        Spacer(Modifier.height(56.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE3C72)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Continue to Camera", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VerificationStepItem(number: String, title: String, description: String, icon: ImageVector) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(Color.White.copy(0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold)
        }
        
        Spacer(Modifier.width(16.dp))
        
        Column {
            Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text(description, color = Color.White.copy(0.6f), fontSize = 12.sp)
        }
    }
}
