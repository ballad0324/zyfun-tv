package com.hoopoe.zyfuntv.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/** 通知渠道与轻量提醒：更新检查、备份完成等 */
object Notify {
    const val CHANNEL_GENERAL = "general"
    private const val NOTIF_TAG = "zyfuntv"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_GENERAL,
                "常规通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "更新、备份与后台任务提醒" }
            manager.createNotificationChannel(channel)
        }
    }

    fun notify(context: Context, title: String, message: String?, id: Int = 1001) {
        ensureChannels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_GENERAL)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(message ?: "")
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_TAG, id, builder.build())
    }
}
