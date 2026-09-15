package com.tinklet.bharatdatingapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tinklet.bharatdatingapp.data.local.UserProfile
import com.tinklet.bharatdatingapp.utils.Translator

@Composable
fun ProfileScreen(
    user: UserProfile?,
    onUpdatePhoto: () -> Unit,
    onUpdatePhotos: () -> Unit, // New callback for multiple photos
    onLogout: () -> Unit,
    onDeactivate: () -> Unit = {},
    onDelete: () -> Unit = {},
    onEditProfile: () -> Unit = {},
    onSubscription: () -> Unit = {},
    currentTheme: String = "System",
    onThemeChange: (String) -> Unit = {},
    currentLanguage: String = "en",
    soundEnabled: Boolean = true,
    onSoundToggle: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var showPrivacyPolicy by remember { mutableStateOf(false) }
    var notificationsEnabled by remember { mutableStateOf(true) }
    var showFullscreenProfile by remember { mutableStateOf(false) }

    if (user == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color(0xFFFE3C72))
                Spacer(Modifier.height(24.dp))
                Text("Loading Profile...", color = Color.Gray, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFE3C72), MaterialTheme.colorScheme.surface)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(contentAlignment = Alignment.Center) {
                    // Instagram-style gradient ring
                    Box(
                        modifier = Modifier
                            .size(174.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))
                                )
                            )
                    )
                    
                    AsyncImage(
                        model = user.photoUri,
                        contentDescription = null,
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .border(4.dp, Color.White, CircleShape)
                            .background(Color.White)
                            .clickable { showFullscreenProfile = true },
                        contentScale = ContentScale.Crop
                    )
                    
                    IconButton(
                        onClick = onUpdatePhoto,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = (-8).dp, y = (-8).dp)
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .border(2.dp, Color.White, CircleShape)
                    ) {
                        Icon(Icons.Rounded.CameraAlt, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                Surface(
                    color = Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(horizontal = 24.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${user.name}, ${user.age}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Text(
                            text = "📍 ${user.state}, ${user.country}",
                            color = Color.White.copy(alpha = 0.9f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Stats Section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProfileStatItem(Translator.translate("Coins", currentLanguage), user.coins.toString(), Icons.Rounded.Star)
            ProfileStatItem(Translator.translate("Status", currentLanguage), if(user.isPremium) Translator.translate("Premium", currentLanguage) else Translator.translate("Free", currentLanguage), Icons.Rounded.Verified)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Professional Menu List
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column {
                ProfileMenuItem(Icons.Rounded.Edit, Translator.translate("Edit Profile Details", currentLanguage)) { onEditProfile() }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                ProfileMenuItem(Icons.Rounded.PhotoLibrary, Translator.translate("My Photos", currentLanguage)) { onUpdatePhotos() }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                
                ProfileMenuItem(
                    icon = if(notificationsEnabled) Icons.Rounded.Notifications else Icons.Rounded.NotificationsOff, 
                    title = "${Translator.translate("Notifications", currentLanguage)}: ${if(notificationsEnabled) "ON" else "OFF"}"
                ) { 
                    notificationsEnabled = !notificationsEnabled
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ProfileMenuItem(
                    icon = if(soundEnabled) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeOff, 
                    title = "${Translator.translate("Interaction Sounds", currentLanguage)}: ${if(soundEnabled) "ON" else "OFF"}"
                ) {
                    onSoundToggle(!soundEnabled)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ProfileMenuItem(Icons.Rounded.BrightnessMedium, "${Translator.translate("Theme", currentLanguage)}: $currentTheme") {
                    val nextTheme = when(currentTheme) {
                        "Light" -> "Dark"
                        "Dark" -> "System"
                        else -> "Light"
                    }
                    onThemeChange(nextTheme)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                ProfileMenuItem(Icons.Rounded.SupportAgent, Translator.translate("Support & Help", currentLanguage)) {
                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                        data = Uri.parse("mailto:support@tinklet.in")
                        putExtra(Intent.EXTRA_SUBJECT, "Query/Issue from ${user.name}")
                    }
                    context.startActivity(intent)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                
                ProfileMenuItem(Icons.Rounded.Shield, Translator.translate("Privacy Policy", currentLanguage)) { showPrivacyPolicy = true }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Danger Zone
        Text(Translator.translate("Danger Zone", currentLanguage), color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start).padding(start = 24.dp))
        
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onDeactivate,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
            ) {
                Text(Translator.translate("Deactivate", currentLanguage))
            }
            Button(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(Translator.translate("Delete Profile", currentLanguage))
            }
        }

        TextButton(onClick = onLogout) {
            Icon(Icons.AutoMirrored.Rounded.Logout, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(Translator.translate("Logout", currentLanguage))
        }
        
        Spacer(modifier = Modifier.height(40.dp))
    }

    if (showPrivacyPolicy) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicy = false },
            title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("1. Data Collection: We collect name, age, and photos for profile creation.")
                    Spacer(Modifier.height(8.dp))
                    Text("2. Usage: Your data is used only to find matches and is never sold to third parties.")
                    Spacer(Modifier.height(8.dp))
                    Text("3. Safety: All photos are face-verified to prevent fake profiles.")
                    Spacer(Modifier.height(8.dp))
                    Text("4. Storage: In-chat photos are hosted on ImgBB and auto-deleted after 1 week.")
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyPolicy = false }) { Text("Close") }
            }
        )
    }

    if (showFullscreenProfile) {
        FullscreenImageDialog(url = user.photoUri, onDismiss = { showFullscreenProfile = false })
    }
}

@Composable
fun ProfileStatItem(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Text(value, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(label, fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
fun ProfileMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(60.dp),
        color = Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.LightGray)
        }
    }
}

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, alpha: Float = 0.5f) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(Color.Gray.copy(alpha = alpha)))
}
