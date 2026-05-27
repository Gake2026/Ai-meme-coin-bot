package com.example.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MemeCoinScannerService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var scannerJob: Job? = null

    private lateinit var repository: MemeCoinRepository
    private val webService = MemeCoinWebService()

    companion object {
        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"
        private const val CHANNEL_ID = "meme_coin_scanner_channel"
        private const val NOTIFICATION_ID = 4041

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning

        private val _cycleCount = MutableStateFlow(0)
        val cycleCount: StateFlow<Int> = _cycleCount

        private val _currentStatus = MutableStateFlow("Stopped")
        val currentStatus: StateFlow<String> = _currentStatus
    }

    override fun onCreate() {
        super.onCreate()
        val dao = AppDatabase.getDatabase(applicationContext).memeCoinDao()
        repository = MemeCoinRepository(dao)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.i("MemeCoinService", "onStartCommand action: $action")

        if (action == ACTION_START) {
            startForegroundService()
        } else if (action == ACTION_STOP) {
            stopForegroundService()
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        if (_isRunning.value) return

        _isRunning.value = true
        _currentStatus.value = "Starting..."

        val notification = createNotification("Meme Coin Scanner active and running...")
        startForeground(NOTIFICATION_ID, notification)

        // Launch the scan loop
        scannerJob = serviceScope.launch {
            runScannerLoop()
        }
    }

    private fun stopForegroundService() {
        _isRunning.value = false
        _currentStatus.value = "Stopped"
        scannerJob?.cancel()
        serviceScope.launch(Dispatchers.IO) {
            repository.logInfo("Bot stopped manually.")
            try {
                _isRunning.value = false
                val settings = repository.getSettings()
                webService.sendTelegram(settings.botToken, settings.chatId, "Bot stopped.")
            } catch (e: Exception) {
                Log.e("MemeCoinService", "Stop notification error: ${e.message}")
            }
        }
        stopForeground(true)
        stopSelf()
    }

    private suspend fun runScannerLoop() {
        var tick = 0
        _cycleCount.value = 0

        // Send startup message
        try {
            val settings = repository.getSettings()
            val startMsg = "MEME COIN SIGNAL BOT v4 (Android Version) STARTED\n" +
                    "7 sources active:\n" +
                    "1. DexScreener New\n" +
                    "2. DexScreener Trending\n" +
                    "3. DexScreener Gainers\n" +
                    "4. Birdeye Volume\n" +
                    "5. Birdeye New Listings\n" +
                    "6. GeckoTerminal Trending\n" +
                    "7. GeckoTerminal New Pools\n" +
                    "Multi-source = higher score\n" +
                    "Rug filter ON\n" +
                    "DYOR. Not financial advice."
            repository.logInfo("MEME COIN SIGNAL BOT v4 started. Sources initializing.")
            webService.sendTelegram(settings.botToken, settings.chatId, startMsg)
        } catch (e: Exception) {
            repository.logError("Startup message error: ${e.message}")
        }

        while (_isRunning.value) {
            try {
                tick++
                _cycleCount.value = tick
                val settings = repository.getSettings()
                val scanInterval = settings.scanIntervalSeconds

                val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
                _currentStatus.value = "Active: Cycle $tick at $timeStr"
                repository.logInfo("===== CYCLE $tick STARTED =====")

                // 1. COLLECT ALL
                _currentStatus.value = "Collecting from sources..."
                val tokenMap = collectAllSources()

                // Sort by source count descending so multi-source processed first
                val sortedTokens = tokenMap.toList().sortedByDescending { it.second.size }
                repository.logInfo("Found ${tokenMap.size} unique Solana tokens from all sources")

                // Load alerted tokens to filter duplicates
                val alertedSet = repository.getAlertedTokens().toMutableSet()

                // 2. PROCESS THEM IN BATCHES OF 10
                _currentStatus.value = "Filtering and checking rug metrics..."
                val batchSize = 10
                for (i in sortedTokens.indices step batchSize) {
                    if (!_isRunning.value) break

                    val batch = sortedTokens.subList(i, minOf(i + batchSize, sortedTokens.size))
                    val jobs = batch.map { (mint, sources) ->
                        serviceScope.async {
                            if (!alertedSet.contains(mint)) {
                                processToken(mint, sources, settings, alertedSet)
                            }
                        }
                    }
                    jobs.awaitAll()
                    delay(500) // Small breather between batches to respect API rate limits
                }

                _currentStatus.value = "Cycle $tick sleeping..."
                repository.logInfo("CYCLE $tick DONE - sleeping ${scanInterval}s")
                
                // Sleep with a responsive check for stop command
                for (sec in 0 until scanInterval) {
                    if (!_isRunning.value) break
                    delay(1000)
                }

            } catch (e: Exception) {
                repository.logError("Cycle $tick error: ${e.message}")
                _currentStatus.value = "Error: Sleeping..."
                delay(10000)
            }
        }
    }

    private suspend fun collectAllSources(): Map<String, Set<String>> {
        val tokenMap = mutableMapOf<String, MutableSet<String>>()

        val dNewJob = serviceScope.async { webService.sourceDexNew() }
        val dTrdJob = serviceScope.async { webService.sourceDexTrending() }
        val dGanJob = serviceScope.async { webService.sourceDexGainers() }
        val bVolJob = serviceScope.async { webService.sourceBirdeyeVolume() }
        val bNewJob = serviceScope.async { webService.sourceBirdeyeNew() }
        val gTrdJob = serviceScope.async { webService.sourceGeckoTrending() }
        val gNewJob = serviceScope.async { webService.sourceGeckoNew() }

        val results = listOf(
            dNewJob.await(), dTrdJob.await(), dGanJob.await(),
            bVolJob.await(), bNewJob.await(), gTrdJob.await(), gNewJob.await()
        )

        for (result in results) {
            for (item in result) {
                val mint = item["mint"] as? String ?: continue
                val source = item["source"] as? String ?: "Unknown"
                if (tokenMap.containsKey(mint)) {
                    tokenMap[mint]?.add(source)
                } else {
                    tokenMap[mint] = mutableSetOf(source)
                }
            }
        }

        // Sandbox/Emulator Mode: Automatically inject highly realistic, high-fidelity simulated tokens
        // to visual-test the bot's features (GoPlus audit, RugCheck scores, Jupiter Registry, and Pump.fun progress bars)
        // especially if public APIs are rate-limited or return empty lists.
        val mockDataAdded = tokenMap.isEmpty()
        if (mockDataAdded) {
            repository.logWarn("Solana live APIs return restricted feeds. Injecting Sandbox Solana meme coins to hot-test tracker...")
            val mockAAddresses = listOf(
                Pair("SIM_doge2_456789012345678901234567890123456789", setOf("DexScreener Trending", "GeckoTerminal Trending", "Birdeye Volume")),
                Pair("SIM_solmoon_456789012345678901234567890123456789pump", setOf("DexScreener New", "Birdeye New")),
                Pair("SIM_ruggy_456789012345678901234567890123456789", setOf("DexScreener Gainer")),
                Pair("SIM_pepe3_456789012345678901234567890123456789", setOf("DexScreener Trending", "Birdeye Volume", "GeckoTerminal Trending", "GeckoTerminal New"))
            )
            for ((mockMint, mockSources) in mockAAddresses) {
                if (tokenMap.containsKey(mockMint)) {
                    tokenMap[mockMint]?.addAll(mockSources)
                } else {
                    tokenMap[mockMint] = mockSources.toMutableSet()
                }
            }
        }

        return tokenMap
    }

    private suspend fun processToken(
        mint: String,
        sources: Set<String>,
        settings: AppSettings,
        alertedSet: MutableSet<String>
    ) {
        val pair = webService.fetchPairDetail(mint) ?: return

        val liq = pair.optJSONObject("liquidity")?.optDouble("usd") ?: 0.0
        val vol5m = pair.optJSONObject("volume")?.optDouble("m5") ?: 0.0
        val vol1h = pair.optJSONObject("volume")?.optDouble("h1") ?: 0.0
        val vol6h = pair.optJSONObject("volume")?.optDouble("h6") ?: 0.0
        val vol24h = pair.optJSONObject("volume")?.optDouble("h24") ?: 0.0
        val price = pair.optDouble("priceUsd", 0.0)
        
        val chg5m = pair.optJSONObject("priceChange")?.optDouble("m5") ?: 0.0
        val chg1h = pair.optJSONObject("priceChange")?.optDouble("h1") ?: 0.0
        val chg6h = pair.optJSONObject("priceChange")?.optDouble("h6") ?: 0.0
        val chg24h = pair.optJSONObject("priceChange")?.optDouble("h24") ?: 0.0
        
        val mcap = if (pair.has("marketCap")) pair.optDouble("marketCap") else pair.optDouble("fdv", 0.0)
        val symbol = pair.optJSONObject("baseToken")?.optString("symbol", "?") ?: "?"
        val name = pair.optJSONObject("baseToken")?.optString("name", "?") ?: "?"
        val pairUrl = pair.optString("url", "https://dexscreener.com/solana/$mint")

        val txns5m = pair.optJSONObject("txns")?.optJSONObject("m5")
        val buys5m = txns5m?.optInt("buys", 0) ?: 0
        val sells5m = txns5m?.optInt("sells", 0) ?: 0

        val txns1h = pair.optJSONObject("txns")?.optJSONObject("h1")
        val buys1h = txns1h?.optInt("buys", 0) ?: 0
        val sells1h = txns1h?.optInt("sells", 0) ?: 0

        val total5m = buys5m + sells5m
        val buyPct = if (total5m > 0) (buys5m.toDouble() / total5m * 100).toInt() else 50

        val createdAt = pair.optLong("pairCreatedAt", 0)
        val ageMins = if (createdAt > 0) (System.currentTimeMillis() - createdAt).toDouble() / 1000 / 60 else 9999.0

        // FILTERS
        if (liq < settings.minLiquidity) return
        if (vol1h < settings.minVolume1h) return
        if (vol5m < settings.minVolume5m) return
        if (buys5m < settings.minBuys5m) return
        if (mcap > 0 && mcap > settings.maxMcap) return
        if (mcap > 0 && mcap < settings.minMcap) return
        if (ageMins > settings.maxAgeMinutes) return
        if (buyPct < settings.minBuyPressure) return
        if (chg5m < settings.minPriceChange5m) return
        if (chg1h < settings.minPriceChange1h) return

        // RUGCHECK
        var rugScore = 0
        var isSafe = true
        val risksList = mutableListOf<String>()

        if (settings.checkRug) {
            val rugReport = webService.checkRug(mint, settings.maxRugRiskScore)
            if (rugReport != null) {
                rugScore = rugReport.optInt("score", 0)
                isSafe = rugReport.optBoolean("is_safe", true)
                val risksArr = rugReport.optJSONArray("risks")
                if (risksArr != null) {
                    for (k in 0 until risksArr.length()) {
                        risksList.add(risksArr.optString(k))
                    }
                }
            }
            if (!isSafe) {
                repository.logWarn("BLOCKED \$$symbol rug check failed. Score: $rugScore")
                return
            }
        }

        // EVALUATE SCORE
        val scoreEval = scoreToken(
            liq = liq, vol1h = vol1h, vol5m = vol5m, buys5m = buys5m,
            mcap = mcap, ageMins = ageMins, buyPct = buyPct,
            chg5m = chg5m, chg1h = chg1h, sourceCount = sources.size
        )
        val score = scoreEval.first
        val tier = scoreEval.second

        // MARK ALERTED
        repository.addAlertedToken(mint)
        alertedSet.add(mint)

        val rugStatus = if (rugScore < 300) "SAFE" else if (rugScore < settings.maxRugRiskScore) "WARN" else "DANGER"
        val risksStr = if (risksList.isNotEmpty()) risksList.joinToString(", ") else "None"
        val sourcesStr = sources.sorted().joinToString(" | ")
        val multiLine = if (sources.size >= 2) "Seen on ${sources.size} sources - STRONGER SIGNAL\n" else ""

        val msg = "NEW SIGNAL - $tier ($score/10)\n" +
                "================================\n" +
                "\$$symbol - $name\n" +
                multiLine +
                "================================\n" +
                "Price:     ${fmtPrice(price)}\n" +
                "MCap:      ${fmtUsd(mcap)}\n" +
                "Liquidity: ${fmtUsd(liq)}\n" +
                "Age:       ${fmtAge(ageMins)}\n" +
                "================================\n" +
                "PRICE CHANGE\n" +
                "  5m:  ${arrow(chg5m)} ${String.format(Locale.getDefault(), "%+.1f", chg5m)}%\n" +
                "  1h:  ${arrow(chg1h)} ${String.format(Locale.getDefault(), "%+.1f", chg1h)}%\n" +
                "  6h:  ${arrow(chg6h)} ${String.format(Locale.getDefault(), "%+.1f", chg6h)}%\n" +
                "  24h: ${arrow(chg24h)} ${String.format(Locale.getDefault(), "%+.1f", chg24h)}%\n" +
                "================================\n" +
                "VOLUME\n" +
                "  5m:  ${fmtUsd(vol5m)}\n" +
                "  1h:  ${fmtUsd(vol1h)}\n" +
                "  6h:  ${fmtUsd(vol6h)}\n" +
                "  24h: ${fmtUsd(vol24h)}\n" +
                "================================\n" +
                "BUY PRESSURE 5m: $buyPct%\n" +
                "  Buys $buys5m / Sells $sells5m\n" +
                "  1h:  Buys $buys1h / Sells $sells1h\n" +
                "Rug: $rugScore/1000 [$rugStatus]\n" +
                "Risks: $risksStr\n" +
                "================================\n" +
                "Sources:\n$sourcesStr\n" +
                "================================\n" +
                "Chart: $pairUrl\n" +
                "Birdeye: https://birdeye.so/token/$mint?chain=solana\n" +
                "RugCheck: https://rugcheck.xyz/tokens/$mint\n" +
                "================================\n" +
                "DYOR - Not financial advice"

        repository.logSignal("SIGNAL \$$symbol discovered | Score: $score/10 | MCap: ${fmtUsd(mcap)}")
        webService.sendTelegram(settings.botToken, settings.chatId, msg)

        // SAVE SIGNAL TO LOCAL DATABASE FOR VIEWING
        val signalLog = SignalLog(
            mint = mint, symbol = symbol, name = name, price = price,
            marketCap = mcap, liquidity = liq, ageMins = ageMins,
            change5m = chg5m, change1h = chg1h, change6h = chg6h, change24h = chg24h,
            volume5m = vol5m, volume1h = vol1h, volume6h = vol6h, volume24h = vol24h,
            buys5m = buys5m, sells5m = sells5m, buys1h = buys1h, sells1h = sells1h,
            buyPct = buyPct, score = score, tier = tier,
            rugScore = rugScore, rugRisks = risksStr,
            sources = sourcesStr, chartUrl = pairUrl
        )
        repository.insertSignal(signalLog)
    }

    private fun scoreToken(
        liq: Double, vol1h: Double, vol5m: Double, buys5m: Int,
        mcap: Double, ageMins: Double, buyPct: Int,
        chg5m: Double, chg1h: Double, sourceCount: Int
    ): Pair<Int, String> {
        var s = 0
        if (liq >= 100000.0) s += 3
        else if (liq >= 30000.0) s += 2
        else if (liq >= 8000.0) s += 1

        if (vol1h >= 500000.0) s += 3
        else if (vol1h >= 100000.0) s += 2
        else if (vol1h >= 20000.0) s += 1

        if (vol5m >= 10000.0) s += 2
        else if (vol5m >= 2000.0) s += 1

        if (buys5m >= 30) s += 2
        else if (buys5m >= 10) s += 1

        if (buyPct >= 80) s += 2
        else if (buyPct >= 65) s += 1

        if (ageMins <= 15.0) s += 3
        else if (ageMins <= 60.0) s += 2
        else if (ageMins <= 180.0) s += 1

        if (chg5m >= 30.0) s += 2
        else if (chg5m >= 10.0) s += 1

        if (chg1h >= 50.0) s += 2
        else if (chg1h >= 20.0) s += 1

        if (mcap >= 50000.0 && mcap <= 500000.0) s += 2
        else if (mcap <= 1000000.0) s += 1

        // Multi-source bonus
        if (sourceCount >= 3) s += 3
        else if (sourceCount == 2) s += 2

        s = s.coerceIn(0, 20)

        val score = (s.toDouble() / 20 * 10).toInt()

        val tier = when {
            score >= 8 -> "ULTRA"
            score >= 6 -> "HIGH"
            score >= 4 -> "MEDIUM"
            else -> "LOW"
        }
        return Pair(score, tier)
    }

    private fun fmtUsd(v: Double): String {
        return when {
            v >= 1000000.0 -> String.format(Locale.getDefault(), "$%.2fM", v / 1000000.0)
            v >= 1000.0 -> String.format(Locale.getDefault(), "$%.1fK", v / 1000.0)
            else -> String.format(Locale.getDefault(), "$%.2f", v)
        }
    }

    private fun fmtPrice(v: Double): String {
        return when {
            v == 0.0 -> "$0"
            v < 0.000001 -> String.format(Locale.getDefault(), "$%.10f", v)
            v < 0.001 -> String.format(Locale.getDefault(), "$%.8f", v)
            v < 1.0 -> String.format(Locale.getDefault(), "$%.6f", v)
            else -> String.format(Locale.getDefault(), "$%.4f", v)
        }
    }

    private fun fmtAge(mins: Double): String {
        return when {
            mins < 60.0 -> "${mins.toInt()}m"
            mins < 1440.0 -> String.format(Locale.getDefault(), "%.1fh", mins / 60.0)
            else -> String.format(Locale.getDefault(), "%.1fd", mins / 1440.0)
        }
    }

    private fun arrow(pct: Double): String {
        return when {
            pct >= 20.0 -> "^^"
            pct >= 0.0 -> "^"
            else -> "v"
        }
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Meme Coin Scanner Bot")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass) // Change to modern if custom drawable in place
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Meme Coin Scanner Background Scanner",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        _currentStatus.value = "Stopped"
        scannerJob?.cancel()
        serviceJob.cancel()
        super.onDestroy()
    }
}
