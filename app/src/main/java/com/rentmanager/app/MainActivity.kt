package com.rentmanager.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// 1. ROOM DATABASE ENTITIES
// ==========================================

@Entity(tableName = "properties")
data class PropertyEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val type: String,
    val rentAmount: Double,
    val isOccupied: Boolean = false
)

@Entity(tableName = "tenants")
data class TenantEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val phone: String,
    val propertyName: String,
    val monthlyRent: Double,
    val securityDeposit: Double,
    val dueDayOfMonth: Int,
    val agreementStart: String,
    val agreementEnd: String,
    val idProofNote: String
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tenantName: String,
    val amount: Double,
    val date: String,
    val mode: String
)

// ==========================================
// 2. ROOM DAO
// ==========================================

@Dao
interface AppDao {
    @Query("SELECT * FROM properties")
    fun getAllProperties(): Flow<List<PropertyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProperty(property: PropertyEntity)

    @Delete
    suspend fun deleteProperty(property: PropertyEntity)

    @Query("SELECT * FROM tenants")
    fun getAllTenants(): Flow<List<TenantEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTenant(tenant: TenantEntity)

    @Delete
    suspend fun deleteTenant(tenant: TenantEntity)

    @Query("SELECT * FROM payments")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)
}

// ==========================================
// 3. ROOM DATABASE CLASS
// ==========================================

@Database(entities = [PropertyEntity::class, TenantEntity::class, PaymentEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "rent_manager_pro_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// 4. MAIN ACTIVITY & UI
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF1976D2),
                    primaryContainer = Color(0xFFE3F2FD),
                    secondary = Color(0xFF388E3C),
                    secondaryContainer = Color(0xFFE8F5E9)
                )
            ) {
                RentManagerProApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentManagerProApp() {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context) }
    val dao = db.appDao()
    val coroutineScope = rememberCoroutineScope()

    val properties by dao.getAllProperties().collectAsState(initial = emptyList())
    val tenants by dao.getAllTenants().collectAsState(initial = emptyList())
    val payments by dao.getAllPayments().collectAsState(initial = emptyList())

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showAddPropertyDialog by remember { mutableStateOf(false) }
    var showAddTenantDialog by remember { mutableStateOf(false) }
    var showAddPaymentDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Rent Manager Pro", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("Local Storage Active", fontSize = 12.sp, color = Color.DarkGray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Shops") },
                    label = { Text("Shops") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.Person, contentDescription = "Tenants") },
                    label = { Text("Tenants") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Payments, contentDescription = "Payments") },
                    label = { Text("Payments") }
                )
            }
        },
        floatingActionButton = {
            if (selectedTab != 0) {
                FloatingActionButton(
                    onClick = {
                        when (selectedTab) {
                            1 -> showAddPropertyDialog = true
                            2 -> showAddTenantDialog = true
                            3 -> showAddPaymentDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> DashboardScreen(properties, tenants, payments)
                1 -> PropertyTab(properties) { prop ->
                    coroutineScope.launch { dao.deleteProperty(prop) }
                    Toast.makeText(context, "Property Removed", Toast.LENGTH_SHORT).show()
                }
                2 -> TenantTab(tenants) { tenant ->
                    coroutineScope.launch { dao.deleteTenant(tenant) }
                    Toast.makeText(context, "Tenant Removed", Toast.LENGTH_SHORT).show()
                }
                3 -> PaymentTab(payments)
            }
        }
    }

    if (showAddPropertyDialog) {
        AddPropertyDialog(
            onDismiss = { showAddPropertyDialog = false },
            onAdd = { name, type, rent ->
                coroutineScope.launch { dao.insertProperty(PropertyEntity(name = name, type = type, rentAmount = rent, isOccupied = false)) }
                showAddPropertyDialog = false
            }
        )
    }

    if (showAddTenantDialog) {
        AddTenantDialog(
            properties = properties,
            onDismiss = { showAddTenantDialog = false },
            onAdd = { name, phone, shop, rent, deposit, dueDay, start, end, idProof ->
                coroutineScope.launch {
                    dao.insertTenant(TenantEntity(name = name, phone = phone, propertyName = shop, monthlyRent = rent, securityDeposit = deposit, dueDayOfMonth = dueDay, agreementStart = start, agreementEnd = end, idProofNote = idProof))
                    val prop = properties.find { it.name == shop }
                    if (prop != null) {
                        dao.insertProperty(prop.copy(isOccupied = true))
                    }
                }
                showAddTenantDialog = false
            }
        )
    }

    if (showAddPaymentDialog) {
        AddPaymentDialog(
            tenants = tenants,
            onDismiss = { showAddPaymentDialog = false },
            onAdd = { tenantName, amount, mode ->
                val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
                coroutineScope.launch { dao.insertPayment(PaymentEntity(tenantName = tenantName, amount = amount, date = dateStr, mode = mode)) }
                showAddPaymentDialog = false
            }
        )
    }
}

// ==========================================
// 5. DASHBOARD SCREEN (Stats & Summary)
// ==========================================

@Composable
fun DashboardScreen(properties: List<PropertyEntity>, tenants: List<TenantEntity>, payments: List<PaymentEntity>) {
    val totalExpected = tenants.sumOf { it.monthlyRent }
    val totalReceived = payments.sumOf { it.amount }
    val pendingAmount = (totalExpected - totalReceived).coerceAtLeast(0.0)
    val vacantShops = properties.count { !it.isOccupied }
    val collectionPercentage = if (totalExpected > 0) ((totalReceived / totalExpected) * 100).coerceAtMost(100.0) else 0.0

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Financial Overview", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Expected Rent", "₹${totalExpected.toInt()}", Color(0xFF1565C0), Modifier.weight(1f))
            MetricCard("Rent Received", "₹${totalReceived.toInt()}", Color(0xFF2E7D32), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Pending Dues", "₹${pendingAmount.toInt()}", Color(0xFFC62828), Modifier.weight(1f))
            MetricCard("Vacant Shops", "$vacantShops", Color(0xFFEF6C00), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Collection Rate", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (collectionPercentage / 100).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("%.1f%% Collected".format(collectionPercentage), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 12.sp, color = Color.DarkGray)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ==========================================
// 6. TABS & REMINDER FUNCTIONS
// ==========================================

@Composable
fun PropertyTab(properties: List<PropertyEntity>, onDelete: (PropertyEntity) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Shops & Properties (${properties.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (properties.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Shops added yet.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(properties) { prop ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(prop.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Text("Type: ${prop.type} | Rent: ₹${prop.rentAmount.toInt()}", fontSize = 13.sp, color = Color.Gray)
                                Text(if (prop.isOccupied) "Status: Occupied" else "Status: Vacant", fontSize = 12.sp, color = if (prop.isOccupied) Color(0xFF2E7D32) else Color(0xFFD32F2F))
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
fun TenantTab(tenants: List<TenantEntity>, onDelete: (TenantEntity) -> Unit) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tenants (${tenants.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (tenants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Tenants added.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tenants) { tenant ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tenant.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                IconButton(onClick = { onDelete(tenant) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                            Text("Phone: ${tenant.phone} | Shop: ${tenant.propertyName}", fontSize = 13.sp)
                            Text("Rent: ₹${tenant.monthlyRent.toInt()} | Deposit: ₹${tenant.securityDeposit.toInt()}", fontSize = 13.sp, color = Color.DarkGray)
                            Text("Due Day: ${tenant.dueDayOfMonth}th of month", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                            Text("Agreement: ${tenant.agreementStart} to ${tenant.agreementEnd}", fontSize = 12.sp, color = Color.Gray)
                            if (tenant.idProofNote.isNotBlank()) {
                                Text("ID Proof: ${tenant.idProofNote}", fontSize = 12.sp, color = Color.DarkGray)
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val msg = "Hello ${tenant.name}, this is a friendly reminder that your rent of ₹${tenant.monthlyRent.toInt()} for ${tenant.propertyName} is due on ${tenant.dueDayOfMonth}th. Please pay on time. Thank you!"

                                Button(onClick = { sendWhatsApp(context, tenant.phone, msg) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))) {
                                    Text("WhatsApp", fontSize = 11.sp, color = Color.White)
                                }
                                Button(onClick = { sendSMS(context, tenant.phone, msg) }) {
                                    Text("SMS", fontSize = 11.sp)
                                }
                                OutlinedButton(onClick = { copyToClipboard(context, msg) }) {
                                    Text("Copy Text", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaymentTab(payments: List<PaymentEntity>) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Payment Records (${payments.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(payments) { pay ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(pay.tenantName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("Date: ${pay.date} | Mode: ${pay.mode}", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text("+ ₹${pay.amount.toInt()}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// Helper Actions for Reminders
fun sendWhatsApp(context: Context, phone: String, message: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://api.whatsapp.com/send?phone=+91$phone&text=${Uri.encode(message)}"))
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "WhatsApp not installed", Toast.LENGTH_SHORT).show()
    }
}

fun sendSMS(context: Context, phone: String, message: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phone")).apply {
        putExtra("sms_body", message)
    }
    context.startActivity(intent)
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Rent Reminder", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Reminder text copied!", Toast.LENGTH_SHORT).show()
}

// Dialogs
@Composable
fun AddPropertyDialog(onDismiss: () -> Unit, onAdd: (String, String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Shop") }
    var rent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Shop/Property") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name (e.g. Shop 101)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (Shop/Flat)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rent, onValueChange = { rent = it }, label = { Text("Monthly Rent (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) onAdd(name, type, rent.toDoubleOrNull() ?: 0.0) }) { Text("Save") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddTenantDialog(properties: List<PropertyEntity>, onDismiss: () -> Unit, onAdd: (String, String, String, Double, Double, Int, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var shopName by remember { mutableStateOf("") }
    var rent by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("5") }
    var start by remember { mutableStateOf("01 Jan 2026") }
    var end by remember { mutableStateOf("31 Dec 2026") }
    var idProof by remember { mutableStateOf("Aadhaar Card") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tenant Details") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tenant Name") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = shopName, onValueChange = { shopName = it }, label = { Text("Assigned Shop Name") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = rent, onValueChange = { rent = it }, label = { Text("Rent Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = deposit, onValueChange = { deposit = it }, label = { Text("Security Deposit (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = dueDay, onValueChange = { dueDay = it }, label = { Text("Rent Due Day (1-31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Agreement Start Date") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Agreement End Date") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = idProof, onValueChange = { idProof = it }, label = { Text("ID Proof / Note") }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank()) {
                    onAdd(name, phone, shopName, rent.toDoubleOrNull() ?: 0.0, deposit.toDoubleOrNull() ?: 0.0, dueDay.toIntOrNull() ?: 5, start, end, idProof)
                }
            }) { Text("Save Tenant") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddPaymentDialog(tenants: List<TenantEntity>, onDismiss: () -> Unit, onAdd: (String, Double, String) -> Unit) {
    var tenantName by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf("UPI") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = tenantName, onValueChange = { tenantName = it }, label = { Text("Tenant Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount Paid (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mode, onValueChange = { mode = it }, label = { Text("Mode (UPI/Cash/Cheque)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (tenantName.isNotBlank() && amount.isNotBlank()) {
                    onAdd(tenantName, amount.toDoubleOrNull() ?: 0.0, mode)
                }
            }) { Text("Save Payment") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
