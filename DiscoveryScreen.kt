package com.tinklet.bharatdatingapp.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.SoundEffectConstants
import com.tinklet.bharatdatingapp.data.local.UserProfile
import com.tinklet.bharatdatingapp.ui.components.SwipeableCard
import com.tinklet.bharatdatingapp.ui.viewmodel.DiscoveryViewModel
import com.tinklet.bharatdatingapp.utils.GeographyUtils
import com.tinklet.bharatdatingapp.utils.SoundManager
import com.tinklet.bharatdatingapp.utils.Translator
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(
    discoveryViewModel: DiscoveryViewModel, // Added
    profiles: List<UserProfile>,
    onCoinCenterClick: () -> Unit, // NEW
    selectedCountry: String,
    selectedState: String,
    ageRange: IntRange,
    genderFilter: String,
    religionFilter: String,
    habitFilter: String,
    languageFilter: String,
    intentionFilter: String,
    onFilterChange: (String, String, IntRange, String, String, String, String, String) -> Unit,
    onLike: (UserProfile) -> Unit,
    onSuperLike: (UserProfile) -> Unit,
    onDislike: (UserProfile) -> Unit,
    onGift: (UserProfile, String) -> Unit,
    onProfileClick: (UserProfile) -> Unit,
    onResetViewed: () -> Unit,
    onMarkAsViewed: (UserProfile) -> Unit,
    onCancelRequest: (UserProfile) -> Unit, // Added
    rejectedProfiles: List<UserProfile> = emptyList(),
    superLikedProfiles: List<UserProfile> = emptyList(),
    sentRequests: List<UserProfile> = emptyList()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        SoundManager.init(context)
    }

    var currentIndex by remember { mutableIntStateOf(0) }
    // Reset index if profiles list changes significantly (e.g. from 0 to many)
    LaunchedEffect(profiles.isEmpty()) {
        if (profiles.isNotEmpty()) currentIndex = 0
    }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showListType by remember { mutableStateOf<String?>(null) }
    val lang by discoveryViewModel.appLanguage.collectAsStateWithLifecycle()
    
    // Animation States
    var showSuperLikeAnim by remember { mutableStateOf(false) }
    var showLikeAnim by remember { mutableStateOf(false) }

    val showWelcome by discoveryViewModel.showWelcomeMessage.collectAsStateWithLifecycle()

    val infiniteTransition = rememberInfiniteTransition(label = "shining_heart")
    val heartAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("❤️", modifier = Modifier.graphicsLayer { this.alpha = heartAlpha })
                        Spacer(Modifier.width(8.dp))
                        Text(Translator.translate("Tinklet", lang), color = Color(0xFFFE3C72), fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        Spacer(Modifier.width(8.dp))
                        Text("❤️", modifier = Modifier.graphicsLayer { this.alpha = heartAlpha })
                    }
                },
                actions = {
                    val me by discoveryViewModel.currentUser.collectAsStateWithLifecycle()
                    TextButton(
                        onClick = onCoinCenterClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFFD700))
                    ) {
                        Icon(Icons.Rounded.MonetizationOn, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${me?.coins ?: 0}", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { showFilterDialog = true }) { Icon(Icons.Rounded.FilterList, "Filter", tint = Color(0xFFFE3C72)) }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (currentIndex < profiles.size) {
                for (i in (currentIndex + 1 downTo currentIndex)) {
                    if (i < profiles.size) {
                        val profile = profiles[i]
                        val isTopCard = i == currentIndex
                        
                        key(profile.email) {
                            SwipeableCard(
                                onSwipeLeft = { 
                                    if(isTopCard) { 
                                        SoundManager.playReject()
                                        onDislike(profile)
                                    } 
                                },
                                onSwipeRight = { 
                                    if(isTopCard) { 
                                        SoundManager.playLike()
                                        showLikeAnim = true
                                        onLike(profile)
                                    } 
                                },
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        if (!isTopCard) {
                                            scaleX = 0.92f
                                            scaleY = 0.92f
                                            alpha = 0.5f
                                        }
                                    }
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxSize().clickable(enabled = isTopCard) { onProfileClick(profile) },
                                    shape = RoundedCornerShape(28.dp),
                                    elevation = CardDefaults.cardElevation(8.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        SubcomposeAsyncImage(
                                            model = profile.photoUri,
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop,
                                            loading = { CircularProgressIndicator(modifier = Modifier.align(Alignment.Center)) }
                                        )

                                        // TOP GRADIENT (For Name/Age legibility)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .background(Brush.verticalGradient(listOf(Color.Black.copy(0.6f), Color.Transparent)))
                                        )

                                        // BOTTOM GRADIENT (For Buttons legibility)
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .height(200.dp)
                                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.7f))))
                                        )

                                        // TOP OVERLAY: Name, Age, Location
                                        Column(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(20.dp)
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    "${profile.name}, ${profile.age}",
                                                    color = Color.White,
                                                    fontSize = 30.sp,
                                                    fontWeight = FontWeight.Black
                                                )
                                                if (profile.badgeType != "NONE" && profile.badgeType != "-") {
                                                    Spacer(Modifier.width(8.dp))
                                                    val badgeColor = when(profile.badgeType) {
                                                        "GOLDEN" -> Color(0xFFFFD700)
                                                        "SILVER" -> Color(0xFFC0C0C0)
                                                        "BRONZE" -> Color(0xFFCD7F32)
                                                        else -> Color.Transparent
                                                    }
                                                    Surface(
                                                        color = badgeColor,
                                                        shape = RoundedCornerShape(8.dp),
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.padding(2.dp))
                                                    }
                                                } else if (profile.isPremium) {
                                                    Spacer(Modifier.width(8.dp))
                                                    Icon(Icons.Rounded.Verified, null, tint = Color(0xFF00BFFF), modifier = Modifier.size(24.dp))
                                                }
                                            }
                                            Text(
                                                "📍 ${profile.state}, ${profile.country}",
                                                color = Color.White.copy(0.9f),
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        // NAVIGATION ARROWS
                                        if (isTopCard) {
                                            Row(
                                                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = { 
                                                        onMarkAsViewed(profile)
                                                        if (currentIndex < profiles.size) currentIndex++
                                                    },
                                                    modifier = Modifier.background(Color.Black.copy(0.2f), CircleShape)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(36.dp))
                                                }
                                                IconButton(
                                                    onClick = { 
                                                        onMarkAsViewed(profile)
                                                        if (currentIndex < profiles.size) currentIndex++
                                                    },
                                                    modifier = Modifier.background(Color.Black.copy(0.2f), CircleShape)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(36.dp))
                                                }
                                            }
                                        }

                                        // BOTTOM OVERLAY: Action Buttons (X, Star, Heart) - LABELS REMOVED
                                        Row(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .padding(bottom = 30.dp)
                                                .fillMaxWidth(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AnimatedActionButton(
                                                onClick = { 
                                                    SoundManager.playReject()
                                                    onDislike(profile)
                                                },
                                                color = Color.White.copy(0.15f),
                                                icon = Icons.Rounded.Close,
                                                iconColor = Color.Red,
                                                size = 60.dp
                                            )
                                            
                                            Spacer(Modifier.width(32.dp))

                                            AnimatedActionButton(
                                                onClick = { 
                                                    SoundManager.playSuperLike()
                                                    showSuperLikeAnim = true
                                                    onSuperLike(profile)
                                                },
                                                color = Color(0xFF2196F3).copy(0.8f),
                                                icon = Icons.Rounded.Star,
                                                iconColor = Color.White,
                                                size = 72.dp
                                            )

                                            Spacer(Modifier.width(32.dp))

                                            AnimatedActionButton(
                                                onClick = { 
                                                    SoundManager.playLike()
                                                    showLikeAnim = true
                                                    onLike(profile)
                                                },
                                                color = Color(0xFFFE3C72).copy(0.8f),
                                                icon = Icons.Rounded.Favorite,
                                                iconColor = Color.White,
                                                size = 60.dp,
                                                hasPlus = true
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Rounded.Search, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text(Translator.translate("No more profiles!", lang), color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Button(
                        onClick = onResetViewed, 
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE3C72))
                    ) { 
                        Text(Translator.translate("Viewed Profile", lang)) 
                    }
                }
            }
        }

        // Action Animations
        if (showLikeAnim) {
            HeartPopAnimation(onEnd = { showLikeAnim = false })
        }
        if (showSuperLikeAnim) {
            StarBurstAnimation(onEnd = { showSuperLikeAnim = false })
        }

        if (showWelcome) {
            WelcomeGiftDialog(onDismiss = { discoveryViewModel.dismissWelcomeMessage() })
        }
    }
    
    if (showFilterDialog) {
        FilterDialog(
            discoveryViewModel = discoveryViewModel,
            selectedCountry, selectedState, ageRange, genderFilter, religionFilter, habitFilter, languageFilter, intentionFilter,
            onDismiss = { showFilterDialog = false },
            onApply = { c, s, a, g, r, h, l, i -> 
                onFilterChange(c, s, a, g, r, h, l, i)
                showFilterDialog = false
            }
        )
    }

    if (showListType != null) {
        val list = when(showListType) {
            "REJECTED" -> rejectedProfiles
            "SUPERLIKE" -> superLikedProfiles
            "REQUEST" -> sentRequests
            else -> emptyList()
        }
        AlertDialog(
            onDismissRequest = { showListType = null },
            title = { Text(when(showListType) { "REJECTED" -> "Rejected"; "SUPERLIKE" -> "Super Liked"; else -> "Sent Requests" }, color = Color(0xFFFE3C72), fontWeight = FontWeight.Bold) },
            text = {
                if (list.isEmpty()) {
                    Text("List is empty.")
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                        items(list) { p ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable { 
                                        showListType = null
                                        onProfileClick(p) 
                                    }, 
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    SubcomposeAsyncImage(model = p.photoUri, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                                    Spacer(Modifier.width(12.dp))
                                    Text("${p.name}, ${p.age}", fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { onCancelRequest(p) }) {
                                    Text("Cancel", color = Color.Red, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showListType = null }) { Text("Close", color = Color(0xFFFE3C72)) } }
        )
    }
}

@Composable
fun AnimatedActionButton(
    onClick: () -> Unit, 
    color: Color, 
    icon: androidx.compose.ui.graphics.vector.ImageVector, 
    iconColor: Color, 
    size: androidx.compose.ui.unit.Dp,
    hasPlus: Boolean = false
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "button_scale"
    )
    
    IconButton(
        onClick = { 
            isPressed = true
            onClick()
        },
        modifier = Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .background(color, CircleShape)
            .padding(8.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = iconColor, modifier = Modifier.size(size * 0.6f))
            if (hasPlus) {
                Icon(Icons.Rounded.Add, null, tint = color, modifier = Modifier.size(size * 0.25f))
            }
        }
        if (isPressed) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(100)
                isPressed = false
            }
        }
    }
}

@Composable
fun HeartPopAnimation(onEnd: () -> Unit) {
    var start by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (start) 0f else 2.5f,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "heart_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (start) 0f else 0.8f,
        animationSpec = tween(600),
        label = "heart_alpha"
    )

    LaunchedEffect(Unit) {
        start = true
        delay(600)
        onEnd()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Rounded.Favorite,
            null,
            tint = Color(0xFFFE3C72).copy(alpha = alpha),
            modifier = Modifier.size(100.dp).graphicsLayer(scaleX = scale, scaleY = scale)
        )
    }
}

@Composable
fun StarBurstAnimation(onEnd: () -> Unit) {
    var start by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        start = true
        delay(1000)
        onEnd()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        repeat(12) { i ->
            val angle = i * 30f
            val distance by animateFloatAsState(
                targetValue = if (start) 300f else 0f,
                animationSpec = spring(stiffness = Spring.StiffnessLow),
                label = "star_dist"
            )
            val alpha by animateFloatAsState(
                targetValue = if (start) 0f else 1f,
                animationSpec = tween(800),
                label = "star_alpha"
            )
            
            Icon(
                Icons.Rounded.Star,
                null,
                tint = Color(0xFF2196F3).copy(alpha = alpha),
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer {
                        translationX = distance * kotlin.math.cos(Math.toRadians(angle.toDouble())).toFloat()
                        translationY = distance * kotlin.math.sin(Math.toRadians(angle.toDouble())).toFloat()
                        rotationZ = angle + 90
                    }
            )
        }
    }
}

@Composable
fun WelcomeGiftDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("🎉 Welcome! 🎉", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Color(0xFFFE3C72))
            }
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Congrats! You've become a member of Tinklet.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "As a welcome gift, you got 25 free coins (worth ₹25)!",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    color = Color(0xFFE65100),
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "Use them for Superlikes or to go Premium.",
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 14.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE3C72))
            ) {
                Text("Let's Start!", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(24.dp)
    )
}

@Composable
fun FilterTag(text: String) {
    Surface(color = Color.White.copy(0.2f), shape = RoundedCornerShape(8.dp)) {
        Text(text, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDialog(
    discoveryViewModel: DiscoveryViewModel,
    initialCountry: String, initialState: String, initialAge: IntRange, initialGender: String, initialReligion: String, initialHabits: String, initialLanguage: String, initialIntentions: String,
    onDismiss: () -> Unit,
    onApply: (String, String, IntRange, String, String, String, String, String) -> Unit
) {
    val lang by discoveryViewModel.appLanguage.collectAsStateWithLifecycle()
    var country by remember { mutableStateOf(initialCountry) }
    var state by remember { mutableStateOf(initialState) }
    var ageStart by remember { mutableStateOf(initialAge.first.toFloat()) }
    var ageEnd by remember { mutableStateOf(initialAge.last.toFloat()) }
    var gender by remember { mutableStateOf(initialGender) }
    var religion by remember { mutableStateOf(initialReligion) }
    var habit by remember { mutableStateOf(initialHabits) }
    var language by remember { mutableStateOf(initialLanguage) }
    var intention by remember { mutableStateOf(initialIntentions) }

    val allLanguages = listOf("All", "English", "Hindi", "Spanish", "French", "German", "Chinese", "Japanese", "Arabic", "Russian", "Portuguese", "Bengali", "Punjabi", "Tamil", "Telugu")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Translator.translate("Search Filters", lang), color = Color(0xFFFE3C72), fontWeight = FontWeight.ExtraBold, fontSize = 26.sp) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "${Translator.translate("Age", lang)}: ${ageStart.toInt()} - ${ageEnd.toInt()}",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color(0xFFFE3C72)
                )
                RangeSlider(
                    value = ageStart..ageEnd, 
                    onValueChange = { ageStart = it.start; ageEnd = it.endInclusive }, 
                    valueRange = 18f..80f, 
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(thumbColor = Color(0xFFFE3C72), activeTrackColor = Color(0xFFFE3C72))
                )
                
                Spacer(Modifier.height(16.dp))
                
                Text(
                    text = Translator.translate("Location Settings", lang),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color(0xFFFE3C72)
                )
                SearchableDropdown(Translator.translate("Country", lang), country, listOf("All") + GeographyUtils.countries) { country = it; state = "All" }
                Spacer(Modifier.height(8.dp))
                SearchableDropdown(Translator.translate("Search State", lang), state, listOf("All") + GeographyUtils.getStatesForCountry(country)) { state = it }
                
                Spacer(Modifier.height(20.dp))
                Text(
                    text = Translator.translate("Categorical Preferences", lang),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = Color(0xFFFE3C72)
                )
                FilterDropdown(Translator.translate("Gender", lang), gender, listOf("All", "Male", "Female", "Other")) { gender = it }
                FilterDropdown(Translator.translate("Religion", lang), religion, listOf("All", "Hindu", "Muslim", "Sikh", "Christian", "Jain", "Other")) { religion = it }
                FilterDropdown(Translator.translate("Habits", lang), habit, listOf("All", "Non-smoker", "Smoker", "Drinking", "None")) { habit = it }
                SearchableDropdown(Translator.translate("Language", lang), language, allLanguages) { language = it }
                FilterDropdown(Translator.translate("Intentions", lang), intention, listOf("All", "Long-term", "Casual", "Friendship only")) { intention = it }
            }
        },
        confirmButton = { 
            Button(
                onClick = { onApply(country, state, ageStart.toInt()..ageEnd.toInt(), gender, religion, habit, language, intention) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE3C72))
            ) { Text(Translator.translate("Apply", lang)) } 
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Translator.translate("Cancel", lang), color = Color.Gray) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchableDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val filteredOptions = options.filter { it.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFFE3C72),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        // FIXED KEYBOARD ISSUE: Use a full-screen or focused dialog for search
        OutlinedTextField(
            value = selected, onValueChange = {}, readOnly = true, 
            trailingIcon = { Icon(Icons.Rounded.ArrowDropDown, null, tint = Color(0xFFFE3C72)) }, 
            modifier = Modifier.fillMaxWidth().clickable { expanded = true }, 
            enabled = false, // Handle click via modifier
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                disabledBorderColor = Color(0xFFFE3C72).copy(0.5f),
                disabledTextColor = MaterialTheme.colorScheme.onSurface
            )
        )

        if (expanded) {
            Dialog(onDismissRequest = { expanded = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Select $label", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFE3C72), modifier = Modifier.padding(bottom = 12.dp))
                        
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Type to search...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Rounded.Search, null) }
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        
                        LazyColumn {
                            items(filteredOptions) { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = { 
                                        onSelect(opt)
                                        expanded = false
                                        searchQuery = ""
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterDropdown(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFFFE3C72),
            modifier = Modifier.padding(bottom = 4.dp)
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = selected, onValueChange = {}, readOnly = true, 
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, 
                modifier = Modifier.menuAnchor().fillMaxWidth(), 
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFFFE3C72),
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { opt -> DropdownMenuItem(text = {Text(opt)}, onClick = { onSelect(opt); expanded = false }) }
            }
        }
    }
}
