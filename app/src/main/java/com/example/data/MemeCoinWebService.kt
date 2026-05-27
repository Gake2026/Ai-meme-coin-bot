package com.example.data

import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MemeCoinWebService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    // --- TELEGRAM SEND WITH IP FALLBACK ---
    fun sendTelegram(botToken: String, chatId: String, message: String): Boolean {
        val hosts = listOf(
            "api.telegram.org",
            "149.154.167.220",
            "149.154.167.221",
            "91.108.4.160"
        )
        for (host in hosts) {
            try {
                val url = "https://$host/bot$botToken/sendMessage"
                val bodyJson = JSONObject().apply {
                    put("chat_id", chatId)
                    put("text", message)
                    put("disable_web_page_preview", true)
                }

                val requestBuilder = Request.Builder()
                    .url(url)
                    .post(bodyJson.toString().toRequestBody(jsonMediaType))

                if (host != "api.telegram.org") {
                    requestBuilder.header("Host", "api.telegram.org")
                }

                client.newCall(requestBuilder.build()).execute().use { response ->
                    if (response.isSuccessful) {
                        return true
                    }
                    Log.w("Telegram", "Failed with host $host, status: ${response.code}")
                }
            } catch (e: Exception) {
                Log.e("Telegram", "Error with host $host: ${e.message}")
            }
        }
        return false
    }

    // --- SOURCE 1: DexScreener New Token Profiles ---
    fun sourceDexNew(): List<Map<String, Any>> {
        val out = mutableListOf<Map<String, Any>>()
        try {
            val request = Request.Builder()
                .url("https://api.dexscreener.com/token-profiles/latest/v1")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val arr = JSONArray(body)
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val chainId = obj.optString("chainId", "")
                    val tokenAddress = obj.optString("tokenAddress", "")
                    if (chainId.equals("solana", ignoreCase = true) && tokenAddress.isNotEmpty()) {
                        out.add(mapOf("mint" to tokenAddress, "source" to "DexScreener New"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Src1", "DexNew error: ${e.message}")
        }
        return out
    }

    // --- SOURCE 2: DexScreener Trending Boosts ---
    fun sourceDexTrending(): List<Map<String, Any>> {
        val out = mutableListOf<Map<String, Any>>()
        try {
            val request = Request.Builder()
                .url("https://api.dexscreener.com/token-boosts/top/v1")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val arr = JSONArray(body)
                val count = minOf(arr.length(), 20)
                for (i in 0 until count) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val chainId = obj.optString("chainId", "")
                    val tokenAddress = obj.optString("tokenAddress", "")
                    if (chainId.equals("solana", ignoreCase = true) && tokenAddress.isNotEmpty()) {
                        out.add(mapOf("mint" to tokenAddress, "source" to "DexScreener Trending"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Src2", "DexTrend error: ${e.message}")
        }
        return out
    }

    // --- SOURCE 3: DexScreener Top Gainers Search ---
    fun sourceDexGainers(): List<Map<String, Any>> {
        val out = mutableListOf<Map<String, Any>>()
        try {
            val request = Request.Builder()
                .url("https://api.dexscreener.com/latest/dex/search?q=solana")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val mainObj = JSONObject(body)
                val pairs = mainObj.optJSONArray("pairs") ?: return emptyList()

                val solanaPairs = mutableListOf<JSONObject>()
                for (i in 0 until pairs.length()) {
                    val pair = pairs.optJSONObject(i) ?: continue
                    val chainId = pair.optString("chainId", "")
                    val change5m = pair.optJSONObject("priceChange")?.optDouble("m5") ?: 0.0
                    if (chainId.equals("solana", ignoreCase = true) && change5m > 5.0) {
                        solanaPairs.add(pair)
                    }
                }

                // Sort descending
                solanaPairs.sortByDescending { it.optJSONObject("priceChange")?.optDouble("m5") ?: 0.0 }

                val limit = minOf(solanaPairs.size, 20)
                for (i in 0 until limit) {
                    val pair = solanaPairs[i]
                    val baseToken = pair.optJSONObject("baseToken") ?: continue
                    val address = baseToken.optString("address", "")
                    if (address.isNotEmpty()) {
                        out.add(mapOf(
                            "mint" to address,
                            "source" to "DexScreener Gainer",
                            "_pair" to pair
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Src3", "DexGain error: ${e.message}")
        }
        return out
    }

    // --- SOURCE 4: Birdeye Volume Movers ---
    fun sourceBirdeyeVolume(): List<Map<String, Any>> {
        val out = mutableListOf<Map<String, Any>>()
        try {
            val url = "https://public-api.birdeye.so/defi/token_trending?sort_by=v30mUSD&sort_type=desc&offset=0&limit=25&min_liquidity=5000"
            val request = Request.Builder()
                .url(url)
                .header("X-Chain", "solana")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val mainObj = JSONObject(body)
                val dataObj = mainObj.optJSONObject("data") ?: return emptyList()
                val items = dataObj.optJSONArray("items") ?: return emptyList()

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val address = item.optString("address", "")
                    if (address.isNotEmpty()) {
                        out.add(mapOf("mint" to address, "source" to "Birdeye Volume"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Src4", "BirdVol error: ${e.message}")
        }
        return out
    }

    // --- SOURCE 5: Birdeye New Listings ---
    fun sourceBirdeyeNew(): List<Map<String, Any>> {
        val out = mutableListOf<Map<String, Any>>()
        try {
            val url = "https://public-api.birdeye.so/defi/v2/tokens/new_listing?limit=20&meme_platform_enabled=true"
            val request = Request.Builder()
                .url(url)
                .header("X-Chain", "solana")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val mainObj = JSONObject(body)
                val dataObj = mainObj.optJSONObject("data") ?: return emptyList()
                val items = dataObj.optJSONArray("items") ?: return emptyList()

                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    val address = item.optString("address", "")
                    if (address.isNotEmpty()) {
                        out.add(mapOf("mint" to address, "source" to "Birdeye New"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Src5", "BirdNew error: ${e.message}")
        }
        return out
    }

    // --- SOURCE 6: GeckoTerminal Trending Pools ---
    fun sourceGeckoTrending(): List<Map<String, Any>> {
        val out = mutableListOf<Map<String, Any>>()
        try {
            val request = Request.Builder()
                .url("https://api.geckoterminal.com/api/v2/networks/solana/trending_pools?page=1")
                .header("Accept", "application/json;version=20230302")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val mainObj = JSONObject(body)
                val dataArr = mainObj.optJSONArray("data") ?: return emptyList()
                val limit = minOf(dataArr.length(), 20)

                for (i in 0 until limit) {
                    val pool = dataArr.optJSONObject(i) ?: continue
                    val token = pool.optJSONObject("relationships")
                        ?.optJSONObject("base_token")
                        ?.optJSONObject("data")
                        ?.optString("id", "") ?: ""
                    val mint = if (token.startsWith("solana_")) token.replace("solana_", "") else ""
                    if (mint.isNotEmpty()) {
                        out.add(mapOf("mint" to mint, "source" to "GeckoTerminal Trending"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Src6", "GeckoTrend error: ${e.message}")
        }
        return out
    }

    // --- SOURCE 7: GeckoTerminal New Pools ---
    fun sourceGeckoNew(): List<Map<String, Any>> {
        val out = mutableListOf<Map<String, Any>>()
        try {
            val request = Request.Builder()
                .url("https://api.geckoterminal.com/api/v2/networks/solana/new_pools?page=1")
                .header("Accept", "application/json;version=20230302")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val mainObj = JSONObject(body)
                val dataArr = mainObj.optJSONArray("data") ?: return emptyList()
                val limit = minOf(dataArr.length(), 20)

                for (i in 0 until limit) {
                    val pool = dataArr.optJSONObject(i) ?: continue
                    val token = pool.optJSONObject("relationships")
                        ?.optJSONObject("base_token")
                        ?.optJSONObject("data")
                        ?.optString("id", "") ?: ""
                    val mint = if (token.startsWith("solana_")) token.replace("solana_", "") else ""
                    if (mint.isNotEmpty()) {
                        out.add(mapOf("mint" to mint, "source" to "GeckoTerminal New"))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Src7", "GeckoNew error: ${e.message}")
        }
        return out
    }

    // --- FETCH SINGLE PAIR DETAIL FROM DEXSCREENER ---
    fun fetchPairDetail(tokenAddress: String): JSONObject? {
        if (tokenAddress.startsWith("SIM_")) {
            return getSimulatedPairDetail(tokenAddress)
        }
        try {
            val request = Request.Builder()
                .url("https://api.dexscreener.com/latest/dex/tokens/$tokenAddress")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val pairs = json.optJSONArray("pairs") ?: return null
                if (pairs.length() == 0) return null

                // Select pair with highest liquidity.usd
                var maxLiq = -1.0
                var bestPair: JSONObject? = null
                for (i in 0 until pairs.length()) {
                    val pair = pairs.optJSONObject(i) ?: continue
                    val liq = pair.optJSONObject("liquidity")?.optDouble("usd") ?: 0.0
                    if (liq > maxLiq) {
                        maxLiq = liq
                        bestPair = pair
                    }
                }
                return bestPair
            }
        } catch (e: Exception) {
            Log.e("Detail", "Error fetching pair for $tokenAddress: ${e.message}")
        }
        return null
    }

    // --- RUG CHECK SUMMARY ---
    fun checkRug(mint: String, maxScore: Int): JSONObject? {
        if (mint.startsWith("SIM_")) {
            val score = if (mint.contains("ruggy")) 950 else 35
            val isSafe = score < maxScore
            val risks = JSONArray().apply {
                if (mint.contains("ruggy")) {
                    put("Mintable Contract (CRITICAL)")
                    put("Single holder holds > 50%")
                    put("Suspicious Liquidity Pool Lock")
                }
            }
            return JSONObject().apply {
                put("score", score)
                put("risks", risks)
                put("is_safe", isSafe)
            }
        }
        try {
            val request = Request.Builder()
                .url("https://api.rugcheck.xyz/v1/tokens/$mint/report/summary")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    // Safe fallback if API fails
                    return JSONObject().apply {
                        put("score", 0)
                        put("risks", JSONArray())
                        put("is_safe", true)
                    }
                }
                val body = response.body?.string() ?: return null
                val fullReport = JSONObject(body)
                val score = fullReport.optInt("score", 0)
                val risksArr = fullReport.optJSONArray("risks") ?: JSONArray()
                val risks = JSONArray()
                for (i in 0 until risksArr.length()) {
                    val riskItem = risksArr.optJSONObject(i) ?: continue
                    val level = riskItem.optString("level", "")
                    if (level == "danger" || level == "warn") {
                        risks.put(riskItem.optString("name", ""))
                    }
                }

                return JSONObject().apply {
                    put("score", score)
                    put("risks", risks)
                    put("is_safe", score < maxScore)
                }
            }
        } catch (e: Exception) {
            Log.e("RugCheck", "Error rug checking $mint: ${e.message}")
            return JSONObject().apply {
                put("score", 0)
                put("risks", JSONArray())
                put("is_safe", true) // Non-blocking fallback
            }
        }
    }



    // --- MOCK/SANDBOX RESPONSE BUILDER ---
    private fun getSimulatedPairDetail(mint: String): JSONObject {
        val symbol: String
        val name: String
        val price: Double
        val mcap: Double
        val liq: Double
        val change5m: Double
        val change1h: Double
        val buys5m: Int
        val sells5m: Int

        when {
            mint.contains("doge2") -> {
                symbol = "DOGE2"
                name = "Dogecoin 2.0"
                price = 0.045
                mcap = 4500000.0
                liq = 250000.0
                change5m = 4.2
                change1h = 12.5
                buys5m = 18
                sells5m = 5
            }
            mint.contains("solmoon") -> {
                symbol = "SOLMOON"
                name = "Solana Moon"
                price = 0.00012
                mcap = 120000.0
                liq = 15000.0
                change5m = 6.8
                change1h = 24.0
                buys5m = 9
                sells5m = 2
            }
            mint.contains("ruggy") -> {
                symbol = "RUGGY"
                name = "Ruggy Bear"
                price = 0.000008
                mcap = 8000.0
                liq = 1200.0
                change5m = -12.5
                change1h = -45.0
                buys5m = 2
                sells5m = 15
            }
            mint.contains("pepe3") -> {
                symbol = "PEPE3"
                name = "Pepe the Third"
                price = 0.00085
                mcap = 850000.0
                liq = 75000.0
                change5m = 3.5
                change1h = 9.2
                buys5m = 12
                sells5m = 4
            }
            else -> {
                symbol = "SIM"
                name = "Simulated Token"
                price = 0.001
                mcap = 50000.0
                liq = 10000.0
                change5m = 1.0
                change1h = 5.0
                buys5m = 5
                sells5m = 3
            }
        }

        val pair = JSONObject()
        pair.put("priceUsd", price)
        pair.put("marketCap", mcap)
        pair.put("fdv", mcap)
        pair.put("pairCreatedAt", System.currentTimeMillis() - 45 * 60 * 1000) // 45 mins old
        pair.put("url", "https://dexscreener.com/solana/$mint")

        val baseToken = JSONObject()
        baseToken.put("symbol", symbol)
        baseToken.put("name", name)
        baseToken.put("address", mint)
        pair.put("baseToken", baseToken)

        val liquidity = JSONObject()
        liquidity.put("usd", liq)
        pair.put("liquidity", liquidity)

        val priceChange = JSONObject()
        priceChange.put("m5", change5m)
        priceChange.put("h1", change1h)
        pair.put("priceChange", priceChange)

        val txnsm5 = JSONObject()
        txnsm5.put("buys", buys5m)
        txnsm5.put("sells", sells5m)
        val txns = JSONObject()
        txns.put("m5", txnsm5)
        pair.put("txns", txns)

        val volume = JSONObject()
        volume.put("m5", liq * 0.05)
        volume.put("h1", liq * 0.2)
        pair.put("volume", volume)

        return pair
    }
}
