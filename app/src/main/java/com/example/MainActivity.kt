package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppSettings
import com.example.data.ConsoleLog
import com.example.data.MemeCoinScannerService
import com.example.data.SignalLog
import com.example.ui.MemeCoinViewModel
import com.example.ui.theme.*
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(this, "Notification permission granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Notifications blocked. Bot state will not show in tray.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Ask for Notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBg),
                    contentWindowInsets = WindowInsets.systemBars
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(DarkBg)
                    ) {
                        MemeCoinControlDashboard()
                    }
                }
            }
        }
    }
}

@Composable
fun MemeCoinControlDashboard() {
    val context = LocalContext.current
    val viewModel: MemeCoinViewModel = viewModel()

    val settingsState by viewModel.settings.collectAsState()
    val recentLogs by viewModel.recentLogs.collectAsState()
    val allSignals by viewModel.allSignals.collectAsState()

    val isRunning by viewModel.isServiceRunning.collectAsState()
    val currentStatus by viewModel.currentServiceStatus.collectAsState()
    val cycleCount by viewModel.serviceCycleCount.collectAsState()

    val activeTab = remember { mutableStateOf("Signals") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // --- HEADER ---
        HeaderRow(isRunning)

        Spacer(modifier = Modifier.height(12.dp))

        // --- BOT CONTROLLER CARD ---
        ControllerPanelCard(
            isRunning = isRunning,
            currentStatus = currentStatus,
            cycleCount = cycleCount,
            signalCount = allSignals.size,
            onStart = { viewModel.startScanner(context) },
            onStop = { viewModel.stopScanner(context) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- TAB SELECTOR ---
        TabSelectorRow(
            activeTab = activeTab.value,
            signalCount = allSignals.size,
            onTabSelected = { activeTab.value = it }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // --- DYNAMIC CONTENT VIEW ---
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeTab.value) {
                "Signals" -> SignalsView(
                    signals = allSignals,
                    onClearHistory = { viewModel.clearSignals() }
                )
                "Terminal" -> TerminalView(
                    logs = recentLogs,
                    onClearLogs = { viewModel.clearLogs() },
                    onResetAlertCache = { viewModel.clearAlertCache() }
                )
                "Filters" -> FiltersView(
                    currentSettings = settingsState,
                    onSave = { updated -> viewModel.saveSettings(updated) }
                )
            }
        }
    }
}

@Composable
fun HeaderRow(isRunning: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Futuristic Dot Icon with Glowing Pulse
        val infiniteTransition = rememberInfiniteTransition(label = "GlPulse")
        val pulseScale by infiniteTransition.animateFloat(
            initialValue = 0.8f,
            targetValue = if (isRunning) 1.2f else 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
        val pulseAlpha by infiniteTransition.animateFloat(
            initialValue = 0.5f,
            targetValue = if (isRunning) 1.0f else 0.7f,
            animationSpec = infiniteRepeatable(
                animation = tween(1000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulseAlpha"
        )

        Box(
            modifier = Modifier
                .size(16.dp)
                .background(
                    color = if (isRunning) CyberGreen.copy(alpha = pulseAlpha) else Color.Gray.copy(alpha = 0.5f),
                    shape = CircleShape
                )
                .padding(3.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(if (isRunning) CyberGreen else Color.Gray, shape = CircleShape)
            )
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = "SOLANA SIGNAL BOT",
                color = TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.SansSerif
            )
            Text(
                text = "Multisource Solana Scanner • v4.0",
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Running pill status badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(if (isRunning) CyberGreenSubtle else Color(0xFF1E293B))
                .border(1.dp, if (isRunning) CyberGreen.copy(0.4f) else Color.DarkGray, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Text(
                text = if (isRunning) "ACTIVE" else "OFFLINE",
                color = if (isRunning) CyberGreen else TextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun ControllerPanelCard(
    isRunning: Boolean,
    currentStatus: String,
    cycleCount: Int,
    signalCount: Int,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isRunning) CyberGreen.copy(0.3f) else BorderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Real-time Tracker Engine",
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = currentStatus,
                        color = if (isRunning) CyberGreen else TextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Power Toggle Button
                Button(
                    onClick = { if (isRunning) onStop() else onStart() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRunning) WarningRed else CyberGreen
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier.height(42.dp)
                ) {
                    Icon(
                        imageVector = if (isRunning) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = "Power Trigger",
                        tint = if (isRunning) Color.White else SurfaceBg,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isRunning) "STOP" else "START",
                        fontWeight = FontWeight.Bold,
                        color = if (isRunning) Color.White else SurfaceBg,
                        fontSize = 13.sp
                    )
                }
            }

            Divider(color = BorderColor, modifier = Modifier.padding(vertical = 12.dp))

            // Stats row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Cycles",
                        tint = CyberBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cycles: $cycleCount",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Alerts Found",
                        tint = OrangeGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Signals Detected: $signalCount",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Active APIs",
                        tint = CyberGreen,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "7 API Feeds",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TabSelectorRow(
    activeTab: String,
    signalCount: Int,
    onTabSelected: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val tabs = listOf("Signals", "Terminal", "Filters")
        tabs.forEach { tab ->
            val isSelected = activeTab == tab
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabSelected(tab) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) CardBg else Color.Transparent
                ),
                shape = RoundedCornerShape(8.dp),
                border = if (isSelected) BorderStroke(1.dp, BorderColor) else null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) CyberGreen else TextSecondary,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        if (tab == "Signals" && signalCount > 0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Box(
                                modifier = Modifier
                                    .background(CyberGreen, CircleShape)
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = signalCount.toString(),
                                    color = SurfaceBg,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SignalsView(
    signals: List<SignalLog>,
    onClearHistory: () -> Unit
) {
    if (signals.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Empty Signals",
                    tint = TextSecondary.copy(0.4f),
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No Signals Discovered Yet",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Start the scanner. When a token meets your financial filters, it will automatically populate here and stream to Telegram.",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Historical Signals (${signals.size})",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Clear All",
                    color = WarningRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onClearHistory() }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(signals, key = { it.id }) { signal ->
                    SignalLogItemCard(signal)
                }
            }
        }
    }
}

@Composable
fun SignalLogItemCard(signal: SignalLog) {
    val context = LocalContext.current
    val decFmt = DecimalFormat("#,##0.00")
    val priceFmt = when {
        signal.price == 0.0 -> "$0"
        signal.price < 0.000001 -> String.format("%.10f", signal.price)
        signal.price < 0.001 -> String.format("%.8f", signal.price)
        signal.price < 1.0 -> String.format("%.6f", signal.price)
        else -> String.format("%.4f", signal.price)
    }

    val dateStr = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date(signal.timestamp))

    fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            // Title + Score Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "\$${signal.symbol}",
                        color = CyberGreen,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = signal.name,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when (signal.tier) {
                                    "ULTRA" -> WarningRed.copy(0.2f)
                                    "HIGH" -> OrangeGold.copy(0.2f)
                                    "MEDIUM" -> CyberGreenSubtle
                                    else -> Color.DarkGray.copy(0.3f)
                                }
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${signal.tier} ${signal.score}/10",
                            color = when (signal.tier) {
                                "ULTRA" -> WarningRed
                                "HIGH" -> OrangeGold
                                "MEDIUM" -> CyberGreen
                                else -> TextSecondary
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = dateStr,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Financial Indicators Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(text = "Price", color = TextSecondary, fontSize = 10.sp)
                    Text(text = "$$priceFmt", color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text(text = "Market Cap", color = TextSecondary, fontSize = 10.sp)
                    Text(text = formatUsdValue(signal.marketCap), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text(text = "Liquidity", color = TextSecondary, fontSize = 10.sp)
                    Text(text = formatUsdValue(signal.liquidity), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Column {
                    Text(text = "Age", color = TextSecondary, fontSize = 10.sp)
                    Text(text = formatAgeMinsValue(signal.ageMins), color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Price Change bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardBg)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Changes: ", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                PriceChangePill("5m", signal.change5m)
                PriceChangePill("1h", signal.change1h)
                PriceChangePill("6h", signal.change6h)
                PriceChangePill("24h", signal.change24h)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Rug and volumes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Buy Pressure (5m): ${signal.buyPct}% (B:${signal.buys5m} / S:${signal.sells5m})",
                    color = TextSecondary,
                    fontSize = 11.sp
                )

                // Rug score
                val rugStatus = if (signal.rugScore < 300) "SAFE" else if (signal.rugScore < 800) "WARN" else "DANGER"
                Text(
                    text = "Rug: ${signal.rugScore} [$rugStatus]",
                    color = if (signal.rugScore < 30) CyberGreen else if (signal.rugScore < 300) OrangeGold else WarningRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (signal.rugRisks.isNotEmpty() && signal.rugRisks != "None") {
                Text(
                    text = "Risks: ${signal.rugRisks}",
                    color = OrangeGold,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Text(
                text = "Sources: ${signal.sources}",
                color = CyberBlue,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Out Links Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { openUrl(signal.chartUrl) },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberGreen),
                    border = BorderStroke(1.dp, CyberGreen.copy(0.4f)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("DexScreener", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { openUrl("https://birdeye.so/token/${signal.mint}?chain=solana") },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberBlue),
                    border = BorderStroke(1.dp, CyberBlue.copy(0.4f)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("Birdeye", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = { openUrl("https://rugcheck.xyz/tokens/${signal.mint}") },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OrangeGold),
                    border = BorderStroke(1.dp, OrangeGold.copy(0.4f)),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text("RugCheck", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun PriceChangePill(label: String, value: Double) {
    val isPositive = value >= 0
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$label:", color = TextSecondary, fontSize = 10.sp)
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = "${if (isPositive) "^" else "v"}${Math.abs(value).toInt()}%",
            color = if (isPositive) CyberGreen else WarningRed,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun formatUsdValue(v: Double): String {
    return when {
         v >= 1000000.0 -> String.format(Locale.getDefault(), "$%.2fM", v / 1000000.0)
         v >= 1000.0 -> String.format(Locale.getDefault(), "$%.1fK", v / 1000.0)
         v == 0.0 -> "$0"
         else -> String.format(Locale.getDefault(), "$%.2f", v)
    }
}

fun formatAgeMinsValue(mins: Double): String {
    return when {
        mins < 60.0 -> "${mins.toInt()}m"
        mins < 1440.0 -> String.format(Locale.getDefault(), "%.1fh", mins / 60.0)
        else -> String.format(Locale.getDefault(), "%.1fd", mins / 1440.0)
    }
}

@Composable
fun TerminalView(
    logs: List<ConsoleLog>,
    onClearLogs: () -> Unit,
    onResetAlertCache: () -> Unit
) {
    val listState = rememberLazyListState()

    // Automatically scroll to top (latest log) when logs update
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Telemetric System Console",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Reset Alert Filter",
                    color = CyberBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onResetAlertCache() }
                )

                Text(
                    text = "Clear Term",
                    color = WarningRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onClearLogs() }
                )
            }
        }

        // Terminal Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black)
                .border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                .padding(10.dp)
        ) {
            if (logs.isEmpty()) {
                Text(
                    text = "Console is silent. Telemetry initialization sequence pending...",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        val timeStr = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(log.timestamp))
                        val logColor = when (log.level) {
                            "SIGNAL" -> CyberGreen
                            "WARN" -> OrangeGold
                            "ERROR" -> WarningRed
                            else -> TextSecondary
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "[$timeStr] ",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Text(
                                text = log.message,
                                color = logColor,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FiltersView(
    currentSettings: AppSettings,
    onSave: (AppSettings) -> Unit
) {
    var botToken by remember(currentSettings) { mutableStateOf(currentSettings.botToken) }
    var chatId by remember(currentSettings) { mutableStateOf(currentSettings.chatId) }
    var scanInterval by remember(currentSettings) { mutableStateOf(currentSettings.scanIntervalSeconds.toString()) }
    var minLiquidity by remember(currentSettings) { mutableStateOf(currentSettings.minLiquidity.toString()) }
    var minVolume1h by remember(currentSettings) { mutableStateOf(currentSettings.minVolume1h.toString()) }
    var minVolume5m by remember(currentSettings) { mutableStateOf(currentSettings.minVolume5m.toString()) }
    var minBuys5m by remember(currentSettings) { mutableStateOf(currentSettings.minBuys5m.toString()) }
    var maxMcap by remember(currentSettings) { mutableStateOf(currentSettings.maxMcap.toString()) }
    var minMcap by remember(currentSettings) { mutableStateOf(currentSettings.minMcap.toString()) }
    var maxAgeMinutes by remember(currentSettings) { mutableStateOf(currentSettings.maxAgeMinutes.toString()) }
    var minBuyPressure by remember(currentSettings) { mutableStateOf(currentSettings.minBuyPressure.toFloat()) }
    var minPriceChange5m by remember(currentSettings) { mutableStateOf(currentSettings.minPriceChange5m.toString()) }
    var minPriceChange1h by remember(currentSettings) { mutableStateOf(currentSettings.minPriceChange1h.toString()) }
    var maxRugScore by remember(currentSettings) { mutableStateOf(currentSettings.maxRugRiskScore.toFloat()) }
    var checkRug by remember(currentSettings) { mutableStateOf(currentSettings.checkRug) }

    val context = LocalContext.current

    fun validateAndSave() {
        val scanInt = scanInterval.toIntOrNull() ?: 60
        val minLiq = minLiquidity.toDoubleOrNull() ?: 8000.0
        val minVol1h = minVolume1h.toDoubleOrNull() ?: 5000.0
        val minVol5m = minVolume5m.toDoubleOrNull() ?: 300.0
        val minBuys = minBuys5m.toIntOrNull() ?: 4
        val maxMc = maxMcap.toDoubleOrNull() ?: 5000000.0
        val minMc = minMcap.toDoubleOrNull() ?: 5000.0
        val maxAge = maxAgeMinutes.toIntOrNull() ?: 240
        val minChg5m = minPriceChange5m.toDoubleOrNull() ?: 3.0
        val minChg1h = minPriceChange1h.toDoubleOrNull() ?: 8.0

        if (botToken.trim().isEmpty() || chatId.trim().isEmpty()) {
            Toast.makeText(context, "Telegram credentials are required!", Toast.LENGTH_SHORT).show()
            return
        }

        val updated = AppSettings(
            botToken = botToken.trim(),
            chatId = chatId.trim(),
            scanIntervalSeconds = scanInt,
            minLiquidity = minLiq,
            minVolume1h = minVol1h,
            minVolume5m = minVol5m,
            minBuys5m = minBuys,
            maxMcap = maxMc,
            minMcap = minMc,
            maxAgeMinutes = maxAge,
            minBuyPressure = minBuyPressure.toInt(),
            minPriceChange5m = minChg5m,
            minPriceChange1h = minChg1h,
            maxRugRiskScore = maxRugScore.toInt(),
            checkRug = checkRug
        )

        onSave(updated)
        Toast.makeText(context, "Meme Coin Filter Thresholds Saved", Toast.LENGTH_SHORT).show()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- SECTION 1: Telegram Credentials ---
        item {
            FilterGroupCard(title = "Telegram API Dispatcher") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter your custom Telegram channel credentials to route discovery reports.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    OutlinedTextField(
                        value = botToken,
                        onValueChange = { botToken = it },
                        label = { Text("BOT_TOKEN") },
                        colors = transparentTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = chatId,
                        onValueChange = { chatId = it },
                        label = { Text("CHAT_ID") },
                        colors = transparentTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }

        // --- SECTION 2: Timing & Scan Engine ---
        item {
            FilterGroupCard(title = "Scan Operations") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = scanInterval,
                        onValueChange = { scanInterval = it },
                        label = { Text("Scan Interval (sec)") },
                        colors = transparentTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = maxAgeMinutes,
                        onValueChange = { maxAgeMinutes = it },
                        label = { Text("Max Token Age (min)") },
                        colors = transparentTextFieldColors(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        }

        // --- SECTION 3: Capital and Volume Metrics ---
        item {
            FilterGroupCard(title = "Volume & Liquidity Limits") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = minLiquidity,
                            onValueChange = { minLiquidity = it },
                            label = { Text("Min Liquidity ($)") },
                            colors = transparentTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        OutlinedTextField(
                            value = minVolume1h,
                            onValueChange = { minVolume1h = it },
                            label = { Text("Min Vol 1H ($)") },
                            colors = transparentTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = minVolume5m,
                            onValueChange = { minVolume5m = it },
                            label = { Text("Min Vol 5M ($)") },
                            colors = transparentTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        OutlinedTextField(
                            value = minBuys5m,
                            onValueChange = { minBuys5m = it },
                            label = { Text("Min Buys 5M") },
                            colors = transparentTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = minMcap,
                            onValueChange = { minMcap = it },
                            label = { Text("Min Market Cap ($)") },
                            colors = transparentTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        OutlinedTextField(
                            value = maxMcap,
                            onValueChange = { maxMcap = it },
                            label = { Text("Max Market Cap ($)") },
                            colors = transparentTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }
        }

        // --- SECTION 4: Buy Pressure & Price Triggers ---
        item {
            FilterGroupCard(title = "Momentum Triggers") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = minPriceChange5m,
                            onValueChange = { minPriceChange5m = it },
                            label = { Text("Min Change 5M (%)") },
                            colors = transparentTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )

                        OutlinedTextField(
                            value = minPriceChange1h,
                            onValueChange = { minPriceChange1h = it },
                            label = { Text("Min Change 1H (%)") },
                            colors = transparentTextFieldColors(),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }

                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Min Buy Ratio (5m)", color = TextPrimary, fontSize = 12.sp)
                            Text(text = "${minBuyPressure.toInt()}%", color = CyberGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Slider(
                            value = minBuyPressure,
                            onValueChange = { minBuyPressure = it },
                            valueRange = 50f..100f,
                            colors = SliderDefaults.colors(
                                thumbColor = CyberGreen,
                                activeTrackColor = CyberGreen,
                                inactiveTrackColor = BorderColor
                            )
                        )
                    }
                }
            }
        }

        // --- SECTION 5: RugCheck Shield ---
        item {
            FilterGroupCard(title = "RugCheck Shield & Security") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Enable Audit Filters", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Leverage rugcheck.xyz to intercept high-risk contracts", color = TextSecondary, fontSize = 11.sp)
                        }
                        Switch(
                            checked = checkRug,
                            onCheckedChange = { checkRug = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberGreen,
                                checkedTrackColor = CyberGreenSubtle,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = SurfaceBg
                            )
                        )
                    }

                    if (checkRug) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "Critical Risk Score Limit", color = TextPrimary, fontSize = 12.sp)
                                Text(text = "${maxRugScore.toInt()}", color = if (maxRugScore < 300) CyberGreen else OrangeGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Slider(
                                value = maxRugScore,
                                onValueChange = { maxRugScore = it },
                                valueRange = 100f..1000f,
                                colors = SliderDefaults.colors(
                                    thumbColor = if (maxRugScore < 300) CyberGreen else OrangeGold,
                                    activeTrackColor = if (maxRugScore < 300) CyberGreen else OrangeGold,
                                    inactiveTrackColor = BorderColor
                                )
                            )
                        }
                    }
                }
            }
        }

        // --- SAVE COMMAND BAR ---
        item {
            Button(
                onClick = { validateAndSave() },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGreen),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .padding(bottom = 8.dp)
            ) {
                Text(
                    text = "APPLY TRIGGER FILTERS",
                    color = SurfaceBg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun FilterGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, BorderColor, RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                color = CyberGreen,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            content()
        }
    }
}

@Composable
fun transparentTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary,
    focusedBorderColor = CyberGreen,
    unfocusedBorderColor = BorderColor,
    focusedLabelColor = CyberGreen,
    unfocusedLabelColor = TextSecondary,
    cursorColor = CyberGreen
)
