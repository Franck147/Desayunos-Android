package com.example.desayuno.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.desayuno.R
import com.example.desayuno.actividades.ActividadPrincipal

object NotificationHelper {
    const val EXTRA_ORDER_ID = "notification_order_id"
    const val EXTRA_USER_ID = "notification_user_id"
    const val CHANNEL_ID = "desayunos_orders_channel"

    fun createNotificationChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID,"Pedidos de desayunos",NotificationManager.IMPORTANCE_HIGH).apply {
                description="Preparación, pedidos listos y nuevas comandas"
            })
    }

    fun enabled(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE

    fun clear(context: Context) { NotificationManagerCompat.from(context).cancelAll() }

    fun show(context: Context,userId: String,notice: AppNotice) {
        createNotificationChannel(context)
        if(!enabled(context)) return
        val prefs=context.getSharedPreferences("notification_delivery",Context.MODE_PRIVATE)
        val key="delivered_$userId"
        val delivered=prefs.getStringSet(key,emptySet()).orEmpty().toSet()
        if(notice.id in delivered) return
        val intent=Intent(context,ActividadPrincipal::class.java).apply {
            flags=Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            data=Uri.parse("desayunos://orders/${notice.orderId}?account=$userId")
            putExtra(EXTRA_ORDER_ID,notice.orderId)
            putExtra(EXTRA_USER_ID,userId)
        }
        val tap=PendingIntent.getActivity(context,0,intent,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_breakfast)
            .setContentTitle(notice.title).setContentText(notice.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(notice.message))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(if(notice.ready) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(tap).setAutoCancel(true).setOnlyAlertOnce(true)
            .setColor(0xFF155B4F.toInt()).setWhen(notice.timestamp).build()
        try {
            NotificationManagerCompat.from(context).notify("$userId:${notice.orderId}",0,notification)
            prefs.edit().putStringSet(key,(delivered + notice.id).toList().takeLast(500).toSet()).apply()
        } catch (_: SecurityException) {
            // Permission may change while posting; updates remain in the app.
        }
    }
}
