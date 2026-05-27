package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MemeCoinRepository(private val memeCoinDao: MemeCoinDao) {

    val allSignals: Flow<List<SignalLog>> = memeCoinDao.getAllSignals()

    val appSettings: Flow<AppSettings> = memeCoinDao.getSettingsFlow()
        .map { it ?: AppSettings() }

    fun getRecentLogs(limit: Int = 100): Flow<List<ConsoleLog>> =
        memeCoinDao.getRecentLogs(limit)

    suspend fun getSettings(): AppSettings {
        return memeCoinDao.getSettings() ?: AppSettings()
    }

    suspend fun saveSettings(settings: AppSettings) {
        memeCoinDao.saveSettings(settings)
    }

    suspend fun logInfo(message: String) {
        memeCoinDao.insertLog(ConsoleLog(message = message, level = "INFO"))
        memeCoinDao.pruneLogs(200)
    }

    suspend fun logWarn(message: String) {
        memeCoinDao.insertLog(ConsoleLog(message = message, level = "WARN"))
        memeCoinDao.pruneLogs(200)
    }

    suspend fun logError(message: String) {
        memeCoinDao.insertLog(ConsoleLog(message = message, level = "ERROR"))
        memeCoinDao.pruneLogs(200)
    }

    suspend fun logSignal(message: String) {
        memeCoinDao.insertLog(ConsoleLog(message = message, level = "SIGNAL"))
        memeCoinDao.pruneLogs(200)
    }

    suspend fun clearLogs() {
        memeCoinDao.clearLogs()
    }

    suspend fun clearSignals() {
        memeCoinDao.clearSignals()
    }

    suspend fun insertSignal(signal: SignalLog) {
        memeCoinDao.insertSignal(signal)
    }

    suspend fun getAlertedTokens(): Set<String> {
        return memeCoinDao.getAllAlertedMints().toSet()
    }

    suspend fun addAlertedToken(mint: String) {
        memeCoinDao.insertAlertedToken(AlertedToken(mint = mint))
    }

    suspend fun clearAlertedTokens() {
        memeCoinDao.clearAlertedTokens()
    }
}
