package com.rentmanager.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RentManagerApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentManagerApp() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Rent Manager Dashboard", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Properties") },
                    label = { Text("Shops/Properties") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Tenants") },
                    label = { Text("Tenants") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Money, contentDescription = "Payments") },
                    label = { Text("Payments") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* Add Action */ }) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> PropertyListScreen()
                1 -> TenantListScreen()
                2 -> PaymentSummaryScreen()
            }
        }
    }
}

@Composable
fun PropertyListScreen() {
    val properties = listOf("Shop No. 101 - Main Market", "Shop No. 102 - First Floor", "Flat 2A - Residential")
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Properties & Shops", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        }
        items(properties) { prop ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(prop, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Status: Occupied", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun TenantListScreen() {
    val tenants = listOf("Rahul Sharma - Shop 101", "Amit Kumar - Shop 102", "Priya Singh - Flat 2A")
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Active Tenants", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        }
        items(tenants) { tenant ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(tenant, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("Rent Due: 1st of every month", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun PaymentSummaryScreen() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Rent Payment Overview", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Total Rent Collected This Month", fontSize = 14.sp)
                Text("₹ 45,000", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
