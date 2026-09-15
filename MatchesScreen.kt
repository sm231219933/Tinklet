package com.tinklet.bharatdatingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
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
import coil.compose.AsyncImage
import com.tinklet.bharatdatingapp.data.local.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchesScreen(
    title: String,
    matches: List<UserProfile>,
    onMatchClick: (UserProfile) -> Unit,
    onChatClick: (UserProfile) -> Unit,
    onBack: () -> Unit,
    discoveryViewModel: com.tinklet.bharatdatingapp.ui.viewmodel.DiscoveryViewModel? = null
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredMatches = matches.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(top = 8.dp)) {
                Text(
                    text = title, 
                    fontSize = 24.sp, 
                    fontWeight = FontWeight.ExtraBold, 
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color(0xFFFE3C72)
                )
                
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name...") },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null, tint = Color.Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFFE3C72),
                        unfocusedBorderColor = Color.LightGray
                    )
                )
            }
        }
    ) { padding ->
        if (filteredMatches.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(if(searchQuery.isEmpty()) "No connections yet." else "No one found with that name.", color = Color.Gray)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredMatches) { match ->
                    val lastMsgFlow = discoveryViewModel?.getMessages(match.email)
                    val lastMsg by lastMsgFlow?.collectAsState(initial = emptyList()) ?: remember { mutableStateOf(emptyList<com.tinklet.bharatdatingapp.data.local.ChatMessage>()) }
                    val lastText = lastMsg.lastOrNull()?.text ?: "No messages yet"

                    MatchItem(
                        match = match, 
                        lastMessage = if (title == "Messages") lastText else null,
                        onProfileClick = { onMatchClick(match) },
                        onChatClick = { onChatClick(match) }
                    )
                }
            }
        }
    }
}

@Composable
fun MatchItem(
    match: UserProfile,
    lastMessage: String? = null,
    onProfileClick: () -> Unit,
    onChatClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { if (lastMessage != null) onChatClick() else onProfileClick() }.padding(vertical = 4.dp),
        headlineContent = { Text(match.name, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
        supportingContent = { 
            Text(
                text = lastMessage ?: "Click to view details", 
                color = if (lastMessage != null) Color.Gray else Color(0xFFFE3C72).copy(0.6f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            ) 
        },
        leadingContent = {
            AsyncImage(
                model = match.photoUri,
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.LightGray),
                contentScale = ContentScale.Crop
            )
        }
    )
}
