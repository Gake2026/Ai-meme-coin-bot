package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemeCoinViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: MemeCoinRepository

    val settings: StateFlow<AppSettings>
    val recentLogs: StateFlow<List<ConsoleLog>>
    val allSignals: StateFlow<List<SignalLog>>

    val isServiceRunning: StateFlow<Boolean> = MemeCoinScannerService.isRunning
    val currentServiceStatus: StateFlow<String> = MemeCoinScannerService.currentStatus
    val serviceCycleCount: StateFlow<Int> = MemeCoinScannerService.cycleCount

    init {
        val dao = AppDatabase.getDatabase(application).memeCoinDao()
        repository = MemeCoinRepository(dao)

        settings = repository.appSettings
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = AppSettings()
            )

        recentLogs = repository.getRecentLogs(100)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        allSignals = repository.allSignals
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )
    }

    fun startScanner(context: Context) {
        val intent = Intent(context, MemeCoinScannerService::class.java).apply {
            action = MemeCoinScannerService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopScanner(context: Context) {
        val intent = Intent(context, MemeCoinScannerService::class.java).apply {
            action = MemeCoinScannerService.ACTION_STOP
        }
        context.startService(intent)
    }

    fun saveSettings(newSettings: AppSettings) {
        viewModelScope.launch {
            repository.saveSettings(newSettings)
            repository.logInfo("Scanner configurations updated.")
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            repository.clearLogs()
            repository.logInfo("Logs cleared.")
        }
    }

    fun clearSignals() {
        viewModelScope.launch {
            repository.clearSignals()
            repository.logInfo("Signal history database cleared.")
        }
    }

    fun clearAlertCache() {
        viewModelScope.launch {
            repository.clearAlertedTokens()
            repository.logInfo("Alerted address cache cleared. Previously seen tokens can trigger signals again.")
        }
    }
}
