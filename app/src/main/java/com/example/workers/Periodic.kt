package com.example.workers

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.Date
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

class PeriodicWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        val intervalMinutes = inputData.getLong("interval_minutes", 15L)
        val next = Date(System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(intervalMinutes))
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        val nextText = sdf.format(next)

        Notifications.createChannel(applicationContext)
        Notifications.notify(applicationContext,
            "Periodic notice",
            "next launch: $nextText")

        return Result.success()
    }
}