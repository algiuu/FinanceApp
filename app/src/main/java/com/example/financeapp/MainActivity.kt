package com.example.financeapp

import android.content.Context
import androidx.compose.ui.graphics.graphicsLayer
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.first
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.financeapp.data.*
import com.example.financeapp.ui.FinanceViewModel
import com.example.financeapp.ui.theme.FinanceAppTheme
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dao = (application as FinanceApp).database.financeDao()

        enableEdgeToEdge()
        setContent {
            FinanceAppTheme {
                val viewModel: FinanceViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return FinanceViewModel(application, dao) as T
                        }
                    }
                )

                MainAppScreen(viewModel)
            }
        }
    }
}

fun formatRp(amount: Double): String {
    val format = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    return format.format(amount).replace("Rp", "Rp ")
}

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Dashboard : Screen("dashboard", "Utama", Icons.Rounded.Home)
    object Wallets : Screen("wallets", "Dompet", Icons.Rounded.AccountBalanceWallet)
    object Transactions : Screen("transactions", "Riwayat", Icons.Rounded.ListAlt)
    object AI : Screen("ai", "AI", Icons.Rounded.AutoAwesome)
    object Goals : Screen("goals", "Target", Icons.Rounded.TrackChanges)
    object Categories : Screen("categories", "Kategori", Icons.Rounded.Tag)
}

enum class AiMode(val label: String) {
    CHAT("Consult"),
    AUTO_RECORD("Quick")
}

@Composable
fun MainAppScreen(viewModel: FinanceViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Dashboard) }
    var initialAiMode by remember { mutableStateOf(AiMode.AUTO_RECORD) }
    var showManualTransactionDialogFromHome by remember { mutableStateOf(false) }

    val wallets by viewModel.wallets.collectAsState()
    val kategoris by viewModel.kategoris.collectAsState()


    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier.clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.1f), RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                val screens = remember { listOf(Screen.Dashboard, Screen.Wallets, Screen.Transactions, Screen.AI, Screen.Goals, Screen.Categories) }
                screens.forEach { screen ->
                    val isSelected = currentScreen == screen

                    val animatedIconSize by animateDpAsState(
                        targetValue = if (isSelected) 28.dp else 22.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessHigh
                        ),
                        label = "iconFlash"
                    )

                    NavigationBarItem(
                        icon = {
                            Icon(
                                screen.icon,
                                contentDescription = screen.title,
                                modifier = Modifier.size(animatedIconSize)
                            )
                        },
                        label = {
                            Text(
                                screen.title,
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Visible,
                                softWrap = false
                            )
                        },
                        selected = isSelected,
                        alwaysShowLabel = true,
                        onClick = {
                            if (screen == Screen.AI) {
                                initialAiMode = AiMode.AUTO_RECORD
                            }
                            currentScreen = screen
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = if (screen == Screen.AI) Color(0xFF818CF8) else MaterialTheme.colorScheme.primary,
                            selectedTextColor = if (screen == Screen.AI) Color(0xFF818CF8) else MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (currentScreen) {
                Screen.Dashboard -> DashboardScreen(
                    viewModel = viewModel,
                    onNavigateToAiQuick = {
                        initialAiMode = AiMode.AUTO_RECORD
                        currentScreen = Screen.AI
                    },
                    onTriggerAddTransaction = {
                        showManualTransactionDialogFromHome = true
                    }
                )
                Screen.Wallets -> WalletsScreen(viewModel)
                Screen.Transactions -> TransactionsScreen(viewModel)
                Screen.AI -> AiScreen(viewModel, initialAiMode, onModeChanged = { initialAiMode = it })
                Screen.Goals -> GoalsScreen(viewModel)
                Screen.Categories -> CategoriesScreen(viewModel)
            }

            if (showManualTransactionDialogFromHome) {
                AddTransactionDialog(
                    wallets = wallets,
                    categories = kategoris,
                    onDismiss = { showManualTransactionDialogFromHome = false },
                    onConfirm = { title, amount, type, walletId, catId ->
                        viewModel.recordManualTransaction(title, amount, type, walletId, catId)
                        showManualTransactionDialogFromHome = false
                    }
                )
            }
        }
    }
}

fun saveImageToInternalStorage(context: Context, uri: Uri): String {
    return try {
        val inputStream = context.contentResolver.openInputStream(uri)
        val file = File(context.filesDir, "user_profile_avatar.jpg")
        val outputStream = FileOutputStream(file)

        inputStream?.use { input ->
            outputStream.use { output ->
                input.copyTo(output)
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        ""
    }
}

@Composable
fun DashboardScreen(
    viewModel: FinanceViewModel,
    onNavigateToAiQuick: () -> Unit,
    onTriggerAddTransaction: () -> Unit
) {
    val wallets by viewModel.wallets.collectAsState()
    val kategoris by viewModel.kategoris.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userProfileUri by viewModel.userProfileUri.collectAsState()

    val totalBalance by viewModel.totalBalance.collectAsState()
    val totalIncome by viewModel.totalIncome.collectAsState()
    val totalExpense by viewModel.totalExpense.collectAsState()
    val netMonthly by viewModel.netMonthly.collectAsState()

    val budgetKategoris = remember(kategoris) { kategoris.filter { it.budget > 0 } }
    var showProfileDialog by remember { mutableStateOf(false) }

    val isDark = isSystemInDarkTheme()
    val primaryGradient = remember(isDark) {
        Brush.verticalGradient(
            colors = if (isDark) listOf(Color(0xFF10B981), Color(0xFF065F46)) else listOf(Color(0xFF10B981), Color(0xFFD1FAE5))
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Hello,", fontSize = 14.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                    Text(
                        text = "$userName ✨",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.5).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { showProfileDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    if (userProfileUri.isNotEmpty()) {
                        AsyncImage(
                            model = Uri.parse(userProfileUri),
                            contentDescription = "Foto Profil",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = userName.take(2).uppercase(),
                            fontWeight = FontWeight.Black,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onNavigateToAiQuick,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = Color.White),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Icon(Icons.Rounded.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Catat Kilat AI", fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                }

                OutlinedButton(
                    onClick = onTriggerAddTransaction,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Transaksi Baru", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(32.dp)
                    },
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize().background(primaryGradient).padding(24.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(
                            color = Color.White.copy(alpha = 0.05f),
                            radius = size.width * 0.4f,
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.9f, size.height * 0.1f)
                        )
                    }

                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("TOTAL BALANCE", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Black, letterSpacing = 1.5.sp)
                            Text(formatRp(totalBalance), fontSize = 25.sp, fontWeight = FontWeight.Black, color = Color.White, style = TextStyle(shadow = Shadow(color = Color.Black.copy(alpha = 0.2f), blurRadius = 8f)))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Surplus", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                                Text(formatRp(totalIncome).replace("Rp ", ""), fontSize = 12.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
                                Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.2f)))
                                Text(if (netMonthly >= 0) "Sisa" else "Minus", fontSize = 8.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                                Text(formatRp(netMonthly).replace("Rp ", ""), fontSize = 10.sp, color = if (netMonthly >= 0) Color(0xFF4ADE80) else Color(0xFFF87171), fontWeight = FontWeight.Black, maxLines = 1)
                                Box(modifier = Modifier.width(1.dp).height(12.dp).background(Color.White.copy(alpha = 0.2f)))
                            }

                            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                Text("Defisit", fontSize = 9.sp, color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                                Text(formatRp(totalExpense).replace("Rp ", ""), fontSize = 12.sp, color = Color(0xFFF87171), fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Budget Watch", Icons.Rounded.Analytics)
        }

        items(
            items = budgetKategoris.take(3),
            key = { "budget_${it.id}" }
        ) { cat ->
            val ratio = remember(cat.spent, cat.budget) { (cat.spent / cat.budget).toFloat().coerceIn(0f, 1f) }
            val color = if (ratio > 0.9f) Color(0xFFF87171) else MaterialTheme.colorScheme.primary
            Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(cat.emoji, fontSize = 24.sp)
                            Text(cat.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text("${(ratio * 100).toInt()}%", fontWeight = FontWeight.Black, fontSize = 12.sp, color = color)
                    }
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { ratio }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape), color = color, trackColor = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }

        item {
            SectionHeader("My Assets", Icons.Rounded.AccountBalanceWallet)
        }

        items(
            items = wallets,
            key = { "wallet_home_${it.id}" }
        ) { wallet ->
            WalletListCard(wallet)
        }
    }

    if (showProfileDialog) {
        UserProfileDialog(viewModel = viewModel, onDismiss = { showProfileDialog = false })
    }
}

@Composable
fun UserProfileDialog(
    viewModel: FinanceViewModel,
    onDismiss: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val apiKey by viewModel.userApiKey.collectAsState()
    val userName by viewModel.userName.collectAsState()
    val userProfileUri by viewModel.userProfileUri.collectAsState()

    var textInputName by remember { mutableStateOf(userName) }
    var textInputKey by remember { mutableStateOf(apiKey) }
    var selectedPhotoUriStr by remember { mutableStateOf(userProfileUri) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val permanentPath = saveImageToInternalStorage(context, it)
            if (permanentPath.isNotEmpty()) {
                selectedPhotoUriStr = permanentPath
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    viewModel.saveUserProfile(context, textInputName.trim(), textInputKey.trim(), selectedPhotoUriStr)
                    android.widget.Toast.makeText(context, "Profil Berhasil Diperbarui! 🚀", android.widget.Toast.LENGTH_SHORT).show()
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Simpan Perubahan", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        .clickable { photoPickerLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedPhotoUriStr.isNotEmpty()) {
                        AsyncImage(
                            model = Uri.parse(selectedPhotoUriStr),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(Icons.Rounded.AddAPhoto, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Ketuk Lingkaran untuk Ubah Foto 📸", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Pengaturan Profil", fontSize = 20.sp, fontWeight = FontWeight.Black)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                OutlinedTextField(
                    value = textInputName,
                    onValueChange = { textInputName = it },
                    label = { Text("Nama Panggilan") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = "", tint = MaterialTheme.colorScheme.primary) }
                )

                OutlinedTextField(
                    value = textInputKey,
                    onValueChange = { textInputKey = it },
                    label = { Text("Gemini API Key") },
                    placeholder = { Text("AIzaSy...") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = { Icon(Icons.Rounded.VpnKey, contentDescription = "", tint = MaterialTheme.colorScheme.primary) }
                )
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun StatItem(icon: ImageVector, label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier.size(32.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, "", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        Column {
            Text(label, fontSize = 9.sp, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
            Text(value, fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Text(
            title.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
    }
}

@Composable
fun WalletListCard(wallet: Wallet) {
    val color = remember(wallet.colorHex) { parseColorSafe(wallet.colorHex) } // ✅ cache!

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(color.copy(alpha = 0.15f)), // ✅ pakai variabel
                contentAlignment = Alignment.Center
            ) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color)) // ✅
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(wallet.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text("Active Balance", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(formatRp(wallet.balance), fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
    }
}

fun parseColorSafe(colorHex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(colorHex))
    } catch (e: Exception) {
        Color.Gray
    }
}

@Composable
fun WalletsScreen(viewModel: FinanceViewModel) {
    val wallets by viewModel.wallets.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var walletToEdit by remember { mutableStateOf<Wallet?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Wallets", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            Button(
                onClick = { showAddDialog = true },
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Rounded.Add, "", modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("New", fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(
                items = wallets,
                key = { it.id }
            ) { wallet ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { walletToEdit = wallet },
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(parseColorSafe(wallet.colorHex).copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.AccountBalanceWallet, "", tint = parseColorSafe(wallet.colorHex), modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(wallet.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("Current Assets", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatRp(wallet.balance), fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface)
                            IconButton(onClick = { viewModel.deleteWallet(wallet) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, "", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddWalletDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, balance, color ->
                viewModel.insertWallet(name, balance, color)
                showAddDialog = false
            }
        )
    }

    walletToEdit?.let { wallet ->
        AddWalletDialog(
            initialWallet = wallet,
            onDismiss = { walletToEdit = null },
            onConfirm = { name, balance, color ->
                viewModel.updateWallet(wallet.copy(name = name, balance = balance, colorHex = color))
                walletToEdit = null
            }
        )
    }
}

@Composable
fun AddWalletDialog(
    initialWallet: Wallet? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String) -> Unit
) {
    var name by remember { mutableStateOf(initialWallet?.name ?: "") }
    var balance by remember { mutableStateOf(initialWallet?.balance?.toString() ?: "") }
    var color by remember { mutableStateOf(initialWallet?.colorHex ?: "#10B981") }

    val colorPresets = listOf(
        "#10B981", "#6366F1", "#F59E0B", "#EF4444",
        "#3B82F6", "#8B5CF6", "#EC4899", "#06B6D4",
        "#84CC16", "#71717A"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(24.dp),
        confirmButton = {
            Button(
                onClick = {
                    val balanceDouble = balance.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) onConfirm(name, balanceDouble, color)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Simpan Dompet", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    if (initialWallet == null) "Buka Dompet Baru" else "Edit Dompet",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
                ModernTextField(value = name, onValueChange = { name = it }, label = "Nama Dompet", icon = Icons.Rounded.Edit)
                ModernTextField(value = balance, onValueChange = { balance = it }, label = "Saldo Awal", icon = Icons.Rounded.Payments, isAmount = true)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Pilih Warna Tema", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        colorPresets.forEach { hex ->
                            val isSelected = color.lowercase() == hex.lowercase()
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color(android.graphics.Color.parseColor(hex)))
                                    .border(
                                        width = if (isSelected) 3.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { color = hex }
                                    .padding(4.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        null,
                                        tint = Color.White,
                                        modifier = Modifier.align(Alignment.Center).size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    isAmount: Boolean = false
) {
    val currencyTransformation = remember { CurrencyVisualTransformation() } // ✅ cache!

    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            if (isAmount) {
                if (newValue.all { it.isDigit() }) onValueChange(newValue)
            } else {
                onValueChange(newValue)
            }
        },
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (isAmount) KeyboardType.Number else KeyboardType.Text
        ),
        visualTransformation = if (isAmount) currencyTransformation else VisualTransformation.None // ✅
    )
}

class CurrencyVisualTransformation : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val formattedText = try {
            val number = originalText.toLong()
            val formatter = java.text.DecimalFormat("#,###")
            val symbols = formatter.decimalFormatSymbols
            symbols.groupingSeparator = '.'
            formatter.decimalFormatSymbols = symbols
            formatter.format(number)
        } catch (e: Exception) {
            originalText
        }

        val annotatedString = androidx.compose.ui.text.AnnotatedString(formattedText)

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val originalSub = originalText.substring(0, offset.coerceAtMost(originalText.length))
                val formattedSub = try {
                    val number = originalSub.toLong()
                    val formatter = java.text.DecimalFormat("#,###")
                    val symbols = formatter.decimalFormatSymbols
                    symbols.groupingSeparator = '.'
                    formatter.decimalFormatSymbols = symbols
                    formatter.format(number)
                } catch (e: Exception) {
                    originalSub
                }
                return formattedSub.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                val originalLength = originalText.length
                val transformedLength = formattedText.length
                if (offset <= 0) return 0
                if (offset >= transformedLength) return originalLength

                var originalOffset = 0
                var transformedOffset = 0
                while (originalOffset < originalLength && transformedOffset < offset) {
                    if (formattedText[transformedOffset] != '.') {
                        originalOffset++
                    }
                    transformedOffset++
                }
                return originalOffset
            }
        }

        return TransformedText(annotatedString, offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(viewModel: FinanceViewModel) {
    val activities by viewModel.activities.collectAsState()
    val kategoris by viewModel.kategoris.collectAsState()
    val wallets by viewModel.wallets.collectAsState()

    val categoryMap = remember(kategoris) { kategoris.associateBy { it.id } }
    val walletMap = remember(wallets) { wallets.associateBy { it.id } }

    var showAddDialog by remember { mutableStateOf(false) }
    var activityToEdit by remember { mutableStateOf<Activity?>(null) }

    var showDateRangePicker by remember { mutableStateOf(false) }
    var dateRangeState = rememberDateRangePickerState()

    val filteredActivities = remember(activities, dateRangeState.selectedStartDateMillis, dateRangeState.selectedEndDateMillis) {
        val start = dateRangeState.selectedStartDateMillis
        val end = dateRangeState.selectedEndDateMillis

        if (start != null && end != null) {
            val startDate = Instant.ofEpochMilli(start).atZone(ZoneId.systemDefault()).toLocalDate()
            val endDate = Instant.ofEpochMilli(end).atZone(ZoneId.systemDefault()).toLocalDate()

            activities.filter {
                val actDate = try { LocalDate.parse(it.date) } catch(e: Exception) { null }
                actDate != null && (actDate.isAfter(startDate) || actDate.isEqual(startDate)) &&
                        (actDate.isBefore(endDate) || actDate.isEqual(endDate))
            }
        } else {
            activities
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(18.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Add Transaction", modifier = Modifier.size(28.dp))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "History",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = (-0.5).sp
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (dateRangeState.selectedStartDateMillis != null) {
                        IconButton(
                            onClick = { dateRangeState.setSelection(null, null) },
                            modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f), CircleShape)
                        ) {
                            Icon(Icons.Rounded.FilterListOff, "Clear Filter", tint = MaterialTheme.colorScheme.error)
                        }
                    }

                    IconButton(
                        onClick = { showDateRangePicker = true },
                        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Rounded.DateRange, "Filter Date", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (dateRangeState.selectedStartDateMillis != null && dateRangeState.selectedEndDateMillis != null) {
                val start = Instant.ofEpochMilli(dateRangeState.selectedStartDateMillis!!).atZone(ZoneId.systemDefault()).toLocalDate()
                val end = Instant.ofEpochMilli(dateRangeState.selectedEndDateMillis!!).atZone(ZoneId.systemDefault()).toLocalDate()
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        "Filtering: ${start.format(DateTimeFormatter.ofPattern("dd MMM"))} - ${end.format(DateTimeFormatter.ofPattern("dd MMM"))}",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                items(
                    items = filteredActivities,
                    key = { it.id }
                ) { act ->
                    val cat = categoryMap[act.categoryId]
                    val wal = walletMap[act.walletId]
                    TransactionListCard(
                        activity = act,
                        category = cat,
                        wallet = wal,
                        onDelete = { viewModel.deleteActivity(act) },
                        onEdit = { activityToEdit = act }
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddTransactionDialog(
            wallets = wallets,
            categories = kategoris,
            onDismiss = { showAddDialog = false },
            onConfirm = { title, amount, type, walletId, catId ->
                viewModel.recordManualTransaction(title, amount, type, walletId, catId)
                showAddDialog = false
            }
        )
    }

    activityToEdit?.let { act ->
        AddTransactionDialog(
            initialActivity = act,
            wallets = wallets,
            categories = kategoris,
            onDismiss = { activityToEdit = null },
            onConfirm = { title, amount, type, walletId, catId ->
                viewModel.updateActivity(
                    oldActivity = act,
                    newActivity = act.copy(title = title, amount = amount, type = type, walletId = walletId, categoryId = catId)
                )
                activityToEdit = null
            }
        )
    }

    if (showDateRangePicker) {
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Pilih") }
            },
            dismissButton = {
                TextButton(onClick = {
                    dateRangeState.setSelection(null, null)
                    showDateRangePicker = false
                }) { Text("Batal") }
            }
        ) {
            DateRangePicker(
                state = dateRangeState,
                title = { Text("Pilih Rentang Tanggal", modifier = Modifier.padding(16.dp)) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun TransactionListCard(
    activity: Activity,
    category: Kategori?,
    wallet: Wallet?,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val isExpense = remember(activity.type) { activity.type == "expense" }
    val formattedAmount = remember(activity.amount, isExpense) {
        (if (isExpense) "-" else "+") + formatRp(activity.amount)
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(category?.emoji ?: "🏷️", fontSize = 22.sp)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    activity.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        activity.date,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Box(modifier = Modifier.size(3.dp).clip(CircleShape).background(MaterialTheme.colorScheme.outlineVariant))
                    Text(
                        wallet?.name ?: "Wallet",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formattedAmount,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isExpense) Color(0xFFF87171) else Color(0xFF4ADE80),
                    maxLines = 1
                )
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        "",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    initialActivity: Activity? = null,
    wallets: List<Wallet>,
    categories: List<Kategori>,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, Int, Int) -> Unit
) {
    var title by remember { mutableStateOf(initialActivity?.title ?: "") }
    var amount by remember { mutableStateOf(initialActivity?.amount?.toString() ?: "") }
    var type by remember { mutableStateOf(initialActivity?.type ?: "expense") }
    var walletId by remember { mutableStateOf(initialActivity?.walletId ?: wallets.firstOrNull()?.id ?: 0) }
    var categoryId by remember { mutableStateOf(initialActivity?.categoryId ?: categories.firstOrNull()?.id ?: 0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.padding(24.dp),
        confirmButton = {
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank() && amountDouble > 0) {
                        onConfirm(title, amountDouble, type, walletId, categoryId)
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Simpan Transaksi", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Text(
                if (initialActivity == null) "Catat Transaksi" else "Edit Transaksi",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(4.dp)
                ) {
                    listOf("expense" to "Pengeluaran", "income" to "Pemasukan").forEach { (valType, label) ->
                        val isSelected = type == valType
                        Box(
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) (if (valType == "expense") Color(0xFFF87171) else Color(0xFF4ADE80)) else Color.Transparent)
                                .clickable { type = valType }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ModernTextField(value = title, onValueChange = { title = it }, label = "Judul", icon = Icons.Rounded.Edit)
                ModernTextField(value = amount, onValueChange = { amount = it }, label = "Nominal", icon = Icons.Rounded.Payments, isAmount = true)

                Text("Pilih Sumber & Kategori", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        modifier = Modifier.weight(1f).height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        LazyColumn(modifier = Modifier.padding(4.dp)) {
                            items(wallets) { w ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(if (walletId == w.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                        .clickable { walletId = w.id }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(parseColorSafe(w.colorHex)))
                                    Spacer(Modifier.width(8.dp))
                                    Text(w.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f).height(100.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    ) {
                        LazyColumn(modifier = Modifier.padding(4.dp)) {
                            items(categories) { c ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .background(if (categoryId == c.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else Color.Transparent)
                                        .clickable { categoryId = c.id }.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(c.emoji, fontSize = 12.sp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(c.name, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun AiScreen(
    viewModel: FinanceViewModel,
    currentMode: AiMode,
    onModeChanged: (AiMode) -> Unit
) {
    var userPrompt by remember { mutableStateOf("") }
    val aiResponse by viewModel.aiResponse.collectAsState()
    val aiLoading by viewModel.aiLoading.collectAsState()

    val aiGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899)),
        start = androidx.compose.ui.geometry.Offset(0f, 0f),
        end = androidx.compose.ui.geometry.Offset(1000f, 1000f)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0xFF6366F1).copy(alpha = 0.08f), Color.Transparent)
                ),
                radius = size.width,
                center = androidx.compose.ui.geometry.Offset(size.width, 0f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "FinanzAI",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        style = TextStyle(brush = aiGradient),
                        letterSpacing = (-1).sp
                    )
                    Text(
                        "Cerdas. Cepat. Hemat.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(4.dp)) {
                        AiMode.entries.forEach { mode ->
                            val isSelected = currentMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                    .clickable {
                                        onModeChanged(mode)
                                        viewModel.clearAiResponse()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    mode.label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            if (currentMode == AiMode.CHAT) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val suggestions = listOf(
                        "Tips Menabung 💡" to "Beri saya tips menabung yang efektif",
                        "Review Pengeluaran 📊" to "Analisis pengeluaran saya bulan ini",
                        "Target Investasi 🎯" to "Berapa yang harus saya simpan untuk beli rumah?"
                    )
                    suggestions.forEach { (label, prompt) ->
                        Surface(
                            onClick = { userPrompt = prompt },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier.size(40.dp).background(Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Rounded.Bolt, "", tint = Color(0xFF6366F1))
                        }
                        Column {
                            Text("QUICK RECORD", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color(0xFF6366F1))
                            Text("Ketik: \"Gajian 5jt masuk ke Mandiri\"", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(24.dp)) {
                    if (aiLoading) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 4.dp,
                                color = Color(0xFF6366F1),
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                "Menganalisis data...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else if (aiResponse.isNotEmpty()) {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            val cleanedResponse = aiResponse
                                .replace(Regex("#+"), "")
                                .replace(Regex("\\*\\*"), "")
                                .replace(Regex("\\*"), "•")
                                .trim()

                            cleanedResponse.split("\n\n").forEach { paragraph ->
                                if (paragraph.isNotBlank()) {
                                    Card(
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(
                                            paragraph.trim(),
                                            fontSize = 15.sp,
                                            lineHeight = 24.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Icon(
                                if (currentMode == AiMode.CHAT) Icons.Rounded.AutoAwesome else Icons.Rounded.FlashOn,
                                "",
                                tint = MaterialTheme.colorScheme.outlineVariant,
                                modifier = Modifier.size(80.dp)
                            )
                            Text(
                                if (currentMode == AiMode.CHAT)
                                    "Tanyakan apa saja tentang keuanganmu.\nSaya akan bantu menganalisis!"
                                else
                                    "Catat banyak transaksi sekaligus\nhanya dengan satu kalimat.",
                                textAlign = TextAlign.Center,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = userPrompt,
                        onValueChange = { userPrompt = it },
                        placeholder = {
                            Text(
                                if (currentMode == AiMode.CHAT) "Tanya AI..." else "Catat cepat...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        maxLines = 4
                    )

                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(enabled = userPrompt.isNotEmpty() && !aiLoading) {
                                viewModel.onAiAction(userPrompt, currentMode == AiMode.CHAT)
                                userPrompt = ""
                            },
                        color = if (userPrompt.isNotEmpty()) Color(0xFF6366F1) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (currentMode == AiMode.CHAT) Icons.Rounded.Send else Icons.Rounded.AutoFixHigh,
                                "",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoriesScreen(viewModel: FinanceViewModel) {
    val kategoris by viewModel.kategoris.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var categoryToEdit by remember { mutableStateOf<Kategori?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Categories", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Rounded.Add, "", tint = MaterialTheme.colorScheme.primary)
            }
        }

        Text("AI automatically detects your custom categories ⚡", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)

        LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(
                items = kategoris,
                key = { it.id }
            ) { cat ->
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { categoryToEdit = cat },
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cat.emoji, fontSize = 24.sp)
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(cat.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            if (cat.budget > 0) {
                                Text("Limit: ${formatRp(cat.budget)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        IconButton(onClick = { viewModel.deleteKategori(cat) }) {
                            Icon(Icons.Rounded.DeleteOutline, "", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f), modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCategoryDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, budget, color, emoji ->
                viewModel.insertKategori(name, budget, color, emoji)
                showAddDialog = false
            }
        )
    }

    categoryToEdit?.let { cat ->
        AddCategoryDialog(
            initialCategory = cat,
            onDismiss = { categoryToEdit = null },
            onConfirm = { name, budget, color, emoji ->
                viewModel.updateKategori(cat.copy(name = name, budget = budget, colorHex = color, emoji = emoji))
                categoryToEdit = null
            }
        )
    }
}

@Composable
fun AddCategoryDialog(
    initialCategory: Kategori? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, String, String) -> Unit
) {
    var name by remember { mutableStateOf(initialCategory?.name ?: "") }
    var budget by remember { mutableStateOf(initialCategory?.budget?.toString() ?: "") }
    var emoji by remember { mutableStateOf(initialCategory?.emoji ?: "🏷️") }
    var color by remember { mutableStateOf(initialCategory?.colorHex ?: "#10B981") }

    val colorPresets = listOf(
        "#10B981", "#6366F1", "#F59E0B", "#EF4444",
        "#3B82F6", "#8B5CF6", "#EC4899", "#06B6D4",
        "#84CC16", "#71717A"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val budgetDouble = budget.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank()) onConfirm(name, budgetDouble, color, emoji)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Simpan Kategori", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Text(
                if (initialCategory == null) "Kategori Baru" else "Edit Kategori",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ModernTextField(value = name, onValueChange = { name = it }, label = "Nama Kategori", icon = Icons.Rounded.Tag)
                ModernTextField(value = budget, onValueChange = { budget = it }, label = "Limit Budget (Bulanan)", icon = Icons.Rounded.Analytics, isAmount = true)

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(0.4f)) {
                        ModernTextField(value = emoji, onValueChange = { emoji = it }, label = "Emoji", icon = Icons.Rounded.Mood)
                    }

                    Column(modifier = Modifier.weight(0.6f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Warna", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            colorPresets.forEach { hex ->
                                val isSelected = color.lowercase() == hex.lowercase()
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(Color(android.graphics.Color.parseColor(hex)))
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { color = hex },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )
}

@Composable
fun GoalsScreen(viewModel: FinanceViewModel) {
    val goals by viewModel.savingGoals.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var goalToEdit by remember { mutableStateOf<SavingGoal?>(null) }
    var goalToTopUp by remember { mutableStateOf<SavingGoal?>(null) }
    var goalToWithdraw by remember { mutableStateOf<SavingGoal?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Dream Goals 🎯", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
            IconButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(Icons.Rounded.Add, "", tint = MaterialTheme.colorScheme.primary)
            }
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(
                items = goals,
                key = { it.id }
            ) { goal ->
                val pct = remember(goal.saved, goal.target) {
                    if (goal.target > 0) (goal.saved / goal.target).toFloat().coerceIn(0f, 1f) else 0f
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { goalToEdit = goal },
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(goal.title, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                                val timeLeft = remember(goal.targetDate) {
                                    try {
                                        val targetDate = LocalDate.parse(goal.targetDate)
                                        val now = LocalDate.now()
                                        val days = java.time.temporal.ChronoUnit.DAYS.between(now, targetDate)
                                        if (days >= 0) {
                                            "$days hari lagi (${targetDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))})"
                                        } else {
                                            "Target lewat (${targetDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))})"
                                        }
                                    } catch (e: Exception) {
                                        "No Deadline"
                                    }
                                }
                                Text(timeLeft, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Box(
                                modifier = Modifier.size(42.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("${(pct * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(
                                progress = { pct },
                                modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(formatRp(goal.saved), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(formatRp(goal.target), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { goalToWithdraw = goal }) {
                                Icon(Icons.Rounded.Remove, "", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Kurangi", color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { goalToTopUp = goal }) {
                                Icon(Icons.Rounded.Add, "", modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Tambah")
                            }
                            IconButton(onClick = { viewModel.deleteSavingGoal(goal) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, "", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.4f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddGoalDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { title, target, saved, date ->
                viewModel.insertSavingGoal(title, target, saved, date)
                showAddDialog = false
            }
        )
    }

    goalToEdit?.let { goal ->
        AddGoalDialog(
            initialGoal = goal,
            onDismiss = { goalToEdit = null },
            onConfirm = { title, target, saved, date ->
                viewModel.updateSavingGoal(goal.copy(title = title, target = target, saved = saved, targetDate = date))
                goalToEdit = null
            }
        )
    }

    if (goalToWithdraw != null) {
        TopUpGoalDialog(
            goalName = goalToWithdraw!!.title,
            isWithdraw = true,
            onDismiss = { goalToWithdraw = null },
            onConfirm = { amount ->
                viewModel.addSavingGoalAmount(goalToWithdraw!!.id, -amount)
                goalToWithdraw = null
            }
        )
    }

    if (goalToTopUp != null) {
        TopUpGoalDialog(
            goalName = goalToTopUp!!.title,
            onDismiss = { goalToTopUp = null },
            onConfirm = { amount ->
                viewModel.addSavingGoalAmount(goalToTopUp!!.id, amount)
                goalToTopUp = null
            }
        )
    }
}

@Composable
fun TopUpGoalDialog(
    goalName: String,
    isWithdraw: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val amountDouble = amount.toDoubleOrNull() ?: 0.0
                    if (amountDouble > 0) onConfirm(amountDouble)
                },
                colors = if (isWithdraw) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
            ) {
                Text(if (isWithdraw) "Kurangi" else "Tambah")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } },
        title = { Text(if (isWithdraw) "Kurangi dari $goalName" else "Tambah ke $goalName") },
        text = {
            ModernTextField(value = amount, onValueChange = { amount = it }, label = "Jumlah", icon = if (isWithdraw) Icons.Rounded.RemoveCircle else Icons.Rounded.Payments, isAmount = true)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddGoalDialog(
    initialGoal: SavingGoal? = null,
    onDismiss: () -> Unit,
    onConfirm: (String, Double, Double, String) -> Unit
) {
    var title by remember { mutableStateOf(initialGoal?.title ?: "") }
    var target by remember { mutableStateOf(initialGoal?.target?.toString() ?: "") }
    var saved by remember { mutableStateOf(initialGoal?.saved?.toString() ?: "") }
    var targetDate by remember { mutableStateOf(initialGoal?.targetDate ?: "") }

    var showDatePicker by remember { mutableStateOf(false) }
    val dateState = rememberDatePickerState()

    val suggestionText = remember(target, saved, targetDate) {
        try {
            val targetNominal = target.toDoubleOrNull() ?: 0.0
            val savedNominal = saved.toDoubleOrNull() ?: 0.0
            val remaining = targetNominal - savedNominal

            if (remaining <= 0 || targetDate.isEmpty()) ""
            else {
                val tDate = LocalDate.parse(targetDate)
                val now = LocalDate.now()
                val days = java.time.temporal.ChronoUnit.DAYS.between(now, tDate)

                if (days <= 0) "Deadline sudah lewat!"
                else {
                    val daily = remaining / days
                    val weekly = remaining / (days / 7.0).coerceAtLeast(1.0)
                    val monthly = remaining / (days / 30.0).coerceAtLeast(1.0)

                    "Saran: Rp ${formatRp(daily).replace("Rp ", "")}/hari, " +
                            "Rp ${formatRp(weekly).replace("Rp ", "")}/minggu, " +
                            "atau Rp ${formatRp(monthly).replace("Rp ", "")}/bulan"
                }
            }
        } catch (e: Exception) { "" }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val targetDouble = target.toDoubleOrNull() ?: 0.0
                    val savedDouble = saved.toDoubleOrNull() ?: 0.0
                    if (title.isNotBlank()) onConfirm(title, targetDouble, savedDouble, targetDate)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Simpan Impian", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Batal", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        title = {
            Text(
                if (initialGoal == null) "Target Impian Baru" else "Edit Target",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ModernTextField(value = title, onValueChange = { title = it }, label = "Nama Barang/Impian", icon = Icons.Rounded.Stars)
                ModernTextField(value = target, onValueChange = { target = it }, label = "Target Nominal", icon = Icons.Rounded.Flag, isAmount = true)
                ModernTextField(value = saved, onValueChange = { saved = it }, label = "Sudah Terkumpul", icon = Icons.Rounded.AccountBalance, isAmount = true)

                Surface(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Event, null, tint = MaterialTheme.colorScheme.primary)
                        Text(
                            text = if (targetDate.isEmpty()) "Pilih Tanggal Target" else targetDate,
                            fontSize = 14.sp,
                            color = if (targetDate.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                if (suggestionText.isNotEmpty()) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Text(suggestionText, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        },
        shape = RoundedCornerShape(28.dp)
    )

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let {
                        targetDate = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Batal") }
            }
        ) {
            DatePicker(state = dateState)
        }
    }
}

@Composable
fun Modifier.noRippleClickable(onClick: () -> Unit): Modifier = this.then(
    Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )
)