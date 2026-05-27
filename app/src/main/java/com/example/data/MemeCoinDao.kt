package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MemeCoinDao {

    // --- Signals ---
    @Query("SELECT * FROM signal_logs ORDER BY timestamp DESC")
    fun getAllSignals(): Flow<List<SignalLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSignal(signal: SignalLog)

    @Query("DELETE FROM signal_logs")
    suspend fun clearSignals()

    // --- Console Logs ---
    @Query("SELECT * FROM console_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int): Flow<List<ConsoleLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ConsoleLog)

    @Query("DELETE FROM console_logs WHERE id NOT IN (SELECT id FROM console_logs ORDER BY timestamp DESC LIMIT :keepCount)")
    suspend fun pruneLogs(keepCount: Int)

    @Query("DELETE FROM console_logs")
    suspend fun clearLogs()

    // --- Alerted Tokens ---
    @Query("SELECT mint FROM alerted_tokens")
    suspend fun getAllAlertedMints(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAlertedToken(token: AlertedToken)

    @Query("DELETE FROM alerted_tokens")
    suspend fun clearAlertedTokens()

    // --- App Settings ---
    @Query("SELECT * FROM app_settings WHERE id = 1")
    fun getSettingsFlow(): Flow<AppSettings?>

    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun getSettings(): AppSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: AppSettings)
}
