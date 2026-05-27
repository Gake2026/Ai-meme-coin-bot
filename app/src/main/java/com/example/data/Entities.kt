package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_logs")
data class SignalLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mint: String,
    val symbol: String,
    val name: String,
    val price: Double,
    val marketCap: Double,
    val liquidity: Double,
    val ageMins: Double,
    val change5m: Double,
    val change1h: Double,
    val change6h: Double,
    val change24h: Double,
    val volume5m: Double,
    val volume1h: Double,
    val volume6h: Double,
    val volume24h: Double,
    val buys5m: Int,
    val sells5m: Int,
    val buys1h: Int,
    val sells1h: Int,
    val buyPct: Int,
    val score: Int,
    val tier: String,
    val rugScore: Int,
    val rugRisks: String,
    val sources: String,
    val chartUrl: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "console_logs")
data class ConsoleLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val message: String,
    val level: String, // INFO, WARN, ERROR, SIGNAL
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "alerted_tokens")
data class AlertedToken(
    @PrimaryKey val mint: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_settings")
data class AppSettings(
    @PrimaryKey val id: Int = 1,
    val botToken: String = "8722588072:AAGsvWj2AWoTqsbEeG_x9tQe4H55H7Vnp2s",
    val chatId: String = "6528054132",
    val scanIntervalSeconds: Int = 60,
    val minLiquidity: Double = 8000.0,
    val minVolume1h: Double = 5000.0,
    val minVolume5m: Double = 300.0,
    val minBuys5m: Int = 4,
    val maxMcap: Double = 5000000.0,
    val minMcap: Double = 5000.0,
    val maxAgeMinutes: Int = 240,
    val minBuyPressure: Int = 52,
    val minPriceChange5m: Double = 3.0,
    val minPriceChange1h: Double = 8.0,
    val maxRugRiskScore: Int = 800,
    val checkRug: Boolean = true
)
