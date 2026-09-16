package com.tinklet.bharatdatingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tinklet.bharatdatingapp.data.local.UserProfile
import com.tinklet.bharatdatingapp.utils.Translator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPhotosScreen(
    user: UserProfile?,
    lang: String,
    onBack: () -> Unit,
    onUploadClick: () -> Unit,
    onDeleteClick: (String) -> Unit
) {
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(Translator.translate("My Photos", lang), fontWeight = FontWeight.ExtraBold, color = Color(0xFFFE3C72)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color(0xFFFE3C72))
                    }
                }
            )
        },
        floatingActionButton = {
            val photos = user?.secondaryPhotos ?: emptyList()
            val limitReached = photos.size >= 10
            
            ExtendedFloatingActionButton(
                onClick = { if (!limitReached) onUploadClick() },
                containerColor = if (limitReached) Color.Gray else Color(0xFFFE3C72),
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(if (limitReached) Icons.Rounded.Block else Icons.Rounded.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(if (limitReached) "Limit 10 Reached" else Translator.translate("Upload Photos", lang))
            }
        }
    ) { padding ->
        val photos = user?.secondaryPhotos ?: emptyList()

        if (photos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(Modifier.height(16.dp))
                    Text(Translator.translate("No photos uploaded yet.", lang), color = Color.Gray)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding).padding(8.dp),
                contentPadding = PaddingValues(bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(photos) { url ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { selectedImageUrl = url }
                    ) {
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        IconButton(
                            onClick = { onDeleteClick(url) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(28.dp)
                                .padding(4.dp)
                                .background(Color.Black.copy(0.5f), CircleShape)
                        ) {
                            Icon(Icons.Rounded.Delete, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        selectedImageUrl?.let { url ->
            FullscreenImageDialog(url = url, onDismiss = { selectedImageUrl = null })
        }
    }
}

@Composable
fun FullscreenImageDialog(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AsyncImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }
        }
    }
}
