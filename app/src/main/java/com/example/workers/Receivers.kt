package com.example.workers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit
import androidx.work.Data
import androidx.work.OneTimeWorkRequest
import androidx.work.ExistingPeriodicWorkPolicy


class FullBattery : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val pct = if (level >= 0 && scale > 0) (level * 100) / scale else -1

        if (pct == 100) {
            val prefs = context.getSharedPreferences("periodic_prefs",
                Context.MODE_PRIVATE)
            val minutes = prefs.getLong("interval_minutes", 15L)

            val input = Data.Builder().putLong("interval_minutes", minutes).build()

            val work: OneTimeWorkRequest = OneTimeWorkRequestBuilder<PeriodicWorker>()
                .setInputData(input)
                .addTag("periodic_full_battery_trigger")
                .build()

            WorkManager.getInstance(context).enqueue(work)
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("periodic_prefs",
                Context.MODE_PRIVATE)
            val enabled = prefs.getBoolean("enabled", false)
            val onlyFull = prefs.getBoolean("only_full_battery", false)
            val minutes = prefs.getLong("interval_minutes", 15L)

            val workManager = WorkManager.getInstance(context)
            if (enabled) {
                if (!onlyFull) {
                    val request: PeriodicWorkRequest = PeriodicWorkRequestBuilder<PeriodicWorker>(
                        minutes, TimeUnit.MINUTES)
                        .addTag("periodic")
                        .build()
                    workManager.enqueueUniquePeriodicWork("periodic_unique_work",
                        ExistingPeriodicWorkPolicy.REPLACE, request)
                }
            }
        }
    }
}