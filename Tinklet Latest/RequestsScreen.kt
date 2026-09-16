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
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.tinklet.bharatdatingapp.data.local.UserProfile

@Composable
fun RequestsScreen(
    incomingNormal: List<UserProfile>,
    incomingSuper: List<UserProfile>,
    incomingRejected: List<UserProfile>,
    sentNormal: List<UserProfile>,
    sentSuper: List<UserProfile>,
    sentRejected: List<UserProfile>,
    onAccept: (UserProfile) -> Unit,
    onReject: (UserProfile) -> Unit,
    onCancel: (UserProfile) -> Unit,
    onSuperLike: (UserProfile) -> Unit,
    onUndoDislike: (UserProfile) -> Unit,
    onProfileClick: (UserProfile) -> Unit
) {
    var mainTab by remember { mutableIntStateOf(0) } // 0: Received, 1: Sent
    val tabs = listOf("Received Requests", "Sent Requests")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = mainTab,
            containerColor = Color.White,
            contentColor = Color(0xFFFE3C72),
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[mainTab]),
                    color = Color(0xFFFE3C72)
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = mainTab == index,
                    onClick = { mainTab = index },
                    text = { Text(title, fontWeight = FontWeight.Bold) }
                )
            }
        }

        if (mainTab == 0) {
            TripleTabLayout(
                list1 = incomingNormal,
                list2 = incomingSuper,
                list3 = incomingRejected,
                tabLabels = listOf("Requests", "Superlikes", "Rejected"),
                onAction1 = onAccept,
                onAction2 = onSuperLike,
                onAction3 = onReject,
                onProfileClick = onProfileClick,
                isSent = false
            )
        } else {
            TripleTabLayout(
                list1 = sentNormal,
                list2 = sentSuper,
                list3 = sentRejected,
                tabLabels = listOf("Requests", "Superlikes", "Rejected"),
                onAction1 = onCancel,
                onAction2 = onCancel,
                onAction3 = onUndoDislike,
                onProfileClick = onProfileClick,
                isSent = true
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripleTabLayout(
    list1: List<UserProfile>,
    list2: List<UserProfile>,
    list3: List<UserProfile>,
    tabLabels: List<String>,
    onAction1: (UserProfile) -> Unit,
    onAction2: (UserProfile) -> Unit,
    onAction3: (UserProfile) -> Unit,
    onProfileClick: (UserProfile) -> Unit,
    isSent: Boolean
) {
    var subTab by remember { mutableIntStateOf(0) }
    
    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = subTab, containerColor = Color.Transparent, divider = {}) {
            tabLabels.forEachIndexed { index, label ->
                val count = when(index) { 0 -> list1.size; 1 -> list2.size; else -> list3.size }
                Tab(selected = subTab == index, onClick = { subTab = index }, text = { Text("$label ($count)", fontSize = 12.sp) })
            }
        }

        val displayList = when(subTab) { 0 -> list1; 1 -> list2; else -> list3 }
        val emptyMsg = "No ${tabLabels[subTab].lowercase()} found."

        if (displayList.isEmpty()) {
            EmptyListView(emptyMsg)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(displayList) { profile ->
                    if (!isSent) {
                        // RECEIVED CARDS
                        if (subTab == 2) {
                            // People who rejected me
                            ReceivedRejectedCard(profile, onProfileClick)
                        } else {
                            ReceivedRequestCard(profile, onAction1, onAction3, onProfileClick)
                        }
                    } else {
                        // SENT CARDS
                        SentActionCard(profile, onAction1, subTab == 2, onProfileClick)
                    }
                }
            }
        }
    }
}

@Composable
fun ReceivedRequestCard(profile: UserProfile, onAccept: (UserProfile) -> Unit, onReject: (UserProfile) -> Unit, onProfileClick: (UserProfile) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(4.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.clickable { onProfileClick(profile) }) {
        Column {
            Box {
                AsyncImage(model = profile.photoUri, contentDescription = null, modifier = Modifier.height(160.dp).fillMaxWidth(), contentScale = ContentScale.Crop)
                if (profile.connectionStatus == "SUPERLIKE") {
                    Box(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color(0xFF2196F3), RoundedCornerShape(4.dp)).padding(4.dp)) {
                        Text("SUPERLIKE", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text("${profile.name}, ${profile.age}", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    // ONLY ACCEPT (Tick) AND REJECT (Cross)
                    IconButton(
                        onClick = { onReject(profile) }, 
                        modifier = Modifier.size(48.dp).background(Color.Red.copy(0.1f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Close, null, tint = Color.Red, modifier = Modifier.size(28.dp))
                    }

                    IconButton(
                        onClick = { onAccept(profile) }, 
                        modifier = Modifier.size(48.dp).background(Color(0xFF4CAF50).copy(0.1f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun ReceivedRejectedCard(profile: UserProfile, onProfileClick: (UserProfile) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.Gray.copy(0.05f)), modifier = Modifier.clickable { onProfileClick(profile) }) {
        Column {
            AsyncImage(model = profile.photoUri, contentDescription = null, modifier = Modifier.height(160.dp).fillMaxWidth().alpha(0.6f), contentScale = ContentScale.Crop)
            Column(modifier = Modifier.padding(8.dp)) {
                Text("${profile.name}, ${profile.age}", fontWeight = FontWeight.Bold, color = Color.Gray)
                Text("Rejected your request", fontSize = 11.sp, color = Color.Red.copy(0.7f))
            }
        }
    }
}

@Composable
fun SentActionCard(profile: UserProfile, onAction: (UserProfile) -> Unit, isUndo: Boolean, onProfileClick: (UserProfile) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(2.dp), colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.clickable { onProfileClick(profile) }) {
        Column {
            Box {
                AsyncImage(model = profile.photoUri, contentDescription = null, modifier = Modifier.height(160.dp).fillMaxWidth(), contentScale = ContentScale.Crop)
                if (profile.connectionStatus == "SUPERLIKE_SENT") {
                    Icon(Icons.Rounded.Star, null, tint = Color(0xFF2196F3), modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(20.dp))
                }
            }
            Column(modifier = Modifier.padding(8.dp)) {
                Text("${profile.name}, ${profile.age}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { onAction(profile) },
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if(isUndo) Color.Gray.copy(0.1f) else Color(0xFFFE3C72).copy(0.1f), contentColor = if(isUndo) Color.DarkGray else Color(0xFFFE3C72)),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(if(isUndo) Icons.AutoMirrored.Rounded.Undo else Icons.Rounded.Close, null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(if(isUndo) "Undo Dislike" else "Cancel Request", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun EmptyListView(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Inbox, null, modifier = Modifier.size(48.dp), tint = Color.LightGray)
            Spacer(Modifier.height(8.dp))
            Text(text, color = Color.Gray, fontSize = 14.sp)
        }
    }
}
