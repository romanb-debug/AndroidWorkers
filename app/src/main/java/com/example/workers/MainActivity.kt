package com.example.workers

import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : ComponentActivity() {
    private val TAG = "periodic"
    private val TAG_FULL = "periodic_full_battery_trigger"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifications.createChannel(this)
        setContent { PeriodicReminderScreen() }
    }

    @Composable
    fun PeriodicReminderScreen() {
        val context = LocalContext.current
        val workManager = WorkManager.getInstance(context)

        var intervalText by remember { mutableStateOf("15") }
        var onlyWhileCharging by remember { mutableStateOf(false) }
        var onlyWhenFull by remember { mutableStateOf(false) }

        var currentStateText by remember { mutableStateOf("No running worker") }

        val prefs = context.getSharedPreferences("periodic_prefs", MODE_PRIVATE)

        val receiver = remember { FullBattery() }

        LaunchedEffect(Unit) {
            workManager.getWorkInfosByTagLiveData(TAG).observeForever { list ->
                val info = list?.firstOrNull()
                currentStateText = info?.state?.name ?: currentStateText
            }
            workManager.getWorkInfosByTagLiveData(TAG_FULL).observeForever { list ->
                val info = list?.firstOrNull()
                if (info != null) currentStateText = info.state.name
            }
        }

        Scaffold { padding ->
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { new ->
                        val digits = new.filter { it.isDigit() }
                        intervalText = digits
                    },
                    label = { Text("Interval (≥15 minutes)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onlyWhileCharging, onCheckedChange = { onlyWhileCharging = it })
                    Text("Only while charging")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onlyWhenFull, onCheckedChange = {
                        onlyWhenFull = it
                    })
                    Text("Only while full battery")
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        val minutes = intervalText.toLongOrNull() ?: 15L
                        val actualMinutes = if (minutes < 15L) 15L else minutes

                        prefs.edit {
                            putLong("interval_minutes", actualMinutes)
                                .putBoolean("enabled", true)
                                .putBoolean("only_full_battery", onlyWhenFull)
                        }

                        if (onlyWhenFull) {
                            try {
                                context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                            } catch (_: Exception) { }
                        } else {
                            val constraintsBuilder = Constraints.Builder()
                            if (onlyWhileCharging) constraintsBuilder.setRequiresCharging(true)
                            val constraints = constraintsBuilder.build()

                            val input = Data.Builder().putLong("interval_minutes", actualMinutes).build()

                            val request = PeriodicWorkRequestBuilder<PeriodicWorker>(actualMinutes, TimeUnit.MINUTES)
                                .setConstraints(constraints)
                                .setInputData(input)
                                .addTag(TAG)
                                .build()

                            workManager.enqueueUniquePeriodicWork(
                                "periodic_unique_work",
                                ExistingPeriodicWorkPolicy.REPLACE,
                                request
                            )
                        }
                    }) {
                        Text("Begin")
                    }

                    Button(onClick = {
                        prefs.edit { putBoolean("enabled", false) }
                        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
                        workManager.cancelAllWorkByTag(TAG)
                        workManager.cancelAllWorkByTag(TAG_FULL)
                        currentStateText = "Stopped"
                    }) {
                        Text("Stop")
                    }
                }

                Text("Status: $currentStateText")
            }
        }
    }
}