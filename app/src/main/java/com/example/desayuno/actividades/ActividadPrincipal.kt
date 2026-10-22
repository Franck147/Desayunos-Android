package com.example.desayuno.actividades

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.desayuno.notification.NotificationHelper
import com.example.desayuno.ui.DesayunoApp
import com.example.desayuno.ui.DesayunoViewModel

class ActividadPrincipal : ComponentActivity() {

    private val appViewModel: DesayunoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        NotificationHelper.createNotificationChannel(this)
        openNotification(intent)

        setContent {
            DesayunoApp(appViewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openNotification(intent)
    }

    private fun openNotification(intent: Intent?) {
        val order=intent?.getStringExtra(NotificationHelper.EXTRA_ORDER_ID) ?: return
        val owner=intent.getStringExtra(NotificationHelper.EXTRA_USER_ID) ?: return
        appViewModel.requestOrderOpen(order,owner)
    }
}
