package com.tinklet.bharatdatingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tinklet.bharatdatingapp.utils.GeographyUtils

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.api.ResolvableApiException
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    discoveryViewModel: com.tinklet.bharatdatingapp.ui.viewmodel.DiscoveryViewModel,
    initialName: String,
    initialEmail: String,
    initialPhone: String,
    initialPassword: String,
    initialCountry: String,
    initialState: String,
    isSignUpMode: Boolean,
    onContinue: (String, Int, String, String, String, String, String, String, String) -> Unit,
    onFetchEmail: () -> Unit,
    onFetchPhone: () -> Unit,
    onToggleMode: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Data states
    var name by remember { mutableStateOf(initialName) }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Select Gender") }
    var email by remember { mutableStateOf(initialEmail) }
    var password by remember { mutableStateOf(initialPassword) }
    var country by remember { mutableStateOf(initialCountry.ifBlank { "Select Country" }) }
    var state by remember { mutableStateOf(initialState.ifBlank { "Select State" }) }
    var phone by remember { mutableStateOf(initialPhone) }
    var referralCodeUsed by remember { mutableStateOf("") }
    var agreedToTerms by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    val isAuthLoading by discoveryViewModel.isAuthLoading.collectAsStateWithLifecycle()
    val isFetchingIdentity by discoveryViewModel.isFetchingIdentity.collectAsStateWithLifecycle()
    val isDetectingLocation by discoveryViewModel.isDetectingLocation.collectAsStateWithLifecycle()
    
    val fetchedEmailState by discoveryViewModel.fetchedEmail.collectAsStateWithLifecycle()
    val fetchedPhoneState by discoveryViewModel.fetchedPhone.collectAsStateWithLifecycle()
    val fetchedCountryState by discoveryViewModel.fetchedCountry.collectAsStateWithLifecycle()
    val fetchedStateState by discoveryViewModel.fetchedState.collectAsStateWithLifecycle()

    // Sync with external updates
    LaunchedEffect(initialEmail, fetchedEmailState) {
        if (fetchedEmailState.isNotBlank()) {
            email = fetchedEmailState
        } else if (initialEmail.isNotBlank()) {
            email = initialEmail
        }
    }
    LaunchedEffect(initialPhone, fetchedPhoneState) {
        if (fetchedPhoneState.isNotBlank()) {
            phone = fetchedPhoneState
        } else if (initialPhone.isNotBlank()) {
            phone = initialPhone
        }
    }
    LaunchedEffect(initialCountry, fetchedCountryState) {
        if (fetchedCountryState.isNotBlank()) {
            country = fetchedCountryState
        } else if (initialCountry.isNotBlank()) {
            country = initialCountry
        }
    }
    LaunchedEffect(initialState, fetchedStateState) {
        if (fetchedStateState.isNotBlank()) {
            state = fetchedStateState
        } else if (initialState.isNotBlank()) {
            state = initialState
        }
    }

    val gpsSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            discoveryViewModel.detectUserLocation(
                onResolutionRequired = { /* Should not happen twice */ },
                onFail = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            discoveryViewModel.detectUserLocation(
                onResolutionRequired = { res ->
                    try {
                        gpsSettingsLauncher.launch(IntentSenderRequest.Builder(res.resolution.intentSender).build())
                    } catch (e: Exception) { }
                },
                onFail = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            )
        } else {
            Toast.makeText(context, "Location permission is required", Toast.LENGTH_SHORT).show()
        }
    }

    var genderExpanded by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        modifier = Modifier.fillMaxSize()
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(Brush.verticalGradient(listOf(Color(0xFFFE3C72), Color(0xFFFF5252))))
                    .imePadding()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(40.dp))
                
                // LOGO & HEADER
                Text("Tinklet", fontSize = 64.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("Premium Global Dating", color = Color.White.copy(0.9f), fontSize = 16.sp, letterSpacing = 2.sp)
                
                Spacer(Modifier.height(32.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .background(Color.White.copy(0.1f), RoundedCornerShape(24.dp))
                        .padding(20.dp)
                ) {
                    Text(if(isSignUpMode) "Create Account" else "Sign In", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(if(isSignUpMode) "Join our verified community" else "Welcome back to Tinklet", fontSize = 12.sp, color = Color.White.copy(0.7f))
                    
                    Spacer(Modifier.height(24.dp))

                    // EMAIL (Click to fetch in Signup, Manual in Login)
                    AuthInputField(
                        label = "Email Address", 
                        value = email, 
                        onValueChange = { if (!isSignUpMode) email = it }, 
                        icon = Icons.Rounded.Email, 
                        enabled = !isSignUpMode,
                        onClick = if (isSignUpMode) onFetchEmail else null,
                        isLoading = (email.isBlank() && isSignUpMode) || isFetchingIdentity
                    )
                    
                    if (isSignUpMode) {
                        Spacer(Modifier.height(12.dp))
                        // PHONE (Click to fetch)
                        AuthInputField(
                            label = "Mobile Number", 
                            value = phone, 
                            onValueChange = { }, 
                            icon = Icons.Rounded.Phone, 
                            enabled = false,
                            onClick = onFetchPhone,
                            isLoading = isFetchingIdentity
                        )
                    }

                    Spacer(Modifier.height(12.dp))
                    AuthInputField("Password (6+ chars)", password, { password = it }, Icons.Rounded.Lock)
                    
                    if (isSignUpMode) {
                        Spacer(Modifier.height(24.dp))
                        Text("Personal Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))

                        AuthInputField("Full Name", name, { name = it }, Icons.Rounded.Person)
                        Spacer(Modifier.height(12.dp))
                        AuthInputField("Age", age, { if(it.length <= 2) age = it }, Icons.Rounded.Cake, isNumber = true)
                        
                        Spacer(Modifier.height(12.dp))

                        // GENDER
                        ExposedDropdownMenuBox(expanded = genderExpanded, onExpandedChange = { genderExpanded = !genderExpanded }) {
                            OutlinedTextField(
                                value = gender, onValueChange = {}, readOnly = true, 
                                label = { Text("Gender", color = Color.White) }, 
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = genderExpanded) }, 
                                modifier = Modifier.menuAnchor().fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color.White,
                                    unfocusedBorderColor = Color.White.copy(0.5f),
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            ExposedDropdownMenu(expanded = genderExpanded, onDismissRequest = { genderExpanded = false }) {
                                listOf("Male", "Female", "Other").forEach { g -> 
                                    DropdownMenuItem(text = { Text(g) }, onClick = { gender = g; genderExpanded = false }) 
                                }
                            }
                        }

                        Spacer(Modifier.height(24.dp))
                        Text("Location Details", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        
                        Spacer(Modifier.height(16.dp))

                        AuthInputField(
                            label = "Country", 
                            value = if (isDetectingLocation && country == "Select Country") "Detecting..." else country, 
                            onValueChange = {}, 
                            icon = Icons.Rounded.Public,
                            enabled = false,
                            isLoading = isDetectingLocation,
                            onClick = {
                                if (!isDetectingLocation) {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            }
                        )

                        Spacer(Modifier.height(12.dp))

                        AuthInputField(
                            label = "State", 
                            value = if (isDetectingLocation && state == "Select State") "Detecting..." else state, 
                            onValueChange = {}, 
                            icon = Icons.Rounded.LocationCity,
                            enabled = false,
                            isLoading = isDetectingLocation,
                            onClick = {
                                if (!isDetectingLocation) {
                                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                }
                            }
                        )

                        Spacer(Modifier.height(24.dp))
                        Text("Promotions", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        AuthInputField("Referral Code (Optional)", referralCodeUsed, { referralCodeUsed = it }, Icons.Rounded.ConfirmationNumber)
                    }

                    Spacer(Modifier.height(32.dp))

                    // TERMS CHECKBOX - DIRECTLY ABOVE BUTTON
                    if (isSignUpMode) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                            Checkbox(
                                checked = agreedToTerms, 
                                onCheckedChange = { agreedToTerms = it },
                                colors = CheckboxDefaults.colors(uncheckedColor = Color.White, checkedColor = Color.White, checkmarkColor = Color.Red)
                            )
                            Text(
                                text = "I agree to Tinklet's Terms & Privacy Policy", 
                                color = Color.White, 
                                fontSize = 12.sp,
                                modifier = Modifier.clickable { showTermsDialog = true }
                            )
                        }
                    }
                    
                    Button(
                        onClick = { 
                            if (isSignUpMode) {
                                // SIGNUP LOGIC
                                scope.launch {
                                    val error = discoveryViewModel.checkRegistrationValidity(email, phone)
                                    if (error != null) {
                                        Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                                        if (error.contains("Email already registered")) {
                                            onToggleMode(false)
                                        }
                                    } else {
                                        onContinue(name, age.toIntOrNull() ?: 18, email, phone, country, state, gender, password, referralCodeUsed)
                                    }
                                }
                            } else {
                                // LOGIN LOGIC
                                scope.launch {
                                    onContinue("", 0, email, "", "", "", "", password, "")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        enabled = !isAuthLoading && (if(isSignUpMode) 
                            (agreedToTerms && name.isNotBlank() && age.isNotBlank() && gender != "Select Gender" && 
                             country != "Select Country" && state != "Select State" && email.isNotBlank() && 
                             phone.isNotBlank() && password.length >= 6)
                        else 
                            (email.isNotBlank() && password.isNotBlank())),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        if (isAuthLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.Black)
                            Spacer(Modifier.width(12.dp))
                            Text("Processing...", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        } else {
                            val buttonText = if (isSignUpMode) "Create Account" else "Log In Now"
                            Text(buttonText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    TextButton(onClick = { onToggleMode(!isSignUpMode) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(
                            if (isSignUpMode) "Already have an account? Sign In" else "New to Tinklet? Create Account", 
                            color = Color.White, 
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    Spacer(Modifier.height(60.dp))
                }
            }

            // UNWANTED LOADING REMOVED
        }
    }

    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = { Text("Terms & Conditions", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("1. Eligibility: You must be at least 18 years old to use Tinklet.")
                    Spacer(Modifier.height(8.dp))
                    Text("2. Safety: Any form of harassment, hate speech, or explicit content is strictly prohibited.")
                    Spacer(Modifier.height(8.dp))
                    Text("3. Verification: You agree to provide an authentic selfie for account verification.")
                    Spacer(Modifier.height(8.dp))
                    Text("4. Deletion: You can request account deletion anytime; data will be purged after 30 days.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showTermsDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
fun AuthInputField(
    label: String, 
    value: String, 
    onValueChange: (String) -> Unit, 
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isNumber: Boolean = false,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, color = Color.White.copy(0.7f)) },
            leadingIcon = { Icon(icon, null, tint = Color.White) },
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else if (value.isNotBlank()) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(20.dp))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = if(isNumber) androidx.compose.ui.text.input.KeyboardType.Number else androidx.compose.ui.text.input.KeyboardType.Text
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.White,
                unfocusedBorderColor = Color.White.copy(0.5f),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = Color.White,
                disabledBorderColor = Color.White.copy(0.5f),
                disabledTextColor = Color.White,
                disabledLabelColor = Color.White.copy(0.7f)
            )
        )
        
        if (onClick != null) {
            Box(modifier = Modifier.matchParentSize().clickable { onClick() })
        }
    }
}

