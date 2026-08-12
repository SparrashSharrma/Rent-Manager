package com.rentmanager.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================
// 1. ROOM DATABASE ENTITIES
// ============================================================

@Entity(tableName = "properties")
data class PropertyEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val type: String,
    val rentAmount: Double,
    val isOccupied: Boolean = false
)

@Entity(tableName = "tenants")
data class TenantEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val name: String,
    val phone: String,
    val propertyName: String,
    val monthlyRent: Double,
    val previousDues: Double = 0.0,
    val securityDeposit: Double,
    val dueDayOfMonth: Int,
    val agreementStart: String,
    val agreementEnd: String,
    val idProofNote: String,
    val tenantPhotoUri: String = "",
    val idProofPhotoUris: String = "" // Comma-separated URIs (Max 3)
)

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val tenantName: String,
    val amount: Double,
    val date: String,
    val mode: String,
    val rentMonth: String = "",
    val note: String = ""
)

// ============================================================
// 2. ROOM DAO
// ============================================================

@Dao
interface AppDao {

    @Query("SELECT * FROM properties ORDER BY id ASC")
    fun getAllProperties(): Flow<List<PropertyEntity>>

    @Query("SELECT * FROM properties ORDER BY id ASC")
    suspend fun getPropertyList(): List<PropertyEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProperty(property: PropertyEntity)

    @Update
    suspend fun updateProperty(property: PropertyEntity)

    @Delete
    suspend fun deleteProperty(property: PropertyEntity)

    @Query("SELECT * FROM tenants ORDER BY id ASC")
    fun getAllTenants(): Flow<List<TenantEntity>>

    @Query("SELECT * FROM tenants ORDER BY id ASC")
    suspend fun getTenantList(): List<TenantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTenant(tenant: TenantEntity)

    @Update
    suspend fun updateTenant(tenant: TenantEntity)

    @Delete
    suspend fun deleteTenant(tenant: TenantEntity)

    @Query("UPDATE tenants SET propertyName = :newName WHERE propertyName = :oldName")
    suspend fun updateTenantPropertyName(oldName: String, newName: String)

    @Query("SELECT * FROM payments ORDER BY id DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments ORDER BY id ASC")
    suspend fun getPaymentList(): List<PaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity)

    @Query("UPDATE payments SET tenantName = :newName WHERE tenantName = :oldName")
    suspend fun updatePaymentTenantName(oldName: String, newName: String)

    @Query("DELETE FROM properties")
    suspend fun deleteAllProperties()

    @Query("DELETE FROM tenants")
    suspend fun deleteAllTenants()

    @Query("DELETE FROM payments")
    suspend fun deleteAllPayments()
}

// ============================================================
// 3. ROOM DATABASE
// ============================================================

@Database(
    entities = [
        PropertyEntity::class,
        TenantEntity::class,
        PaymentEntity::class
    ],
    version = 4,
    exportSchema = false
)
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
                    "rent_manager_tript_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}

// ============================================================
// 4. MAIN ACTIVITY & COLOR PALETTE
// ============================================================

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val saffronPrimary = Color(0xFFE65100)
            val saffronContainer = Color(0xFFFFE0B2)
            val goldenSecondary = Color(0xFFFFB300)
            val goldenContainer = Color(0xFFFFF8E1)
            val backgroundSurface = Color(0xFFFFFDF8)

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = saffronPrimary,
                    primaryContainer = saffronContainer,
                    secondary = goldenSecondary,
                    secondaryContainer = goldenContainer,
                    surface = backgroundSurface
                )
            ) {
                RentManagerExportApp()
            }
        }
    }
}

// ============================================================
// 5. MAIN APP
// ============================================================

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

    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var showAddPropertyDialog by remember { mutableStateOf(false) }
    var showAddTenantDialog by remember { mutableStateOf(false) }
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }

    var propertyToEdit by remember { mutableStateOf<PropertyEntity?>(null) }
    var tenantToEdit by remember { mutableStateOf<TenantEntity?>(null) }
    var fullScreenPhotoUri by remember { mutableStateOf<String?>(null) }

    val restoreFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            coroutineScope.launch {
                restoreFromBackupJson(context, dao, it)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFE65100),
                            modifier = Modifier
                                .size(42.dp)
                                .border(2.dp, Color(0xFFFFD54F), RoundedCornerShape(10.dp))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Rent Manager",
                                    tint = Color(0xFFFFF59D),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "Rent Manager",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = Color(0xFF3E2723)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = Color(0xFFD32F2F),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        "v1.3.6",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            Text(
                                "Tript Digitals | Dev: Sparash Ram Sharma",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFE65100)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showBackupDialog = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "Backup & Restore",
                            tint = Color(0xFFE65100)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Search, contentDescription = "Shops") },
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
                    icon = { Icon(Icons.Default.MoreVert, contentDescription = "Payments") },
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
                            2 -> {
                                if (properties.any { !it.isOccupied }) {
                                    showAddTenantDialog = true
                                } else {
                                    Toast.makeText(context, "No vacant shop available", Toast.LENGTH_SHORT).show()
                                }
                            }
                            3 -> {
                                if (tenants.isNotEmpty()) {
                                    showAddPaymentDialog = true
                                } else {
                                    Toast.makeText(context, "Add a tenant first", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    containerColor = Color(0xFFE65100),
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (selectedTab) {
                0 -> DashboardScreen(properties = properties, tenants = tenants, payments = payments)
                1 -> PropertyTab(
                    properties = properties,
                    onEdit = { propertyToEdit = it },
                    onDelete = { property ->
                        coroutineScope.launch {
                            val assignedTenant = tenants.find { it.propertyName.equals(property.name, ignoreCase = true) }
                            if (assignedTenant != null) {
                                Toast.makeText(context, "Cannot delete an occupied shop", Toast.LENGTH_LONG).show()
                            } else {
                                dao.deleteProperty(property)
                                Toast.makeText(context, "Property deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )
                2 -> TenantTab(
                    tenants = tenants,
                    payments = payments,
                    onEdit = { tenantToEdit = it },
                    onImageClick = { fullScreenPhotoUri = it },
                    onDelete = { tenant ->
                        coroutineScope.launch {
                            dao.deleteTenant(tenant)
                            val prop = properties.find { it.name.equals(tenant.propertyName, ignoreCase = true) }
                            if (prop != null) {
                                dao.insertProperty(prop.copy(isOccupied = false))
                            }
                            Toast.makeText(context, "Tenant removed & shop vacated", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                3 -> PaymentTab(payments = payments)
            }
        }
    }

    if (showAddPropertyDialog) {
        AddPropertyDialog(
            onDismiss = { showAddPropertyDialog = false },
            onAdd = { name, type, rent ->
                coroutineScope.launch {
                    dao.insertProperty(
                        PropertyEntity(name = name.trim(), type = type.trim(), rentAmount = rent, isOccupied = false)
                    )
                }
                showAddPropertyDialog = false
            }
        )
    }

    if (showAddTenantDialog) {
        AddTenantDialog(
            vacantProperties = properties.filter { !it.isOccupied },
            onDismiss = { showAddTenantDialog = false },
            onAdd = { name, phone, shop, rent, prevDues, deposit, dueDay, start, end, idProof, photoUri, idPhotoUris ->
                coroutineScope.launch {
                    dao.insertTenant(
                        TenantEntity(
                            name = name.trim(),
                            phone = phone.trim(),
                            propertyName = shop.trim(),
                            monthlyRent = rent,
                            previousDues = prevDues,
                            securityDeposit = deposit,
                            dueDayOfMonth = dueDay.coerceIn(1, 31),
                            agreementStart = start.trim(),
                            agreementEnd = end.trim(),
                            idProofNote = idProof.trim(),
                            tenantPhotoUri = photoUri,
                            idProofPhotoUris = idPhotoUris
                        )
                    )
                    val prop = properties.find { it.name.equals(shop, ignoreCase = true) }
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
            onAdd = { tenantName, amount, mode, rentMonth, paymentDate, note ->
                coroutineScope.launch {
                    dao.insertPayment(
                        PaymentEntity(
                            tenantName = tenantName,
                            amount = amount,
                            date = paymentDate,
                            mode = mode,
                            rentMonth = rentMonth,
                            note = note
                        )
                    )
                }
                showAddPaymentDialog = false
            }
        )
    }

    propertyToEdit?.let { property ->
        EditPropertyDialog(
            property = property,
            onDismiss = { propertyToEdit = null },
            onUpdate = { updatedProperty ->
                coroutineScope.launch {
                    val oldName = property.name
                    val newName = updatedProperty.name
                    dao.updateProperty(updatedProperty)
                    if (!oldName.equals(newName, ignoreCase = true)) {
                        dao.updateTenantPropertyName(oldName, newName)
                    }
                }
                propertyToEdit = null
                Toast.makeText(context, "Shop details updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    tenantToEdit?.let { tenant ->
        EditTenantDialog(
            tenant = tenant,
            allProperties = properties,
            onDismiss = { tenantToEdit = null },
            onUpdate = { updatedTenant ->
                coroutineScope.launch {
                    val oldName = tenant.name
                    val oldShop = tenant.propertyName
                    val newShop = updatedTenant.propertyName

                    dao.updateTenant(updatedTenant)

                    if (!oldName.equals(updatedTenant.name, ignoreCase = true)) {
                        dao.updatePaymentTenantName(oldName, updatedTenant.name)
                    }

                    if (!oldShop.equals(newShop, ignoreCase = true)) {
                        val previousShop = properties.find { it.name.equals(oldShop, ignoreCase = true) }
                        val newProperty = properties.find { it.name.equals(newShop, ignoreCase = true) }
                        if (previousShop != null) dao.insertProperty(previousShop.copy(isOccupied = false))
                        if (newProperty != null) dao.insertProperty(newProperty.copy(isOccupied = true))
                    }
                }
                tenantToEdit = null
                Toast.makeText(context, "Tenant details updated", Toast.LENGTH_SHORT).show()
            }
        )
    }

    fullScreenPhotoUri?.let { uri ->
        Dialog(onDismissRequest = { fullScreenPhotoUri = null }) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(12.dp)) {
                    val bitmap = rememberBitmapFromUri(uri)
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Full Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().height(350.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier.size(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Image load error", color = Color.White)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = { fullScreenPhotoUri = null },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
                    ) {
                        Text("Close", color = Color.White)
                    }
                }
            }
        }
    }

    if (showBackupDialog) {
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Backup & Restore Data", color = Color(0xFF3E2723), fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Backup your shops, tenants, photos and payment records.")
                    Button(
                        onClick = {
                            coroutineScope.launch { exportBackupJson(context, dao) }
                            showBackupDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null, tint = Color.White)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Backup Data", color = Color.White)
                    }
                    OutlinedButton(
                        onClick = {
                            restoreFileLauncher.launch("application/json")
                            showBackupDialog = false
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF8F00)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore Backup")
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF8E1), RoundedCornerShape(6.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "App Version: 1.3.6 | Tript Digitals\nDev: Sparash Ram Sharma",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE65100)
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBackupDialog = false }) { Text("Close") }
            }
        )
    }
}

// ============================================================
// 6. DASHBOARD
// ============================================================

@Composable
fun DashboardScreen(
    properties: List<PropertyEntity>,
    tenants: List<TenantEntity>,
    payments: List<PaymentEntity>
) {
    val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    val totalCurrentRent = tenants.sumOf { it.monthlyRent }
    val totalPreviousDues = tenants.sumOf { it.previousDues }
    val totalExpected = totalCurrentRent + totalPreviousDues

    val totalReceivedCurrentMonth = payments
        .filter { it.rentMonth == currentMonth }
        .sumOf { it.amount }

    val pendingAmount = (totalExpected - totalReceivedCurrentMonth).coerceAtLeast(0.0)
    val vacantShops = properties.count { !it.isOccupied }

    val collectionPercentage = if (totalExpected > 0) {
        (totalReceivedCurrentMonth / totalExpected * 100).coerceIn(0.0, 100.0)
    } else {
        0.0
    }

    var paidCount = 0
    var overdueCount = 0

    tenants.forEach { tenant ->
        val paidThisMonth = payments
            .filter { it.tenantName.equals(tenant.name, ignoreCase = true) && it.rentMonth == currentMonth }
            .sumOf { it.amount }
        val due = tenant.monthlyRent + tenant.previousDues
        if (paidThisMonth >= due) paidCount++ else overdueCount++
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Overview & Financials", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E2723))
                Text(
                    "Current Month: ${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            Surface(
                color = Color(0xFFFFF176),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFBC02D))
            ) {
                Text(
                    "v1.3.6",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE65100),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Expected Rent + Dues", "₹${totalExpected.toInt()}", Color(0xFFE65100), Modifier.weight(1f))
            MetricCard("Received This Month", "₹${totalReceivedCurrentMonth.toInt()}", Color(0xFF2E7D32), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Total Pending", "₹${pendingAmount.toInt()}", Color(0xFFD32F2F), Modifier.weight(1f))
            MetricCard("Vacant Shops", "$vacantShops", Color(0xFFFF8F00), Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Paid Tenants", "$paidCount", Color(0xFF2E7D32), Modifier.weight(1f))
            MetricCard("Overdue Tenants", "$overdueCount", Color(0xFFD32F2F), Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFE082))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Collection Rate", fontWeight = FontWeight.Bold, color = Color(0xFF3E2723))
                    Text("%.1f%%".format(collectionPercentage), fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                }
                Spacer(modifier = Modifier.height(8.dp))

                val progressFraction = (collectionPercentage / 100).toFloat().coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                        .background(Color(0xFFFFECB3), RoundedCornerShape(6.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .fillMaxHeight()
                            .background(Color(0xFFE65100), RoundedCornerShape(6.dp))
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Developed by Sparash Ram Sharma | Tript Digitals",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF8D6E63)
            )
        }
    }
}

// ============================================================
// 7. METRIC CARD
// ============================================================

@Composable
fun MetricCard(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, fontSize = 11.sp, color = Color.DarkGray)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

// ============================================================
// 8. PROPERTY TAB
// ============================================================

@Composable
fun PropertyTab(
    properties: List<PropertyEntity>,
    onEdit: (PropertyEntity) -> Unit,
    onDelete: (PropertyEntity) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredProperties = properties.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.type.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Shops & Properties (${properties.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E2723))
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Shop...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFE65100)) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filteredProperties.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No Shops found.", color = Color.Gray)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filteredProperties) { prop ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(prop.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF3E2723))
                                Surface(
                                    color = if (prop.isOccupied) Color(0xFFE8F5E9) else Color(0xFFFFEBEE),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        if (prop.isOccupied) "Occupied" else "Vacant",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (prop.isOccupied) Color(0xFF2E7D32) else Color(0xFFD32F2F),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text("Type: ${prop.type} | Rent: ₹${prop.rentAmount.toInt()}", fontSize = 13.sp, color = Color.Gray)

                            Spacer(modifier = Modifier.height(10.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(0.8.dp).background(Color(0xFFFFE082)))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onEdit(prop) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Edit Shop", fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = { onDelete(prop) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Delete", fontSize = 12.sp, color = Color(0xFFD32F2F))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 9. TENANT TAB (WITH PHOTO & ID PROOF THUMBNAILS)
// ============================================================

@Composable
fun TenantTab(
    tenants: List<TenantEntity>,
    payments: List<PaymentEntity>,
    onEdit: (TenantEntity) -> Unit,
    onImageClick: (String) -> Unit,
    onDelete: (TenantEntity) -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())

    val filteredTenants = tenants.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.propertyName.contains(searchQuery, ignoreCase = true) ||
                it.phone.contains(searchQuery, ignoreCase = true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Tenants (${tenants.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E2723))
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Tenant or Shop...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFE65100)) },
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
                    val currentMonthPayments = tenantPayments.filter { it.rentMonth == currentMonth }
                    val totalPaidThisMonth = currentMonthPayments.sumOf { it.amount }
                    val totalHistoricalPaid = tenantPayments.sumOf { it.amount }
                    val totalDue = tenant.monthlyRent + tenant.previousDues
                    val balance = (totalDue - totalPaidThisMonth).coerceAtLeast(0.0)

                    val status = when {
                        balance <= 0 -> "PAID" to Color(0xFF2E7D32)
                        totalPaidThisMonth > 0 -> "PARTIAL" to Color(0xFFFF8F00)
                        else -> "OVERDUE" to Color(0xFFD32F2F)
                    }

                    val idPhotosList = tenant.idProofPhotoUris.split(",").filter { it.isNotBlank() }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (tenant.tenantPhotoUri.isNotBlank()) {
                                        val bitmap = rememberBitmapFromUri(tenant.tenantPhotoUri)
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = "Tenant Photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(45.dp)
                                                    .clip(CircleShape)
                                                    .border(1.5.dp, Color(0xFFE65100), CircleShape)
                                                    .clickable { onImageClick(tenant.tenantPhotoUri) }
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                        }
                                    }
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(tenant.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF3E2723))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Box(
                                                modifier = Modifier
                                                    .background(status.second.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Text(status.first, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = status.second)
                                            }
                                        }
                                        Text("Shop: ${tenant.propertyName}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Phone: ${tenant.phone}", fontSize = 13.sp)
                            Text("Monthly Rent: ₹${tenant.monthlyRent.toInt()}", fontSize = 13.sp, color = Color.DarkGray)
                            Text("Previous Dues: ₹${tenant.previousDues.toInt()}", fontSize = 13.sp, color = Color.DarkGray)
                            Text("Security Deposit: ₹${tenant.securityDeposit.toInt()}", fontSize = 13.sp, color = Color.DarkGray)
                            Text("Paid This Month: ₹${totalPaidThisMonth.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32))
                            Text("Pending: ₹${balance.toInt()}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (balance > 0) Color(0xFFD32F2F) else Color(0xFF2E7D32))
                            Text("Total Historical Paid: ₹${totalHistoricalPaid.toInt()}", fontSize = 12.sp, color = Color.Gray)
                            Text("Rent Due: ${ordinalDay(tenant.dueDayOfMonth)} of every month", fontSize = 12.sp, color = Color.Gray)
                            Text("Agreement: ${tenant.agreementStart} to ${tenant.agreementEnd}", fontSize = 12.sp, color = Color.Gray)
                            Text("ID Proof Note: ${tenant.idProofNote}", fontSize = 12.sp, color = Color.Gray)

                            if (idPhotosList.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("ID Proof Photos (${idPhotosList.size}/3):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                                Spacer(modifier = Modifier.height(4.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(idPhotosList) { photoUri ->
                                        val bitmap = rememberBitmapFromUri(photoUri)
                                        if (bitmap != null) {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = "ID Photo",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .size(50.dp)
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .border(1.dp, Color(0xFFFFB300), RoundedCornerShape(6.dp))
                                                    .clickable { onImageClick(photoUri) }
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val msg = buildRentReminderMessage(tenant, balance)

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { sendWhatsApp(context, tenant.phone, msg) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("WhatsApp", fontSize = 10.sp, color = Color.White)
                                }
                                Button(
                                    onClick = { sendSMS(context, tenant.phone, msg) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00)),
                                    modifier = Modifier.weight(0.75f)
                                ) {
                                    Text("SMS", fontSize = 10.sp, color = Color.White)
                                }
                                OutlinedButton(onClick = { copyToClipboard(context, msg) }, modifier = Modifier.weight(1f)) {
                                    Text("Copy", fontSize = 10.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth().height(0.8.dp).background(Color(0xFFFFE082)))
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Button(
                                    onClick = { onEdit(tenant) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Edit", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { exportTenantPDF(context, tenant, tenantPayments) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Send, contentDescription = "PDF", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("PDF", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = { exportTenantCSV(context, tenant, tenantPayments) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = "Excel", modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text("Excel", fontSize = 10.sp)
                                }
                                OutlinedButton(
                                    onClick = { onDelete(tenant) },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                                    modifier = Modifier.weight(0.65f)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFD32F2F), modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 10. PAYMENT TAB
// ============================================================

@Composable
fun PaymentTab(payments: List<PaymentEntity>) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }

    val filteredPayments = payments.filter {
        it.tenantName.contains(searchQuery, ignoreCase = true) ||
                it.mode.contains(searchQuery, ignoreCase = true) ||
                it.rentMonth.contains(searchQuery, ignoreCase = true)
    }

    val total = filteredPayments.sumOf { it.amount }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Payment History (${payments.size})", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF3E2723))
                Text("Showing: ₹${total.toInt()}", fontSize = 12.sp, color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { exportAllPaymentsCSV(context, payments) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00))
            ) {
                Text("All CSV", fontSize = 10.sp, color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("Search Payment...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color(0xFFE65100)) },
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDE7))
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(pay.tenantName, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF3E2723))
                                Text("Rent Month: ${formatRentMonth(pay.rentMonth)}", fontSize = 12.sp, color = Color.DarkGray)
                                Text("Date: ${pay.date}", fontSize = 12.sp, color = Color.Gray)
                                Text("Mode: ${pay.mode}", fontSize = 12.sp, color = Color.Gray)
                                if (pay.note.isNotBlank()) {
                                    Text("Note: ${pay.note}", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            Text("+ ₹${pay.amount.toInt()}", fontWeight = FontWeight.Bold, color = Color(0xFF2E7D32), fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 11. WHATSAPP & COMMUNICATIONS
// ============================================================

fun sendWhatsApp(context: Context, phone: String, message: String) {
    try {
        val cleanPhone = phone.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")
        val internationalPhone = when {
            cleanPhone.startsWith("+91") -> cleanPhone.substring(1)
            cleanPhone.startsWith("91") && cleanPhone.length == 12 -> cleanPhone
            cleanPhone.length == 10 -> "91$cleanPhone"
            else -> cleanPhone
        }

        val uri = Uri.parse("https://api.whatsapp.com/send?phone=$internationalPhone&text=${Uri.encode(message)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open WhatsApp", Toast.LENGTH_SHORT).show()
    }
}

fun sendSMS(context: Context, phone: String, message: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$phone")).apply {
            putExtra("sms_body", message)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Unable to open SMS", Toast.LENGTH_SHORT).show()
    }
}

fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Rent Reminder", text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "Reminder copied!", Toast.LENGTH_SHORT).show()
}

fun buildRentReminderMessage(tenant: TenantEntity, balance: Double): String {
    return buildString {
        append("Hello ${tenant.name},\n\n")
        append("Rent reminder for ${tenant.propertyName}:\n")
        append("• Monthly Rent: ₹${tenant.monthlyRent.toInt()}\n")
        if (tenant.previousDues > 0) {
            append("• Previous Pending Rent: ₹${tenant.previousDues.toInt()}\n")
        }
        append("• Total Amount Due: ₹${balance.toInt()}\n")
        append("• Due Date: ${ordinalDay(tenant.dueDayOfMonth)} of every month\n\n")
        append("Please make the payment at the earliest.\n\n")
        append("Thank you.\n")
        append("Rent Manager - Tript Digitals\n")
        append("Dev: Sparash Ram Sharma")
    }
}

// ============================================================
// 12. BACKUP & RESTORE
// ============================================================

suspend fun exportBackupJson(context: Context, dao: AppDao) {
    try {
        val props = dao.getPropertyList()
        val tenants = dao.getTenantList()
        val payments = dao.getPaymentList()

        val rootObj = JSONObject()
        rootObj.put("backupVersion", 4)
        rootObj.put("appVersion", "1.3.6")
        rootObj.put("company", "Tript Digitals")
        rootObj.put("developer", "Sparash Ram Sharma")
        rootObj.put("backupDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))

        val propsArr = JSONArray()
        props.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.id)
            obj.put("name", p.name)
            obj.put("type", p.type)
            obj.put("rentAmount", p.rentAmount)
            obj.put("isOccupied", p.isOccupied)
            propsArr.put(obj)
        }
        rootObj.put("properties", propsArr)

        val tenantArr = JSONArray()
        tenants.forEach { t ->
            val obj = JSONObject()
            obj.put("id", t.id)
            obj.put("name", t.name)
            obj.put("phone", t.phone)
            obj.put("propertyName", t.propertyName)
            obj.put("monthlyRent", t.monthlyRent)
            obj.put("previousDues", t.previousDues)
            obj.put("securityDeposit", t.securityDeposit)
            obj.put("dueDayOfMonth", t.dueDayOfMonth)
            obj.put("agreementStart", t.agreementStart)
            obj.put("agreementEnd", t.agreementEnd)
            obj.put("idProofNote", t.idProofNote)
            obj.put("tenantPhotoUri", t.tenantPhotoUri)
            obj.put("idProofPhotoUris", t.idProofPhotoUris)
            tenantArr.put(obj)
        }
        rootObj.put("tenants", tenantArr)

        val payArr = JSONArray()
        payments.forEach { pay ->
            val obj = JSONObject()
            obj.put("id", pay.id)
            obj.put("tenantName", pay.tenantName)
            obj.put("amount", pay.amount)
            obj.put("date", pay.date)
            obj.put("mode", pay.mode)
            obj.put("rentMonth", pay.rentMonth)
            obj.put("note", pay.note)
            payArr.put(obj)
        }
        rootObj.put("payments", payArr)

        val fileName = "RentManager_Backup_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.json"
        val file = File(context.cacheDir, fileName)
        file.writeText(rootObj.toString(2))

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Save Rent Manager Backup"))
    } catch (e: Exception) {
        Toast.makeText(context, "Backup Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

suspend fun restoreFromBackupJson(context: Context, dao: AppDao, uri: Uri) {
    try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val jsonStr = inputStream?.bufferedReader()?.use { it.readText() } ?: ""

        if (jsonStr.isBlank()) {
            Toast.makeText(context, "Backup file is empty", Toast.LENGTH_LONG).show()
            return
        }

        val rootObj = JSONObject(jsonStr)

        dao.deleteAllPayments()
        dao.deleteAllTenants()
        dao.deleteAllProperties()

        if (rootObj.has("properties")) {
            val propsArr = rootObj.getJSONArray("properties")
            for (i in 0 until propsArr.length()) {
                val obj = propsArr.getJSONObject(i)
                dao.insertProperty(
                    PropertyEntity(
                        id = obj.optInt("id", 0),
                        name = obj.optString("name", ""),
                        type = obj.optString("type", "Shop"),
                        rentAmount = obj.optDouble("rentAmount", 0.0),
                        isOccupied = obj.optBoolean("isOccupied", false)
                    )
                )
            }
        }

        if (rootObj.has("tenants")) {
            val tenantArr = rootObj.getJSONArray("tenants")
            for (i in 0 until tenantArr.length()) {
                val obj = tenantArr.getJSONObject(i)
                dao.insertTenant(
                    TenantEntity(
                        id = obj.optInt("id", 0),
                        name = obj.optString("name", ""),
                        phone = obj.optString("phone", ""),
                        propertyName = obj.optString("propertyName", ""),
                        monthlyRent = obj.optDouble("monthlyRent", 0.0),
                        previousDues = obj.optDouble("previousDues", 0.0),
                        securityDeposit = obj.optDouble("securityDeposit", 0.0),
                        dueDayOfMonth = obj.optInt("dueDayOfMonth", 5),
                        agreementStart = obj.optString("agreementStart", ""),
                        agreementEnd = obj.optString("agreementEnd", ""),
                        idProofNote = obj.optString("idProofNote", ""),
                        tenantPhotoUri = obj.optString("tenantPhotoUri", ""),
                        idProofPhotoUris = obj.optString("idProofPhotoUris", "")
                    )
                )
            }
        }

        if (rootObj.has("payments")) {
            val payArr = rootObj.getJSONArray("payments")
            for (i in 0 until payArr.length()) {
                val obj = payArr.getJSONObject(i)
                dao.insertPayment(
                    PaymentEntity(
                        id = obj.optInt("id", 0),
                        tenantName = obj.optString("tenantName", ""),
                        amount = obj.optDouble("amount", 0.0),
                        date = obj.optString("date", ""),
                        mode = obj.optString("mode", "UPI"),
                        rentMonth = obj.optString("rentMonth", ""),
                        note = obj.optString("note", "")
                    )
                )
            }
        }

        Toast.makeText(context, "Database restored successfully!", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Restore Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ============================================================
// 13. PDF & CSV EXPORTS
// ============================================================

fun exportTenantPDF(context: Context, tenant: TenantEntity, tenantPayments: List<PaymentEntity>) {
    try {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        canvas.drawText("Monthly Rent: Rs ${tenant.monthlyRent.toInt()}", 40f, y, paint)
        y += 20f
        canvas.drawText("Previous Pending Dues: Rs ${tenant.previousDues.toInt()}", 40f, y, paint)
        y += 20f
        canvas.drawText("Security Deposit: Rs ${tenant.securityDeposit.toInt()}", 40f, y, paint)
        y += 20f
        canvas.drawText("Rent Due Day: ${ordinalDay(tenant.dueDayOfMonth)} of every month", 40f, y, paint)
        y += 20f
        canvas.drawText("Agreement: ${tenant.agreementStart} to ${tenant.agreementEnd}", 40f, y, paint)

        y += 30f
        val totalPaid = tenantPayments.sumOf { it.amount }
        paint.isFakeBoldText = true
        canvas.drawText("Total Historical Paid: Rs ${totalPaid.toInt()}", 40f, y, paint)

        y += 25f
        paint.color = android.graphics.Color.GRAY
        canvas.drawLine(40f, y, 555f, y, paint)

        y += 30f
        paint.color = android.graphics.Color.BLACK
        paint.isFakeBoldText = true
        paint.textSize = 14f
        canvas.drawText("Payment History:", 40f, y, paint)

        y += 25f
        paint.textSize = 10f
        paint.isFakeBoldText = false

        if (tenantPayments.isEmpty()) {
            canvas.drawText("No payment transactions recorded.", 40f, y, paint)
        } else {
            for (pay in tenantPayments) {
                val line = "${pay.date} | ${formatRentMonth(pay.rentMonth)} | Rs ${pay.amount.toInt()} | ${pay.mode}"
                canvas.drawText(line.take(90), 40f, y, paint)
                y += 18f
                if (y > 780f) break
            }
        }

        pdfDocument.finishPage(page)

        val safeName = tenant.name.replace(Regex("[^A-Za-z0-9_ -]"), "").replace(" ", "_")
        val fileName = "${safeName}_Statement.pdf"
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
        Toast.makeText(context, "PDF Export Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun csvEscape(value: String): String {
    return "\"${value.replace("\"", "\"\"")}\""
}

fun exportTenantCSV(context: Context, tenant: TenantEntity, tenantPayments: List<PaymentEntity>) {
    try {
        val totalPaid = tenantPayments.sumOf { it.amount }
        val builder = StringBuilder()

        builder.append("TENANT STATEMENT\n")
        builder.append("Tenant Name,${csvEscape(tenant.name)}\n")
        builder.append("Phone,${csvEscape(tenant.phone)}\n")
        builder.append("Shop/Property,${csvEscape(tenant.propertyName)}\n")
        builder.append("Monthly Rent,${tenant.monthlyRent}\n")
        builder.append("Previous Pending Rent,${tenant.previousDues}\n")
        builder.append("Security Deposit,${tenant.securityDeposit}\n")
        builder.append("Due Day,${ordinalDay(tenant.dueDayOfMonth)}\n")
        builder.append("Agreement Period,${csvEscape("${tenant.agreementStart} to ${tenant.agreementEnd}")}\n")
        builder.append("Historical Rent Paid,$totalPaid\n\n")

        builder.append("PAYMENT HISTORY\n")
        builder.append("Date,Rent Month,Amount Paid,Payment Mode,Note\n")
        for (p in tenantPayments) {
            builder.append(
                "${csvEscape(p.date)},${csvEscape(formatRentMonth(p.rentMonth))},${p.amount},${csvEscape(p.mode)},${csvEscape(p.note)}\n"
            )
        }

        val safeName = tenant.name.replace(Regex("[^A-Za-z0-9_ -]"), "").replace(" ", "_")
        val file = File(context.cacheDir, "${safeName}_Statement.csv")
        file.writeText(builder.toString())

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Tenant Excel/CSV Report"))
    } catch (e: Exception) {
        Toast.makeText(context, "CSV Export Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun exportAllPaymentsCSV(context: Context, payments: List<PaymentEntity>) {
    try {
        val builder = StringBuilder()
        builder.append("Tenant Name,Rent Month,Amount Paid,Date,Payment Mode,Note\n")

        payments.forEach {
            builder.append(
                "${csvEscape(it.tenantName)},${csvEscape(formatRentMonth(it.rentMonth))},${it.amount},${csvEscape(it.date)},${csvEscape(it.mode)},${csvEscape(it.note)}\n"
            )
        }

        val file = File(context.cacheDir, "All_Payments_Report.csv")
        file.writeText(builder.toString())

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share All Payments CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "CSV Export Error: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ============================================================
// 14. ADD & EDIT DIALOGS (WITH GALLERY & CAMERA PICKERS)
// ============================================================

@Composable
fun AddPropertyDialog(onDismiss: () -> Unit, onAdd: (String, String, Double) -> Unit) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Shop") }
    var rent by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Shop/Property", color = Color(0xFF3E2723), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type (Shop/Flat)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rent, onValueChange = { rent = it }, label = { Text("Monthly Rent (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && rent.toDoubleOrNull() != null) {
                        onAdd(name, type, rent.toDouble())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
            ) { Text("Save", color = Color.White) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditPropertyDialog(property: PropertyEntity, onDismiss: () -> Unit, onUpdate: (PropertyEntity) -> Unit) {
    var name by remember { mutableStateOf(property.name) }
    var type by remember { mutableStateOf(property.type) }
    var rent by remember { mutableStateOf(property.rentAmount.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Shop Details", color = Color(0xFF3E2723), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Shop Name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = type, onValueChange = { type = it }, label = { Text("Type") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = rent, onValueChange = { rent = it }, label = { Text("Monthly Rent (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newRent = rent.toDoubleOrNull() ?: property.rentAmount
                    if (name.isNotBlank()) {
                        onUpdate(property.copy(name = name.trim(), type = type.trim(), rentAmount = newRent))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
            ) { Text("Update", color = Color.White) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddTenantDialog(
    vacantProperties: List<PropertyEntity>,
    onDismiss: () -> Unit,
    onAdd: (String, String, String, Double, Double, Double, Int, String, String, String, String, String) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedShop by remember { mutableStateOf(vacantProperties.firstOrNull()?.name ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var rent by remember { mutableStateOf(vacantProperties.firstOrNull()?.rentAmount?.toInt()?.toString() ?: "") }
    var prevDues by remember { mutableStateOf("") }
    var deposit by remember { mutableStateOf("") }
    var dueDay by remember { mutableStateOf("5") }
    var start by remember { mutableStateOf(SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())) }
    var end by remember { mutableStateOf("31 Dec 2026") }
    var idProof by remember { mutableStateOf("Aadhaar Card") }

    var tenantPhotoUri by remember { mutableStateOf("") }
    var idProofPhotoList by remember { mutableStateOf<List<String>>(emptyList()) }

    val photoGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { tenantPhotoUri = it.toString() }
    }
    val photoCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { saveBitmapToCache(context, it)?.let { uri -> tenantPhotoUri = uri.toString() } }
    }

    val idGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        val newList = idProofPhotoList.toMutableList()
        uris.forEach { uri -> if (newList.size < 3) newList.add(uri.toString()) }
        idProofPhotoList = newList
    }
    val idCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let {
            saveBitmapToCache(context, it)?.let { uri ->
                if (idProofPhotoList.size < 3) {
                    idProofPhotoList = idProofPhotoList + uri.toString()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tenant", color = Color(0xFF3E2723), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tenant Name") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()) }

                // TENANT PHOTO UPLOAD SECTION
                item {
                    Column {
                        Text("Tenant Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tenantPhotoUri.isNotBlank()) {
                                val bitmap = rememberBitmapFromUri(tenantPhotoUri)
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Tenant Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(50.dp).clip(CircleShape).border(1.5.dp, Color(0xFFE65100), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                            Button(
                                onClick = { photoGalleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Gallery", fontSize = 10.sp, color = Color.White) }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { photoCameraLauncher.launch() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Camera", fontSize = 10.sp, color = Color.White) }
                        }
                    }
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedShop,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Vacant Shop") },
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expanded = true }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
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
                item { OutlinedTextField(value = prevDues, onValueChange = { prevDues = it }, label = { Text("Previous Pending Rent (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = deposit, onValueChange = { deposit = it }, label = { Text("Security Deposit (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = dueDay, onValueChange = { dueDay = it }, label = { Text("Rent Due Day (1-31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Agreement Start Date") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Agreement End Date") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = idProof, onValueChange = { idProof = it }, label = { Text("ID Proof Note") }, modifier = Modifier.fillMaxWidth()) }

                // ID PROOF PHOTOS SECTION (MAX 3)
                item {
                    Column {
                        Text("ID Proof Photos (Max 3): ${idProofPhotoList.size}/3", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Button(
                                onClick = { idGalleryLauncher.launch("image/*") },
                                enabled = idProofPhotoList.size < 3,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00)),
                                modifier = Modifier.weight(1f)
                            ) { Text("+ Gallery", fontSize = 10.sp, color = Color.White) }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { idCameraLauncher.launch() },
                                enabled = idProofPhotoList.size < 3,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                modifier = Modifier.weight(1f)
                            ) { Text("+ Camera", fontSize = 10.sp, color = Color.White) }
                        }
                        if (idProofPhotoList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(idProofPhotoList) { uriStr ->
                                    val bitmap = rememberBitmapFromUri(uriStr)
                                    if (bitmap != null) {
                                        Box {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = "ID Thumbnail",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(6.dp))
                                            )
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.Red,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .background(Color.White, CircleShape)
                                                    .align(Alignment.TopEnd)
                                                    .clickable { idProofPhotoList = idProofPhotoList - uriStr }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedRent = rent.toDoubleOrNull()
                    val parsedDueDay = dueDay.toIntOrNull()
                    if (name.isNotBlank() && selectedShop.isNotBlank() && parsedRent != null) {
                        onAdd(
                            name, phone, selectedShop, parsedRent,
                            prevDues.toDoubleOrNull() ?: 0.0, deposit.toDoubleOrNull() ?: 0.0,
                            parsedDueDay?.coerceIn(1, 31) ?: 5, start, end, idProof,
                            tenantPhotoUri, idProofPhotoList.joinToString(",")
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
            ) { Text("Save Tenant", color = Color.White) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun EditTenantDialog(
    tenant: TenantEntity,
    allProperties: List<PropertyEntity>,
    onDismiss: () -> Unit,
    onUpdate: (TenantEntity) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(tenant.name) }
    var phone by remember { mutableStateOf(tenant.phone) }
    var shopName by remember { mutableStateOf(tenant.propertyName) }
    var rent by remember { mutableStateOf(tenant.monthlyRent.toInt().toString()) }
    var prevDues by remember { mutableStateOf(tenant.previousDues.toInt().toString()) }
    var deposit by remember { mutableStateOf(tenant.securityDeposit.toInt().toString()) }
    var dueDay by remember { mutableStateOf(tenant.dueDayOfMonth.toString()) }
    var start by remember { mutableStateOf(tenant.agreementStart) }
    var end by remember { mutableStateOf(tenant.agreementEnd) }
    var idProof by remember { mutableStateOf(tenant.idProofNote) }

    var tenantPhotoUri by remember { mutableStateOf(tenant.tenantPhotoUri) }
    var idProofPhotoList by remember { mutableStateOf(tenant.idProofPhotoUris.split(",").filter { it.isNotBlank() }) }

    val photoGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { tenantPhotoUri = it.toString() }
    }
    val photoCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let { saveBitmapToCache(context, it)?.let { uri -> tenantPhotoUri = uri.toString() } }
    }

    val idGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        val newList = idProofPhotoList.toMutableList()
        uris.forEach { uri -> if (newList.size < 3) newList.add(uri.toString()) }
        idProofPhotoList = newList
    }
    val idCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        bitmap?.let {
            saveBitmapToCache(context, it)?.let { uri ->
                if (idProofPhotoList.size < 3) {
                    idProofPhotoList = idProofPhotoList + uri.toString()
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tenant Details", color = Color(0xFF3E2723), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Tenant Name") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth()) }

                // TENANT PHOTO UPLOAD SECTION
                item {
                    Column {
                        Text("Tenant Photo", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (tenantPhotoUri.isNotBlank()) {
                                val bitmap = rememberBitmapFromUri(tenantPhotoUri)
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = "Tenant Preview",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(50.dp).clip(CircleShape).border(1.5.dp, Color(0xFFE65100), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                            }
                            Button(
                                onClick = { photoGalleryLauncher.launch("image/*") },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Gallery", fontSize = 10.sp, color = Color.White) }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { photoCameraLauncher.launch() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                modifier = Modifier.weight(1f)
                            ) { Text("Camera", fontSize = 10.sp, color = Color.White) }
                        }
                    }
                }

                item { OutlinedTextField(value = shopName, onValueChange = { shopName = it }, label = { Text("Assigned Shop") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = rent, onValueChange = { rent = it }, label = { Text("Current Monthly Rent (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = prevDues, onValueChange = { prevDues = it }, label = { Text("Previous Pending Rent (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = deposit, onValueChange = { deposit = it }, label = { Text("Security Deposit (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = dueDay, onValueChange = { dueDay = it }, label = { Text("Rent Due Day (1-31)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Agreement Start Date") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("Agreement End Date") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = idProof, onValueChange = { idProof = it }, label = { Text("ID Proof Note") }, modifier = Modifier.fillMaxWidth()) }

                // ID PROOF PHOTOS SECTION (MAX 3)
                item {
                    Column {
                        Text("ID Proof Photos (Max 3): ${idProofPhotoList.size}/3", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            Button(
                                onClick = { idGalleryLauncher.launch("image/*") },
                                enabled = idProofPhotoList.size < 3,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8F00)),
                                modifier = Modifier.weight(1f)
                            ) { Text("+ Gallery", fontSize = 10.sp, color = Color.White) }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { idCameraLauncher.launch() },
                                enabled = idProofPhotoList.size < 3,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                                modifier = Modifier.weight(1f)
                            ) { Text("+ Camera", fontSize = 10.sp, color = Color.White) }
                        }
                        if (idProofPhotoList.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(idProofPhotoList) { uriStr ->
                                    val bitmap = rememberBitmapFromUri(uriStr)
                                    if (bitmap != null) {
                                        Box {
                                            Image(
                                                bitmap = bitmap,
                                                contentDescription = "ID Thumbnail",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(6.dp))
                                            )
                                            Icon(
                                                Icons.Default.Close,
                                                contentDescription = "Remove",
                                                tint = Color.Red,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .background(Color.White, CircleShape)
                                                    .align(Alignment.TopEnd)
                                                    .clickable { idProofPhotoList = idProofPhotoList - uriStr }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onUpdate(
                            tenant.copy(
                                name = name.trim(),
                                phone = phone.trim(),
                                propertyName = shopName.trim(),
                                monthlyRent = rent.toDoubleOrNull() ?: tenant.monthlyRent,
                                previousDues = prevDues.toDoubleOrNull() ?: tenant.previousDues,
                                securityDeposit = deposit.toDoubleOrNull() ?: tenant.securityDeposit,
                                dueDayOfMonth = (dueDay.toIntOrNull() ?: tenant.dueDayOfMonth).coerceIn(1, 31),
                                agreementStart = start.trim(),
                                agreementEnd = end.trim(),
                                idProofNote = idProof.trim(),
                                tenantPhotoUri = tenantPhotoUri,
                                idProofPhotoUris = idProofPhotoList.joinToString(",")
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
            ) { Text("Update Tenant", color = Color.White) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun AddPaymentDialog(tenants: List<TenantEntity>, onDismiss: () -> Unit, onAdd: (String, Double, String, String, String, String) -> Unit) {
    val currentMonth = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
    val today = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())

    var selectedTenant by remember { mutableStateOf(tenants.firstOrNull()?.name ?: "") }
    var selectedTenantProperty by remember { mutableStateOf(tenants.firstOrNull()?.propertyName ?: "") }
    var expanded by remember { mutableStateOf(false) }
    var amount by remember { mutableStateOf(tenants.firstOrNull()?.monthlyRent?.toInt()?.toString() ?: "") }
    var mode by remember { mutableStateOf("UPI") }
    var rentMonth by remember { mutableStateOf(currentMonth) }
    var paymentDate by remember { mutableStateOf(today) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Record Rent Payment", color = Color(0xFF3E2723), fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = if (selectedTenant.isNotBlank()) "$selectedTenant ($selectedTenantProperty)" else "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select Tenant") },
                            trailingIcon = {
                                IconButton(onClick = { expanded = !expanded }) {
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { expanded = true }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            tenants.forEach { tenant ->
                                DropdownMenuItem(
                                    text = { Text("${tenant.name} (${tenant.propertyName})") },
                                    onClick = {
                                        selectedTenant = tenant.name
                                        selectedTenantProperty = tenant.propertyName
                                        amount = tenant.monthlyRent.toInt().toString()
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                item { OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount Paid (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = rentMonth, onValueChange = { rentMonth = it }, label = { Text("Rent Month (YYYY-MM)") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = paymentDate, onValueChange = { paymentDate = it }, label = { Text("Payment Date") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = mode, onValueChange = { mode = it }, label = { Text("Payment Mode") }, modifier = Modifier.fillMaxWidth()) }
                item { OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note / Reference (Optional)") }, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedAmount = amount.toDoubleOrNull()
                    if (selectedTenant.isNotBlank() && parsedAmount != null && parsedAmount > 0) {
                        onAdd(selectedTenant, parsedAmount, mode.trim().ifBlank { "UPI" }, rentMonth.trim(), paymentDate.trim(), note.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))
            ) { Text("Save Payment", color = Color.White) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ============================================================
// 15. HELPER UTILS & IMAGE LOADERS
// ============================================================

@Composable
fun rememberBitmapFromUri(uriString: String): ImageBitmap? {
    val context = LocalContext.current
    var imageBitmap by remember(uriString) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uriString) {
        if (uriString.isNotBlank()) {
            try {
                val uri = Uri.parse(uriString)
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                imageBitmap = bitmap?.asImageBitmap()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            imageBitmap = null
        }
    }
    return imageBitmap
}

fun saveBitmapToCache(context: Context, bitmap: Bitmap): Uri? {
    return try {
        val file = File(context.cacheDir, "img_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun ordinalDay(day: Int): String {
    val safeDay = day.coerceIn(1, 31)
    return when {
        safeDay % 100 in 11..13 -> "${safeDay}th"
        safeDay % 10 == 1 -> "${safeDay}st"
        safeDay % 10 == 2 -> "${safeDay}nd"
        safeDay % 10 == 3 -> "${safeDay}rd"
        else -> "${safeDay}th"
    }
}

fun formatRentMonth(month: String): String {
    return try {
        val date = SimpleDateFormat("yyyy-MM", Locale.getDefault()).parse(month)
        if (date != null) {
            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
        } else {
            month
        }
    } catch (e: Exception) {
        month
    }
}
