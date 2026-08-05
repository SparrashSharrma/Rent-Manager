package com.rentmanager.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Data Models
data class Property(
    val id: Int,
    val name: String,
    val type: String,
    val rentAmount: Double,
    var isOccupied: Boolean = true
)

data class Tenant(
    val id: Int,
    val name: String,
    val phone: String,
    val propertyName: String,
    val monthlyRent: Double
)

data class Payment(
    val id: Int,
    val tenantName: String,
    val amount: Double,
    val date: String,
    val mode: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1E88E5),
                    primaryContainer = Color(0xFFE3F2FD),
                    secondary = Color(0xFF43A047),
                    secondaryContainer = Color(0xFFE8F5E9)
                )
            ) {
                RentManagerMainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentManagerMainScreen() {
    val context = LocalContext.current

    // Active App State
    val properties = remember {
        mutableStateListOf(
            Property(1, "Shop No. 101", "Shop", 8500.0, true),
            Property(2, "Shop No. 102", "Shop", 7000.0, true)
        )
    }

    val tenants = remember {
        mutableStateListOf(
            Tenant(1, "Ramesh Kumar", "9876543210", "Shop No. 101", 8500.0),
            Tenant(2, "Sunil Sharma", "9812345678", "Shop No. 102", 7000.0)
        )
    }

    val payments = remember {
        mutableStateListOf(
            Payment(1, "Ramesh Kumar", 8500.0, "01 Aug 2026", "UPI"),
            Payment(2, "Sunil Sharma", 7000.0, "02 Aug 2026", "Cash")
        )
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddPropertyDialog by remember { mutableStateOf(false) }
    var showAddTenantDialog by remember { mutableStateOf(false) }
    var showAddPaymentDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rent Manager", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Shops, Tenants & Payments", fontSize = 12.sp, color = Color.DarkGray)
                    }
                },
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
                    icon = { Icon(Icons.Default.Payments, contentDescription = "Payments") },
                    label = { Text("Payments") }
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    when (selectedTab) {
                        0 -> showAddPropertyDialog = true
                        1 -> showAddTenantDialog = true
                        2 -> showAddPaymentDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedTab) {
                0 -> PropertyTab(
                    properties = properties,
                    onDelete = { prop ->
                        properties.remove(prop)
                        Toast.makeText(context, "Property Removed", Toast.LENGTH_SHORT).show()
                    }
                )
                1 -> TenantTab(
                    tenants = tenants,
                    onDelete = { tenant ->
                        tenants.remove(tenant)
                        Toast.makeText(context, "Tenant Removed", Toast.LENGTH_SHORT).show()
                    }
                )
                2 -> PaymentTab(payments = payments)
            }
        }
    }

    // Dialogs
    if (showAddPropertyDialog) {
        AddPropertyDialog(
            onDismiss = { showAddPropertyDialog = false },
            onAdd = { newProp ->
                properties.add(newProp)
                showAddPropertyDialog = false
                Toast.makeText(context, "Property Added!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddTenantDialog) {
        AddTenantDialog(
            onDismiss = { showAddTenantDialog = false },
            onAdd = { newTenant ->
                tenants.add(newTenant)
                showAddTenantDialog = false
                Toast.makeText(context, "Tenant Added!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAddPaymentDialog) {
        AddPaymentDialog(
            onDismiss = { showAddPaymentDialog = false },
            onAdd = { newPayment ->
                payments.add(newPayment)
                showAddPaymentDialog = false
                Toast.makeText(context, "Payment Logged!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
fun PropertyTab(properties: List<Property>, onDelete: (Property) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Properties & Shops", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (properties.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Properties added yet. Click + to add.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(properties) { prop ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prop.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Type: ${prop.type} | Rent: ₹${prop.rentAmount.toInt()}/mo", fontSize = 14.sp, color = Color.Gray)
                            }
                            IconButton(onClick = { onDelete(prop) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TenantTab(tenants: List<Tenant>, onDelete: (Tenant) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Active Tenants", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (tenants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Tenants added. Click + to add.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tenants) { tenant ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tenant.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Phone: ${tenant.phone}", fontSize = 13.sp)
                                Text("Assigned: ${tenant.propertyName}", fontSize = 13.sp, color = Color.DarkGray)
                                Text("Rent: ₹${tenant.monthlyRent.toInt()}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDelete(tenant) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentTab(payments: List<Payment>) {
    val totalCollected = payments.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Total Rent Collected This Month", fontSize = 14.sp, color = Color.DarkGray)
                Text("₹ ${totalCollected.toInt()}", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("Payment History", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (payments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No payment records yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(payments) { pay ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(pay.tenantName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Date: ${pay.date} | Mode: ${pay.mode}", fontSize = 13.sp, color = Color.Gray)
                            }
                            Text("+ ₹${pay.amount.toInt()}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddPropertyDialog(onDismiss: () -> Unit, onAdd: (Property) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Shop") }
    var rent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Property / Shop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (e.g. Shop 103)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (Shop/Flat)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rent, onValueChange = { rent = it }, label = { Text("Monthly Rent Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && rent.isNotBlank()) {
                    onAdd(Property(System.currentTimeMillis().toInt(), name, type, rent.toDoubleOrNull() ?: 0.0))
                }
            }) { Text("Add") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddTenantDialog(onDismiss: () -> Unit, onAdd: (Tenant) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var propName by remember { mutableStateOf("") }
    var rent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tenant") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tenant Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = propName, onValueChange = { propName = it }, label = { Text("Assigned Shop") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rent, onValueChange = { rent = it }, label = { Text("Monthly Rent (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAdd(Tenant(System.currentTimeMillis().toInt(), name, phone, propName, rent.toDoubleOrNull() ?: 0.0))
                }
            }) { Text("Add") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddPaymentDialog(onDismiss: () -> Unit, onAdd: (Payment) -> Unit) {
    var tenantName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("UPI") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Rent Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("Tenant Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount Paid (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mode, onValueChange = { mode = it }, label = { Text("Payment Mode (UPI/Cash)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (tenantName.isNotBlank() && amount.isNotBlank()) {
                    onAdd(Payment(System.currentTimeMillis().toInt(), tenantName, amount.toDoubleOrNull() ?: 0.0, "Today", mode))
                }
            }) { Text("Save Payment") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
