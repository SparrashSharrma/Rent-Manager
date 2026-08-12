package com.rentmanager.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
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
import androidx.core.content.FileProvider
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
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
    val previousDues: Double = 0.0, // PENDING RENT FOR PREVIOUS MONTHS
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

@Database(entities = [PropertyEntity::class, TenantEntity::class, PaymentEntity::class], version = 2, exportSchema = false)
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
                    "rent_manager_v2_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// ==========================================
// 4. MAIN ACTIVITY & SAFFRON-GOLDEN THEME
// ==========================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val SaffronPrimary = Color(0xFFE65100)
            val SaffronContainer = Color(0xFFFFE0B2)
            val GoldenSecondary = Color(0xFFFF8F00)
            val GoldenContainer = Color(0xFFFFF8E1)
            val BackgroundSurface = Color(0xFFFFFDF9)

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = SaffronPrimary,
                    primaryContainer = SaffronContainer,
                    secondary = GoldenSecondary,
                    secondaryContainer = GoldenContainer,
                    surface = BackgroundSurface
                )
            ) {
                RentManagerExportApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RentManagerExportApp() {
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
                        Text("Rent Manager Pro", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF4E342E))
                        Text("Developer: Sparrash Sharrma", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFD84315))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.primaryContainer) {
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
                    Toast.makeText(context, "Property Deleted", Toast.LENGTH_SHORT).show()
                }
                2 -> TenantTab(tenants, payments) { tenant ->
                    coroutineScope.launch {
                        dao.deleteTenant(tenant)
                        val prop = properties.find { it.name == tenant.propertyName }
                        if (prop != null) {
                            dao.insertProperty(prop.copy(isOccupied = false))
                        }
                    }
                    Toast.makeText(context, "Tenant Removed & Shop Vacated", Toast.LENGTH_SHORT).show()
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
            vacantProperties = properties.filter { !it.isOccupied },
            onDismiss = { showAddTenantDialog = false },
            onAdd = { name, phone, shop, rent, prevDues, deposit, dueDay, start, end, idProof ->
                coroutineScope.launch {
                    dao.insertTenant(TenantEntity(name = name, phone = phone, propertyName = shop, monthlyRent = rent, previousDues = prevDues, securityDeposit = deposit, dueDayOfMonth = dueDay, agreementStart = start, agreementEnd = end, idProofNote = idProof))
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
// 5. DASHBOARD SCREEN
// ==========================================

@Composable
fun DashboardScreen(properties: List<PropertyEntity>, tenants: List<TenantEntity>, payments: List<PaymentEntity>) {
    val totalCurrentRent = tenants.sumOf { it.monthlyRent }
    val totalPreviousDues = tenants.sumOf { it.previousDues }
    val totalExpected = totalCurrentRent + totalPreviousDues
    val totalReceived = payments.sumOf { it.amount }
    val pendingAmount = (totalExpected - totalReceived).coerceAtLeast(0.0)
    val vacantShops = properties.count { !it.isOccupied }
    val collectionPercentage = if (totalExpected > 0) ((totalReceived / totalExpected) * 100).coerceAtMost(100.0) else 0.0

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Overview & Financials", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4E342E))
        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Total Expected (Incl. Prev)", "₹${totalExpected.toInt()}", Color(0xFFE65100), Modifier.weight(1f))
            MetricCard("Total Received", "₹${totalReceived.toInt()}", Color(0xFF2E7D32), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Total Pending Dues", "₹${pendingAmount.toInt()}", Color(0xFFC62828), Modifier.weight(1f))
            MetricCard("Vacant Shops", "$vacantShops", Color(0xFFFF8F00), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Collection Rate", fontWeight = FontWeight.Bold, color = Color(0xFF4E342E))
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (collectionPercentage / 100).toFloat() },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text("%.1f%% Collected".format(collectionPercentage), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFE65100))
            }
        }
    }
}

@Composable
fun MetricCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 12.sp, color = Color.DarkGray)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ==========================================
// 6. TABS WITH SEARCH & TENANT EXPORTS & REMINDERS
// ==========================================

@Composable
fun PropertyTab(properties: List<PropertyEntity>, onDelete: (PropertyEntity) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredProperties = properties.filter { it.name.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Shops & Properties (${properties.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4E342E))
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Shop...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredProperties.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Shops found.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredProperties) { prop ->
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
fun TenantTab(tenants: List<TenantEntity>, payments: List<PaymentEntity>, onDelete: (TenantEntity) -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val filteredTenants = tenants.filter { it.name.contains(searchQuery, ignoreCase = true) || it.propertyName.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tenants (${tenants.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4E342E))
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Tenant or Shop...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredTenants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Tenants found.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(filteredTenants) { tenant ->
                    val tenantPayments = payments.filter { it.tenantName.equals(tenant.name, ignoreCase = true) }
                    val totalPaid = tenantPayments.sumOf { it.amount }
                    val totalDue = tenant.monthlyRent + tenant.previousDues
                    val balance = (totalDue - totalPaid).coerceAtLeast(0.0)

                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(tenant.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                IconButton(onClick = { onDelete(tenant) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                                }
                            }
                            Text("Phone: ${tenant.phone} | Shop: ${tenant.propertyName}", fontSize = 13.sp)
                            Text("Monthly Rent: ₹${tenant.monthlyRent.toInt()} | Prev Dues: ₹${tenant.previousDues.toInt()}", fontSize = 13.sp, color = Color.DarkGray)
                            Text("Security Deposit: ₹${tenant.securityDeposit.toInt()}", fontSize = 13.sp, color = Color.DarkGray)
                            Text("Paid: ₹${totalPaid.toInt()} | Total Pending: ₹${balance.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (balance > 0) Color(0xFFC62828) else Color(0xFF2E7D32))
                            Text("Due Day: ${tenant.dueDayOfMonth}th | Agreement: ${tenant.agreementStart} to ${tenant.agreementEnd}", fontSize = 12.sp, color = Color.Gray)

                            Spacer(modifier = Modifier.height(10.dp))
                            
                            // Reminder Message Generation including Previous Dues
                            val msg = StringBuilder().apply {
                                append("Hello ${tenant.name},\n")
                                append("Rent reminder for ${tenant.propertyName}:\n")
                                append("• Monthly Rent: ₹${tenant.monthlyRent.toInt()}\n")
                                if (tenant.previousDues > 0) {
                                    append("• Previous Pending Rent: ₹${tenant.previousDues.toInt()}\n")
                                }
                                append("• Total Amount Due: ₹${balance.toInt()}\n")
                                append("Due Day: ${tenant.dueDayOfMonth}th of month. Please make the payment soon. Thank you!")
                            }.toString()

                            // Action Buttons: Send Message (WhatsApp / SMS / Copy)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { sendWhatsApp(context, tenant.phone, msg) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("WhatsApp", fontSize = 10.sp, color = Color.White)
                                }
                                Button(onClick = { sendSMS(context, tenant.phone, msg) }) {
                                    Text("SMS", fontSize = 10.sp)
                                }
                                OutlinedButton(onClick = { copyToClipboard(context, msg) }) {
                                    Text("Copy Text", fontSize = 10.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Divider(color = Color.LightGray, thickness = 0.8.dp)
                            Spacer(modifier = Modifier.height(8.dp))

                            // EXPORT PDF AND EXCEL BUTTONS
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { exportTenantPDF(context, tenant, tenantPayments) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD84315)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, contentDescription = "PDF", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("PDF Statement", fontSize = 11.sp, color = Color.White)
                                }

                                Button(
                                    onClick = { exportTenantCSV(context, tenant, tenantPayments) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.TableChart, contentDescription = "Excel", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Excel (CSV)", fontSize = 11.sp, color = Color.White)
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
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val filteredPayments = payments.filter { it.tenantName.contains(searchQuery, ignoreCase = true) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Payment History (${payments.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF4E342E))
            Button(onClick = { exportAllPaymentsCSV(context, payments) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) {
                Text("All Payments CSV", fontSize = 11.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Payment by Tenant...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredPayments.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Payments recorded.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filteredPayments) { pay ->
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
}

// ==========================================
// 7. EXPORT & HELPER FUNCTIONS
// ==========================================

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

fun exportTenantPDF(context: Context, tenant: TenantEntity, tenantPayments: List<PaymentEntity>) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint()

        var y = 50f

        paint.textSize = 20f
        paint.isFakeBoldText = true
        paint.color = android.graphics.Color.BLACK
        canvas.drawText("TENANT RENT STATEMENT", 40f, y, paint)

        y += 30f
        paint.textSize = 12f
        paint.isFakeBoldText = false
        canvas.drawText("Tenant Name: ${tenant.name}", 40f, y, paint)
        y += 20f
        canvas.drawText("Phone Number: ${tenant.phone}", 40f, y, paint)
        y += 20f
        canvas.drawText("Assigned Shop/Property: ${tenant.propertyName}", 40f, y, paint)
        y += 20f
        canvas.drawText("Monthly Rent: RS ${tenant.monthlyRent.toInt()}", 40f, y, paint)
        y += 20f
        canvas.drawText("Previous Months Pending Dues: RS ${tenant.previousDues.toInt()}", 40f, y, paint)
        y += 20f
        canvas.drawText("Security Deposit: RS ${tenant.securityDeposit.toInt()}", 40f, y, paint)
        y += 20f
        canvas.drawText("Rent Due Day: ${tenant.dueDayOfMonth}th of every month", 40f, y, paint)
        y += 20f
        canvas.drawText("Agreement Period: ${tenant.agreementStart} to ${tenant.agreementEnd}", 40f, y, paint)

        y += 30f
        val totalPaid = tenantPayments.sumOf { it.amount }
        val totalDue = tenant.monthlyRent + tenant.previousDues
        val balance = (totalDue - totalPaid).coerceAtLeast(0.0)

        paint.isFakeBoldText = true
        canvas.drawText("Total Paid: RS ${totalPaid.toInt()}  |  Total Pending Balance: RS ${balance.toInt()}", 40f, y, paint)

        y += 20f
        paint.color = android.graphics.Color.GRAY
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 30f
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        paint.textSize = 14f
        canvas.drawText("Payment History Log:", 40f, y, paint)

        y += 25f
        paint.textSize = 11f
        paint.isFakeBoldText = false

        if (tenantPayments.isEmpty()) {
            canvas.drawText("No payment transactions recorded for this tenant.", 40f, y, paint)
        } else {
            for (pay in tenantPayments) {
                canvas.drawText("Date: ${pay.date}   |   Amount: RS ${pay.amount.toInt()}   |   Mode: ${pay.mode}", 40f, y, paint)
                y += 20f
                if (y > 780f) break
            }
        }

        pdfDocument.finishPage(page)

        val fileName = "${tenant.name.replace(" ", "_")}_Statement.pdf"
        val file = File(context.cacheDir, fileName)
        pdfDocument.writeTo(file.outputStream())
        pdfDocument.close()

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Tenant PDF Statement"))
    } catch (e: Exception) {
        Toast.makeText(context, "PDF Export Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun exportTenantCSV(context: Context, tenant: TenantEntity, tenantPayments: List<PaymentEntity>) {
    try {
        val totalPaid = tenantPayments.sumOf { it.amount }
        val totalDue = tenant.monthlyRent + tenant.previousDues
        val balance = (totalDue - totalPaid).coerceAtLeast(0.0)

        val builder = StringBuilder()
        builder.append("TENANT STATEMENT\n")
        builder.append("Tenant Name,${tenant.name}\n")
        builder.append("Phone,${tenant.phone}\n")
        builder.append("Shop/Property,${tenant.propertyName}\n")
        builder.append("Monthly Rent,${tenant.monthlyRent}\n")
        builder.append("Previous Months Pending Rent,${tenant.previousDues}\n")
        builder.append("Security Deposit,${tenant.securityDeposit}\n")
        builder.append("Agreement Period,${tenant.agreementStart} to ${tenant.agreementEnd}\n")
        builder.append("Total Rent Paid,$totalPaid\n")
        builder.append("Total Pending Balance,$balance\n\n")

        builder.append("PAYMENT HISTORY\n")
        builder.append("Date,Amount Paid (RS),Payment Mode\n")
        for (p in tenantPayments) {
            builder.append("${p.date},${p.amount},${p.mode}\n")
        }

        val fileName = "${tenant.name.replace(" ", "_")}_Statement.csv"
        val file = File(context.cacheDir, fileName)
        file.writeText(builder.toString())

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Tenant Excel (CSV) Report"))
    } catch (e: Exception) {
        Toast.makeText(context, "CSV Export Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun exportAllPaymentsCSV(context: Context, payments: List<PaymentEntity>) {
    try {
        val csvHeader = "Tenant Name,Amount Paid (RS),Date,Payment Mode\n"
        val csvBody = payments.joinToString("\n") { "${it.tenantName},${it.amount},${it.date},${it.mode}" }
        val csvContent = csvHeader + csvBody

        val file = File(context.cacheDir, "All_Payments_Report.csv")
        file.writeText(csvContent)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share All Payments CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "CSV Export Error: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ==========================================
// 8. DIALOGS
// ==========================================

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTenantDialog(vacantProperties: List<PropertyEntity>, onDismiss: () -> Unit, onAdd: (String, String, String, Double, Double, Double, Int, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedShop by remember { mutableStateOf(vacantProperties.firstOrNull()?.name ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var rent by remember { mutableStateOf(vacantProperties.firstOrNull()?.rentAmount?.toInt()?.toString() ?: "") }
    var prevDues by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("5") }
    var start by remember { mutableStateOf("01 Jan 2026") }
    var end by remember { mutableStateOf("31 Dec 2026") }
    var idProof by remember { mutableStateOf("Aadhaar Card") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tenant") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tenant Name") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()) }

                item {
                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                        OutlinedTextField(
                            value = selectedShop,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Vacant Shop") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            vacantProperties.forEach { prop ->
                                DropdownMenuItem(
                                    text = { Text("${prop.name} (₹${prop.rentAmount.toInt()})") },
                                    onClick = {
                                        selectedShop = prop.name
                                        rent = prop.rentAmount.toInt().toString()
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item { OutlinedTextField(value = rent, onValueChange = { rent = it }, label = { Text("Current Monthly Rent (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = prevDues, onValueChange = { prevDues = it }, label = { Text("Previous Months Pending Rent (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = deposit, onValueChange = { deposit = it }, label = { Text("Security Deposit (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = dueDay, onValueChange = { dueDay = it }, label = { Text("Rent Due Day (1-31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Agreement Start Date") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Agreement End Date") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = idProof, onValueChange = { idProof = it }, label = { Text("ID Proof Note") }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isNotBlank() && selectedShop.isNotBlank()) {
                    onAdd(name, phone, selectedShop, rent.toDoubleOrNull() ?: 0.0, prevDues.toDoubleOrNull() ?: 0.0, deposit.toDoubleOrNull() ?: 0.0, dueDay.toIntOrNull() ?: 5, start, end, idProof)
                }
            }) { Text("Save Tenant") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPaymentDialog(tenants: List<TenantEntity>, onDismiss: () -> Unit, onAdd: (String, Double, String) -> Unit) {
    var selectedTenant by remember { mutableStateOf(tenants.firstOrNull()?.name ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(tenants.firstOrNull()?.monthlyRent?.toInt()?.toString() ?: "") }
    var mode by remember { mutableStateOf("UPI") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Rent Payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(
                        value = selectedTenant,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select Tenant") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        tenants.forEach { t ->
                            DropdownMenuItem(
                                text = { Text("${t.name} (${t.propertyName})") },
                                onClick = {
                                    selectedTenant = t.name
                                    amount = t.monthlyRent.toInt().toString()
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount Paid (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = mode, onValueChange = { mode = it }, label = { Text("Payment Mode (UPI/Cash/Cheque)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                if (selectedTenant.isNotBlank() && amount.isNotBlank()) {
                    onAdd(selectedTenant, amount.toDoubleOrNull() ?: 0.0, mode)
                }
            }) { Text("Save Payment") }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
