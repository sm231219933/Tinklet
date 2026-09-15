package com.tinklet.bharatdatingapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class SubPlan(val name: String, val price: String, val duration: String, val discount: String = "")

@Composable
fun SubscriptionScreen(onBack: () -> Unit) {
    val plans = listOf(
        SubPlan("Trial Pack", "₹10", "1 Day"),
        SubPlan("Monthly", "₹99", "30 Days", "Save 20%"),
        SubPlan("Quarterly", "₹279", "90 Days", "Best Seller"),
        SubPlan("Yearly", "₹819", "365 Days", "70% OFF")
    )

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Box(
            modifier = Modifier.fillMaxWidth().height(200.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFFE3C72), Color.Black))),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TINKLET GOLD", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Unlimited Likes • Profile Boost • Global Filters", color = Color.White.copy(0.8f))
            }
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
            items(plans) { plan ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            if (plan.discount.isNotEmpty()) {
                                Text(plan.discount, color = Color(0xFFFE3C72), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(plan.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Text(plan.duration, color = Color.Gray, fontSize = 14.sp)
                        }
                        Button(
                            onClick = { /* Integrate Payment */ },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFE3C72))
                        ) {
                            Text(plan.price)
                        }
                    }
                }
            }
        }
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        ) {
            Text("Maybe Later", color = Color.Gray)
        }
    }
}
