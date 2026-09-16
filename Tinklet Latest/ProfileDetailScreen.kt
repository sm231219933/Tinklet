package com.tinklet.bharatdatingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tinklet.bharatdatingapp.data.local.UserProfile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileDetailScreen(
    profile: UserProfile,
    discoveryViewModel: com.tinklet.bharatdatingapp.ui.viewmodel.DiscoveryViewModel? = null,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(profile.name, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // HIGH QUALITY SINGLE PROFILE IMAGE AT TOP
            Box(modifier = Modifier.fillMaxWidth().height(450.dp)) {
                AsyncImage(
                    model = profile.photoUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { selectedImageUrl = profile.photoUri },
                    contentScale = ContentScale.Crop
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                                startY = 700f
                            )
                        )
                )

                if (profile.profession == "Testing User") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 16.dp, end = 16.dp)
                            .background(Color.Red.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "TESTING PROFILE",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(24.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${profile.name}, ${profile.age}",
                            color = Color.White,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black
                        )
                        if (profile.badgeType != "NONE" && profile.badgeType != "-") {
                            Spacer(Modifier.width(12.dp))
                            val badgeColor = when(profile.badgeType) {
                                "GOLDEN" -> Color(0xFFFFD700)
                                "SILVER" -> Color(0xFFC0C0C0)
                                "BRONZE" -> Color(0xFFCD7F32)
                                else -> Color.Transparent
                            }
                            Surface(
                                color = badgeColor,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Rounded.WorkspacePremium, null, tint = Color.White, modifier = Modifier.padding(4.dp))
                            }
                        } else if (profile.connectionStatus == "ACCEPTED") {
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.Rounded.Verified, null, tint = Color(0xFF00BFFF), modifier = Modifier.size(24.dp))
                        }
                    }
                    Text(
                        "📍 ${profile.state}, ${profile.country}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 16.sp
                    )
                }
            }

            // Information Grid
            Column(modifier = Modifier.padding(24.dp)) {
                SectionHeader("About")
                Text(profile.bio.ifBlank { "No bio added yet." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                
                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("Personal Details")
                InfoChipRow(listOf(
                    if (profile.height.isNotBlank()) "📏 ${profile.height}" else "",
                    if (profile.religion.isNotBlank()) "☸️ ${profile.religion}" else "",
                    if (profile.education.isNotBlank()) "🎓 ${profile.education}" else "",
                    if (profile.profession.isNotBlank()) "💼 ${profile.profession}" else "",
                    if (profile.language.isNotBlank()) "🗣️ ${profile.language}" else "",
                    if (profile.dob.isNotBlank()) "🎂 ${profile.dob}" else "",
                    if (profile.gender.isNotBlank()) "👤 ${profile.gender}" else ""
                ).filter { it.isNotBlank() })

                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("Lifestyle & Goals")
                InfoChipRow(listOf(
                    if (profile.diet.isNotBlank()) "🥗 ${profile.diet}" else "",
                    if (profile.habits.isNotBlank()) "🚬 ${profile.habits}" else "",
                    if (profile.intentions.isNotBlank()) "🎯 ${profile.intentions}" else ""
                ).filter { it.isNotBlank() })

                Spacer(modifier = Modifier.height(24.dp))
                
                SectionHeader("Interests")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profile.interests.split(",").forEach { hobby ->
                        if (hobby.isNotBlank()) {
                            AssistChip(onClick = {}, label = { Text(hobby.trim()) })
                        }
                    }
                }

                if (profile.secondaryPhotos.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    SectionHeader("Photos")
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        profile.secondaryPhotos.forEach { photoUrl ->
                            AsyncImage(
                                model = photoUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(110.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.LightGray)
                                    .clickable { selectedImageUrl = photoUrl },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
                
                // SAFETY SECTION
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Safety Tools", fontWeight = FontWeight.Bold, color = Color.Red)
                        Text("If you find this profile inappropriate or suspicious, please use the tools below.", fontSize = 12.sp)
                        Spacer(Modifier.height(12.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { discoveryViewModel?.blockUser(profile.email) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
                            ) {
                                Icon(Icons.Rounded.Block, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Block")
                            }
                            Button(
                                onClick = { discoveryViewModel?.reportUser(profile.email) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) {
                                Icon(Icons.Rounded.Report, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Report")
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(60.dp))
            }
        }

        selectedImageUrl?.let { url ->
            Dialog(
                onDismissRequest = { selectedImageUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )
                    IconButton(
                        onClick = { selectedImageUrl = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).background(Color.Black.copy(0.5f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InfoChipRow(items: List<String>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = item,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
