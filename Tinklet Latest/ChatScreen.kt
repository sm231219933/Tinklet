package com.tinklet.bharatdatingapp.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import coil.compose.SubcomposeAsyncImage
import com.tinklet.bharatdatingapp.data.local.ChatMessage
import com.tinklet.bharatdatingapp.data.local.UserProfile
import com.tinklet.bharatdatingapp.utils.VoiceNoteRecorder
import com.tinklet.bharatdatingapp.utils.VideoNoteRecorder
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    match: UserProfile,
    messages: List<ChatMessage>,
    discoveryViewModel: com.tinklet.bharatdatingapp.ui.viewmodel.DiscoveryViewModel? = null,
    onSendMessage: (String) -> Unit,
    onSendImageClick: () -> Unit,
    onSendGift: (String) -> Unit,
    onCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onBack: () -> Unit,
    onNameClick: (UserProfile) -> Unit // CLICKABLE NAME
) {
    var textState by remember { mutableStateOf("") }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showGiftSheet by remember { mutableStateOf(false) }
    
    var messageToManage by remember { mutableStateOf<ChatMessage?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editState by remember { mutableStateOf("") }

    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val voiceRecorder = remember { VoiceNoteRecorder(context) }
    var isRecordingVoice by remember { mutableStateOf(false) }
    
    var isRecordingVideo by remember { mutableStateOf(false) }
    val previewView = remember { androidx.camera.view.PreviewView(context) }
    val videoRecorder = remember { VideoNoteRecorder(context, previewView, lifecycleOwner) }

    LaunchedEffect(match.email) {
        discoveryViewModel?.markMessagesAsRead(match.email)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { onNameClick(match) } // CLICKABLE NAME
                    ) {
                        SubcomposeAsyncImage(
                            model = match.photoUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop,
                            loading = { CircularProgressIndicator() }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(match.name, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    /* Hiding call icons for now
                    IconButton(onClick = onCallClick) {
                        Icon(Icons.Rounded.Call, contentDescription = "Voice Call", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onVideoCallClick) {
                        Icon(Icons.Rounded.Videocam, contentDescription = "Video Call", tint = MaterialTheme.colorScheme.primary)
                    }
                    */
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Rounded.MoreVert, "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Clear Chat") },
                            onClick = { 
                                showMenu = false
                                discoveryViewModel?.deleteChat(match.email)
                            }
                        )
                    }
                }
            )
        }
    ) { padding ->
        var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
        var showMessageOptions by remember { mutableStateOf(false) }

        if (isRecordingVideo) {
            Dialog(onDismissRequest = { isRecordingVideo = false }) {
                Box(modifier = Modifier.size(250.dp).clip(CircleShape).background(Color.Black)) {
                    androidx.compose.ui.viewinterop.AndroidView(
                        factory = { previewView },
                        modifier = Modifier.fillMaxSize()
                    )
                    Text("Recording...", color = Color.Red, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp))
                }
            }
        }

        var sentGiftUrl by remember { mutableStateOf<String?>(null) }

        if (sentGiftUrl != null) {
            Dialog(onDismissRequest = { sentGiftUrl = null }) {
                Box(modifier = Modifier.size(300.dp).background(Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                    AsyncImage(
                        model = sentGiftUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            LaunchedEffect(sentGiftUrl) {
                delay(3000)
                sentGiftUrl = null
            }
        }

        if (showGiftSheet) {
            GiftSheet(
                onGiftSelected = { giftName -> 
                    onSendMessage("Sent you a $giftName 🎁") 
                    sentGiftUrl = "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1JmVwPXYxX2ludGVybmFsX2dpZl9ieV9pZCZjdD1n/3o7TKoWXlo3S1Qfe3C/giphy.gif"
                },
                onDismiss = { showGiftSheet = false }
            )
        }

        if (showMessageOptions && messageToManage != null) {
            ModalBottomSheet(onDismissRequest = { showMessageOptions = false }) {
                Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                    if (messageToManage!!.senderId == "ME") {
                        ListItem(
                            headlineContent = { Text("Edit Message") },
                            leadingContent = { Icon(Icons.Rounded.Edit, null) },
                            modifier = Modifier.clickable { 
                                showMessageOptions = false
                                showEditDialog = true
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Delete for Everyone", color = Color.Red) },
                            leadingContent = { Icon(Icons.Rounded.DeleteSweep, null, tint = Color.Red) },
                            modifier = Modifier.clickable { 
                                showMessageOptions = false
                                showDeleteDialog = true
                            }
                        )
                    }
                    ListItem(
                        headlineContent = { Text("Delete for Me") },
                        leadingContent = { Icon(Icons.Rounded.Delete, null) },
                        modifier = Modifier.clickable { 
                            showMessageOptions = false
                            discoveryViewModel?.deleteMessage(messageToManage!!, false)
                        }
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }

        if (fullScreenImageUrl != null) {
            Dialog(
                onDismissRequest = { fullScreenImageUrl = null },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clickable { fullScreenImageUrl = null },
                    contentAlignment = Alignment.Center
                ) {
                    SubcomposeAsyncImage(
                        model = fullScreenImageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                        loading = { CircularProgressIndicator(color = Color.White) }
                    )
                }
            }
        }

        if (showDeleteDialog && messageToManage != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Delete Message?") },
                text = { Text("Do you want to delete this message for everyone or just for you?") },
                confirmButton = {
                    TextButton(onClick = {
                        discoveryViewModel?.deleteMessage(messageToManage!!, true)
                        showDeleteDialog = false
                    }) { Text("Delete for Everyone", color = Color.Red) }
                },
                dismissButton = {
                    TextButton(onClick = {
                        discoveryViewModel?.deleteMessage(messageToManage!!, false)
                        showDeleteDialog = false
                    }) { Text("Delete for Me") }
                }
            )
        }

        if (showEditDialog && messageToManage != null) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit Message") },
                text = {
                    TextField(value = editState, onValueChange = { editState = it })
                },
                confirmButton = {
                    TextButton(onClick = {
                        discoveryViewModel?.editMessage(messageToManage!!, editState)
                        showEditDialog = false
                    }) { Text("Save") }
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                reverseLayout = true
            ) {
                items(messages) { message ->
                    SnapchatMessageBubble(
                        message = message,
                        onImageClick = { fullScreenImageUrl = it },
                        onLongClick = {
                            messageToManage = message
                            editState = message.text ?: ""
                            showMessageOptions = true
                        }
                    )
                }
            }

            if (showEmojiPicker) {
                EmojiPicker(onEmojiSelected = { onSendMessage(it) })
            }

            val uploadProgress by (discoveryViewModel?.uploadProgress?.collectAsState() ?: remember { mutableStateOf(null) })
            val uploadError by (discoveryViewModel?.uploadError?.collectAsState() ?: remember { mutableStateOf(null) })
            
            if (uploadProgress != null) {
                LinearProgressIndicator(
                    progress = { uploadProgress!! / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = Color(0xFFFE3C72)
                )
                Text(
                    "Uploading: $uploadProgress%",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            if (uploadError != null) {
                Text(
                    uploadError!!,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).clickable { /* Retry could be here */ },
                    fontSize = 12.sp,
                    color = Color.Red
                )
            }

            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onSendImageClick) {
                        Icon(Icons.Rounded.CameraAlt, null, tint = Color.Gray)
                    }
                    
                    /* Hiding Gift button for now
                    IconButton(onClick = { showGiftSheet = true }) {
                        Icon(Icons.Rounded.CardGiftcard, null, tint = Color(0xFFFE3C72))
                    }
                    */

                    TextField(
                        value = textState,
                        onValueChange = { textState = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(28.dp)),
                        placeholder = { Text("Chat...") },
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        trailingIcon = {
                            IconButton(onClick = { showEmojiPicker = !showEmojiPicker }) {
                                Icon(Icons.Rounded.EmojiEmotions, null, tint = Color.Gray)
                            }
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (textState.isBlank()) {
                        /* Hiding Video/Voice notes for now
                        Row {
                            IconButton(
                                onClick = { /* handled by gesture */ },
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = {
                                                isRecordingVideo = true
                                                videoRecorder.setupCamera {
                                                    videoRecorder.startRecording { file, duration ->
                                                        isRecordingVideo = false
                                                        if (file != null) {
                                                            discoveryViewModel?.sendVideoNote(match.email, file, duration)
                                                        } else {
                                                            Toast.makeText(context, "Recording failed", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            },
                                            onPress = {
                                                awaitRelease()
                                                if (isRecordingVideo) {
                                                    videoRecorder.stopRecording()
                                                }
                                            }
                                        )
                                    }
                                    .background(if(isRecordingVideo) Color.Red else Color.DarkGray, CircleShape)
                            ) {
                                Icon(Icons.Rounded.Videocam, null, tint = Color.White)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { /* Voice recording logic */ },
                                modifier = Modifier
                                    .pointerInput(Unit) {
                                        detectTapGestures(
                                            onLongPress = {
                                                try {
                                                    isRecordingVoice = true
                                                    voiceRecorder.startRecording()
                                                } catch (e: Exception) {
                                                    isRecordingVoice = false
                                                    Toast.makeText(context, "Mic access failed", Toast.LENGTH_SHORT).show()
                                                }
                                            },
                                            onPress = {
                                                awaitRelease()
                                                if (isRecordingVoice) {
                                                    isRecordingVoice = false
                                                    val (file, duration) = voiceRecorder.stopRecording()
                                                    if (file != null && duration > 0) {
                                                        discoveryViewModel?.sendVoiceNote(match.email, file, duration)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    .background(if(isRecordingVoice) Color.Red else Color.DarkGray, CircleShape)
                            ) {
                                Icon(Icons.Rounded.Mic, null, tint = Color.White)
                            }
                        }
                        */
                    } else {
                        IconButton(
                            onClick = {
                                val message = textState.trim()
                                if (message.isNotEmpty()) {
                                    onSendMessage(message)
                                    textState = ""
                                }
                            },
                            modifier = Modifier.background(Color(0xFFFE3C72), CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SnapchatMessageBubble(
    message: ChatMessage,
    onImageClick: (String) -> Unit,
    onLongClick: () -> Unit
) {
    val isMe = message.senderId == "ME"
    val alignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    val color = if (message.isDeletedForEveryone) Color.LightGray else if (isMe) Color(0xFFFE3C72) else Color(0xFFF1F1F1)
    val textColor = if (isMe) Color.White else Color.Black
    val shape = if (isMe) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    
    val timeFormat = remember { java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()) }
    val timeString = timeFormat.format(java.util.Date(message.timestamp))

    val text = message.text ?: ""
    val isImage = text.startsWith("[IMAGE]")
    val isVoice = message.voiceUrl != null
    val isVideoNote = message.videoNoteUrl != null

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(color)
                .padding(if (isImage || isVideoNote) 4.dp else 16.dp)
                .pointerInput(message.id) { // ATOMIC FIX: Use ID as key to prevent offset bug
                    detectTapGestures(onLongPress = { onLongClick() })
                }
                .then(if (isImage) Modifier.clickable { onImageClick(text.removePrefix("[IMAGE]")) } else Modifier)
        ) {
            if (message.isDeletedForEveryone) {
                Text("This message was deleted", color = Color.DarkGray, fontSize = 14.sp, style = androidx.compose.ui.text.TextStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic))
            } else if (isImage) {
                val url = text.removePrefix("[IMAGE]")
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    SubcomposeAsyncImage(
                        model = url,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            }
                        },
                        error = {
                            Icon(Icons.Rounded.Warning, null, tint = Color.Gray, modifier = Modifier.align(Alignment.Center))
                        }
                    )
                }
            } else if (isVoice) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = textColor)
                    Spacer(Modifier.width(8.dp))
                    Text("Voice Note (${message.duration}s)", color = textColor)
                }
            } else if (isVideoNote) {
                Box(modifier = Modifier.size(200.dp).clip(CircleShape).background(Color.Black)) {
                    SubcomposeAsyncImage(
                        model = message.videoNoteUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Icon(Icons.Rounded.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(48.dp))
                }
            } else {
                Column {
                    Text(
                        text = text, 
                        color = textColor, 
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (message.isEdited) {
                        Text("Edited", fontSize = 10.sp, color = textColor.copy(0.7f), modifier = Modifier.align(Alignment.End))
                    }
                }
            }

            // Status Ticks and Time
            Row(
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(timeString, fontSize = 10.sp, color = textColor.copy(alpha = 0.7f))
                if (isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    val tickIcon = if (message.status == "SENT") Icons.Rounded.Done else Icons.Rounded.DoneAll
                    val tickColor = if (message.status == "READ") Color(0xFF34B7F1) else textColor.copy(alpha = 0.7f)
                    Icon(
                        imageVector = tickIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = tickColor
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GiftSheet(
    onGiftSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val gifts = listOf(
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1JmVwPXYxX2ludGVybmFsX2dpZl9ieV9pZCZjdD1n/3o7TKoWXlo3S1Qfe3C/giphy.gif" to "Rose",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1JmVwPXYxX2ludGVybmFsX2dpZl9ieV9pZCZjdD1n/l2SpXzKHRHCvAUnT2/giphy.gif" to "Ring",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExOHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1NHp1JmVwPXYxX2ludGVybmFsX2dpZl9ieV9pZCZjdD1n/3o7TKoWXlo3S1Qfe3C/giphy.gif" to "Heart"
    )
    
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Send a Gift (1 Coin)", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(gifts) { (url, name) ->
                    Card(
                        modifier = Modifier.size(120.dp).clickable { 
                            onGiftSelected(name)
                            onDismiss()
                        },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = url,
                                contentDescription = name,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun EmojiPicker(onEmojiSelected: (String) -> Unit) {
    val emojis = listOf("❤️", "🔥", "✨", "😂", "😘", "🙌", "🥂", "🌹")
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        items(emojis) { emoji ->
            Text(
                text = emoji,
                fontSize = 32.sp,
                modifier = Modifier
                    .clickable { onEmojiSelected(emoji) }
                    .padding(8.dp)
            )
        }
    }
}
