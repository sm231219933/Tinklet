package com.tinklet.bharatdatingapp.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpVerificationScreen(
    destination: String, // Can be Phone or Email
    isEmail: Boolean = false,
    onVerified: () -> Unit,
    onResendOtp: () -> Unit,
    onBack: () -> Unit
) {
    var otp by remember { mutableStateOf("") }
    
    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text("Verification") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Verify ${if (isEmail) "Email" else "Number"}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isEmail) 
                        "📧 Check OTP sent to $destination" 
                    else 
                        "💬 Check OTP on WhatsApp sent to +91 $destination",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = otp,
                onValueChange = { if (it.length <= 6) otp = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("6-Digit OTP") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = { if (otp.length == 6) onVerified() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = otp.length == 6,
                shape = MaterialTheme.shapes.medium
            ) {
                Text("Verify & Continue", fontSize = 18.sp)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            TextButton(onClick = onResendOtp) {
                Text("Didn't receive? Resend OTP", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
