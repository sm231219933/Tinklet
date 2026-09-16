package com.tinklet.bharatdatingapp.ui.screens

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tinklet.bharatdatingapp.data.local.UserProfile
import com.tinklet.bharatdatingapp.utils.AdManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinCenterScreen(
    user: UserProfile?,
    leaderboard: List<UserProfile>,
    onAdRewarded: () -> Unit,
    onBadgePurchase: (String, Int) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    var showRichList by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tinklet Coin Center", fontWeight = FontWeight.ExtraBold, color = Color(0xFFFE3C72)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color(0xFFFE3C72)) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Coin Balance Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD700).copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Rounded.MonetizationOn, null, tint = Color(0xFFFFD700), modifier = Modifier.size(64.dp))
                    Text("Current Balance", fontSize = 16.sp, color = Color.Gray)
                    Text("${user?.coins ?: 0} Coins", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.Black)
                }
            }

            Spacer(Modifier.height(24.dp))

            // Earn Section
            CoinSectionHeader("Earn Coins")
            
            CoinActionCard(
                title = "Watch Rewarded Ad",
                subtitle = "Earn 5 coins instantly",
                icon = Icons.Rounded.PlayCircle,
                buttonText = "Watch Now",
                color = Color(0xFF4CAF50)
            ) {
                activity?.let {
                    AdManager.showRewardedAd(it) {
                        onAdRewarded()
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            CoinActionCard(
                title = "Invite Friends",
                subtitle = "Earn 50 coins per referral",
                icon = Icons.Rounded.Share,
                buttonText = "Share App",
                color = Color(0xFF2196F3)
            ) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "Join me on Tinklet! Use my referral code: ${user?.referralCode ?: "-"} to join the verified dating community.")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Referral Code"))
            }

            Spacer(Modifier.height(24.dp))

            // Membership Section
            CoinSectionHeader("Exclusive Badges")
            Text("Badges give you priority in discovery list!", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            
            BadgeActionCard(
                title = "Bronze Badge",
                subtitle = "Cost: 250 coins",
                icon = Icons.Rounded.WorkspacePremium,
                buttonText = if (user?.badgeType == "BRONZE") "Active" else "Get",
                color = Color(0xFFCD7F32), // Bronze
                enabled = user?.badgeType != "BRONZE"
            ) {
                onBadgePurchase("BRONZE", 250)
            }

            Spacer(Modifier.height(8.dp))

            BadgeActionCard(
                title = "Silver Badge",
                subtitle = "Cost: 550 coins",
                icon = Icons.Rounded.WorkspacePremium,
                buttonText = if (user?.badgeType == "SILVER") "Active" else "Get",
                color = Color(0xFFC0C0C0), // Silver
                enabled = user?.badgeType != "SILVER"
            ) {
                onBadgePurchase("SILVER", 550)
            }

            Spacer(Modifier.height(8.dp))

            BadgeActionCard(
                title = "Golden Badge",
                subtitle = "Cost: 1300 coins",
                icon = Icons.Rounded.WorkspacePremium,
                buttonText = if (user?.badgeType == "GOLDEN") "Active" else "Get",
                color = Color(0xFFFFD700), // Gold
                enabled = user?.badgeType != "GOLDEN"
            ) {
                onBadgePurchase("GOLDEN", 1300)
            }

            Spacer(Modifier.height(24.dp))

            // Leaderboard Section
            CoinSectionHeader("Tinklet Rich List")
            Button(
                onClick = { showRichList = true },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE3C72))
            ) {
                Icon(Icons.Rounded.EmojiEvents, null)
                Spacer(Modifier.width(12.dp))
                Text("View Rich List", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }

            if (showRichList) {
                RichListDialog(leaderboard) { showRichList = false }
            }

            Spacer(Modifier.height(32.dp))

            // Guide
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Coin Economy Guide", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    GuideItem("🪙 Like a profile", "-1 Coin")
                    GuideItem("⭐ Superlike", "-10 Coins")
                    GuideItem("🏆 Bronze Badge", "-250 Coins")
                    GuideItem("💎 Silver Badge", "-550 Coins")
                    GuideItem("🥇 Golden Badge", "-1300 Coins")
                    GuideItem("📺 Watch an ad", "+5 Coins")
                    GuideItem("🤝 Friend joins & logins", "+50 Coins")
                    Spacer(Modifier.height(8.dp))
                    Text("Badges stay active for 30 days and put you at the top of other users' discovery lists.", fontSize = 12.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun RichListDialog(leaderboard: List<UserProfile>, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text("Tinklet Rich List 🏆", fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFFFE3C72))
                Spacer(Modifier.height(16.dp))
                if (leaderboard.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No one in the list yet...", color = Color.Gray)
                    }
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                        itemsIndexed(leaderboard) { index, user ->
                            LeaderboardItem(index + 1, user)
                            if (index < leaderboard.size - 1) HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun LeaderboardItem(rank: Int, user: UserProfile) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "#$rank", 
            fontWeight = FontWeight.Bold, 
            color = when(rank) {
                1 -> Color(0xFFFFD700)
                2 -> Color(0xFFC0C0C0)
                3 -> Color(0xFFCD7F32)
                else -> Color.Gray
            },
            modifier = Modifier.width(40.dp)
        )
        Text(user.name, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text("${user.coins} Coins", color = Color(0xFFFE3C72), fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
fun BadgeActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, buttonText: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    CoinActionCard(title, subtitle, icon, buttonText, color, enabled, onClick)
}

@Composable
fun CoinSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        fontWeight = FontWeight.Black,
        fontSize = 18.sp
    )
}

@Composable
fun CoinActionCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, buttonText: String, color: Color, enabled: Boolean = true, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).background(color.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = color)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(subtitle, fontSize = 12.sp, color = Color.Gray)
            }
            Button(
                onClick = onClick,
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(containerColor = color),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(buttonText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun GuideItem(label: String, cost: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 14.sp)
        Text(cost, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFE3C72))
    }
}
