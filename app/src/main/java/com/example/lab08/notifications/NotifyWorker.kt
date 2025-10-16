package com.example.lab08.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.example.lab08.MainActivity
import com.example.lab08.R

class NotifyWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val taskId = inputData.getInt("taskId", -1)
        val title = inputData.getString("title") ?: "Tarea"
        val description = inputData.getString("description") ?: ""
        sendNotification(title, description, taskId)
        return Result.success()
    }

    private fun sendNotification(title: String, text: String, taskId: Int) {
        val context = applicationContext
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, taskId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationHelper.notify(context, taskId, notification)
    }
}
