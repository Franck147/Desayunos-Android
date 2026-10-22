package com.example.desayuno

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.desayuno.notification.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Run only on the development emulator; no real orders or network calls. */
@RunWith(AndroidJUnit4::class)
class NotificationDeliveryTest {
    @Test fun phoneNotificationHasDestinationAndPersistsDeduplication() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val permission=Manifest.permission.POST_NOTIFICATIONS
        val user="notification-test-user"
        val notice=AppNotice("order:test-order:READY","Tu desayuno está listo","Pedido #9090 · Acércate al local.",System.currentTimeMillis(),NoticeKind.ORDER,"test-order",true)
        val manager=context.getSystemService(NotificationManager::class.java)
        val prefs=context.getSharedPreferences("notification_delivery",Context.MODE_PRIVATE)
        fun matching()=manager.activeNotifications.filter { it.tag=="$user:test-order" }
        prefs.edit().remove("delivered_$user").commit()
        try {
            if(Build.VERSION.SDK_INT>=33) {
                instrumentation.uiAutomation.grantRuntimePermission(context.packageName,permission)
            }
            assertTrue(NotificationHelper.enabled(context))
            NotificationHelper.show(context,user,notice)
            val deadline=SystemClock.uptimeMillis()+3000
            while(matching().isEmpty() && SystemClock.uptimeMillis()<deadline) SystemClock.sleep(30)
            val posted=matching().single().notification
            assertEquals(notice.title,posted.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(notice.message,posted.extras.getString(Notification.EXTRA_BIG_TEXT))
            assertEquals(Notification.VISIBILITY_PRIVATE,posted.visibility)
            assertNotNull(posted.smallIcon)
            assertNotNull(posted.contentIntent)
            assertTrue(prefs.getStringSet("delivered_$user",emptySet()).orEmpty().contains(notice.id))
            NotificationHelper.show(context,user,notice)
            assertEquals(1,matching().size)
        } finally {
            manager.cancel("$user:test-order",0)
            prefs.edit().remove("delivered_$user").commit()
        }
    }

    /** Invoke separately after revoking permission with adb while the app is stopped. */
    @Test fun deniedPermissionDoesNotPostOrConsumeTheAlert() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(Build.VERSION.SDK_INT>=33)
        assertFalse(NotificationHelper.enabled(context))
        val notice=AppNotice("denied-test","Aviso de prueba","Sin acceso a pedidos reales",System.currentTimeMillis(),NoticeKind.ORDER,"denied-order",true)
        val prefs=context.getSharedPreferences("notification_delivery",Context.MODE_PRIVATE)
        prefs.edit().remove("delivered_denied-user").commit()
        NotificationHelper.show(context,"denied-user",notice)
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.none { it.tag=="denied-user:denied-order" })
        assertFalse(prefs.contains("delivered_denied-user"))
    }
}
