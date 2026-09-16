package com.tinklet.bharatdatingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinklet.bharatdatingapp.data.local.UserProfile
import com.tinklet.bharatdatingapp.ui.viewmodel.DiscoveryViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import android.Manifest
import android.app.Activity
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    user: UserProfile?, 
    onSave: (UserProfile) -> Unit, 
    onBack: () -> Unit,
    discoveryViewModel: DiscoveryViewModel? = null // Optional for backward compat
) {
    if (user == null) return
    val context = LocalContext.current

    var name by remember { mutableStateOf(user.name) }
    var dob by remember { mutableStateOf(user.dob) }
    var gender by remember { mutableStateOf(user.gender) }
    var height by remember { mutableStateOf(user.height) }
    var education by remember { mutableStateOf(user.education) }
    var profession by remember { mutableStateOf(user.profession) }
    var religion by remember { mutableStateOf(user.religion) }
    var country by remember { mutableStateOf(user.country) }
    var state by remember { mutableStateOf(user.state) }
    var diet by remember { mutableStateOf(user.diet) }
    var habits by remember { mutableStateOf(user.habits) }
    var language by remember { mutableStateOf(user.language) }
    var intentions by remember { mutableStateOf(user.intentions) } 
    var bio by remember { mutableStateOf(user.bio) }
    var interests by remember { mutableStateOf(user.interests) } 

    val isDetectingLocation by (discoveryViewModel?.isDetectingLocation?.collectAsStateWithLifecycle() ?: remember { mutableStateOf(false) })
    val fetchedCountry by (discoveryViewModel?.fetchedCountry?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("") })
    val fetchedState by (discoveryViewModel?.fetchedState?.collectAsStateWithLifecycle() ?: remember { mutableStateOf("") })

    LaunchedEffect(fetchedCountry, fetchedState) {
        if (fetchedCountry.isNotBlank()) country = fetchedCountry
        if (fetchedState.isNotBlank()) state = fetchedState
    }

    val gpsSettingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            discoveryViewModel?.detectUserLocation(
                onResolutionRequired = { },
                onFail = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            discoveryViewModel?.detectUserLocation(
                onResolutionRequired = { res ->
                    try {
                        gpsSettingsLauncher.launch(IntentSenderRequest.Builder(res.resolution.intentSender).build())
                    } catch (e: Exception) { }
                },
                onFail = { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    val datePickerState = rememberDatePickerState()
    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Profile", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFE3C72)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color(0xFFFE3C72)) }
                },
                actions = {
                    Button(
                        onClick = { 
                            onSave(user.copy(
                                name = name, dob = dob, gender = gender, height = height,
                                education = education, profession = profession, religion = religion,
                                country = country, state = state,
                                diet = diet, habits = habits, language = language, intentions = intentions, 
                                bio = bio, interests = interests
                            )) 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE3C72)),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text("Save", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(horizontal = 20.dp)) {
            item {
                Spacer(Modifier.height(16.dp))
                
                // CATEGORY: BASIC INFO
                SectionHeading("Basic Information", Color(0xFFFE3C72))
                CustomEditField("Name", name, { name = it })
                
                Spacer(Modifier.height(12.dp))

                CustomDateField("Date of Birth", dob, { showDatePicker = true })

                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let {
                                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                    dob = sdf.format(Date(it))
                                }
                                showDatePicker = false
                            }) { Text("Confirm", color = Color(0xFFFE3C72)) }
                        }
                    ) { DatePicker(state = datePickerState) }
                }

                Spacer(Modifier.height(12.dp))
                CustomDropdownField("Gender", gender, listOf("Male", "Female", "Other"), Color(0xFFFE3C72)) { gender = it }

                Spacer(Modifier.height(24.dp))

                // CATEGORY: LOCATION
                SectionHeading("Location Details", Color(0xFF388E3C))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Country: $country", fontWeight = FontWeight.Bold)
                        Text("State: $state", color = Color.Gray)
                    }
                    Button(
                        onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF388E3C)),
                        enabled = !isDetectingLocation
                    ) {
                        if (isDetectingLocation) CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                        else Text("Update Location", fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(24.dp))

                // CATEGORY: PHYSICAL & EDUCATION
                SectionHeading("Physical & Education", Color(0xFFE65100))
                val feetInches = mutableListOf<String>()
                for (f in 4..7) { for (i in 0..11) { feetInches.add("$f'$i\"") } }
                CustomDropdownField("Height", height, feetInches, Color(0xFFE65100)) { height = it }
                Spacer(Modifier.height(12.dp))
                CustomDropdownField("Education", education, listOf("High School", "Bachelors", "Masters", "PhD", "Other"), Color(0xFFE65100)) { education = it }

                Spacer(Modifier.height(24.dp))

                // CATEGORY: WORK & FAITH
                SectionHeading("Work & Faith", Color(0xFF00796B))
                CustomDropdownField("Profession", profession, listOf("Private Job", "Government Job", "Business", "Self Employed", "Student", "Not Working"), Color(0xFF00796B)) { profession = it }
                Spacer(Modifier.height(12.dp))
                CustomDropdownField("Religion", religion, listOf("Hindu", "Muslim", "Sikh", "Christian", "Jain", "Buddhist", "Other", "Prefer not to say"), Color(0xFF00796B)) { religion = it }

                Spacer(Modifier.height(24.dp))

                // CATEGORY: LIFESTYLE
                SectionHeading("Lifestyle & Goals", Color(0xFF512DA8))
                CustomDropdownField("Diet", diet, listOf("Vegetarian", "Non Vegetarian", "Eggetarian", "Vegan"), Color(0xFF512DA8)) { diet = it }
                Spacer(Modifier.height(12.dp))
                CustomDropdownField("Habits", habits, listOf("Non-smoker", "Smoker", "Social Drinker", "Regular Drinker", "Tea/Coffee lover"), Color(0xFF512DA8)) { habits = it }
                Spacer(Modifier.height(12.dp))
                
                val languages = listOf("English", "Hindi", "Bengali", "Telugu", "Marathi", "Tamil", "Urdu", "Gujarati", "Kannada", "Odia", "Malayalam", "Punjabi", "Sanskrit", "Spanish", "French", "German", "Other")
                CustomDropdownField("Language", language, languages, Color(0xFF512DA8)) { language = it }
                
                Spacer(Modifier.height(12.dp))
                CustomDropdownField("Goals", intentions, listOf("Marriage", "Long term", "Casual", "Friends"), Color(0xFF512DA8)) { intentions = it }


                Spacer(Modifier.height(24.dp))

                // CATEGORY: ABOUT ME
                SectionHeading("About Me", Color.DarkGray)
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 500) bio = it },
                    placeholder = { Text("Tell us about yourself...") },
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color.DarkGray)
                )

                Spacer(Modifier.height(24.dp))

                // CATEGORY: HOBBIES
                SectionHeading("Hobbies & Interests", Color(0xFFC2185B))
                val availableHobbies = listOf("Travel", "Music", "Movies", "Reading", "Sports", "Cooking", "Gaming", "Photography", "Art", "Gym")
                val selectedHobbies = interests.split(",").filter { it.isNotBlank() }.map { it.trim() }.toMutableStateList()
                
                FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableHobbies.forEach { hobby ->
                        FilterChip(
                            selected = selectedHobbies.contains(hobby),
                            onClick = {
                                if (selectedHobbies.contains(hobby)) selectedHobbies.remove(hobby)
                                else selectedHobbies.add(hobby)
                                interests = selectedHobbies.joinToString(", ")
                            },
                            label = { Text(hobby) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFC2185B),
                                selectedLabelColor = Color.White
                            ),
                            shape = RoundedCornerShape(20.dp)
                        )
                    }
                }
                
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
fun SectionHeading(text: String, color: Color) {
    Text(
        text = text.uppercase(),
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = color,
        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomEditField(label: String, value: String, onValueChange: (String) -> Unit) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, color = Color(0xFFFE3C72), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFE3C72),
                unfocusedBorderColor = Color.LightGray
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDateField(label: String, value: String, onClick: () -> Unit) {
    Column {
        Text(label, fontWeight = FontWeight.Bold, color = Color(0xFFFE3C72), modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        OutlinedTextField(
            value = value.ifEmpty { "Select Date" },
            onValueChange = {},
            readOnly = true,
            modifier = Modifier.fillMaxWidth().clickable { onClick() },
            trailingIcon = { Icon(Icons.Rounded.CalendarToday, null, tint = Color(0xFFFE3C72)) },
            enabled = false,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                disabledBorderColor = Color.LightGray,
                disabledTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomDropdownField(label: String, selected: String, options: List<String>, themeColor: Color, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, fontWeight = FontWeight.Bold, color = themeColor, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = selected.ifEmpty { "Select $label" },
                onValueChange = {},
                readOnly = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = themeColor,
                    unfocusedBorderColor = Color.LightGray
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt) },
                        onClick = { onSelect(opt); expanded = false }
                    )
                }
            }
        }
    }
}
