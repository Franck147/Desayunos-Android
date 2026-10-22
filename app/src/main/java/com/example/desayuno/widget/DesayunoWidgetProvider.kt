package com.example.desayuno.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.desayuno.R
import com.example.desayuno.actividades.ActividadPrincipal
import com.example.desayuno.repository.DesayunoRepository

class DesayunoWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val repo = DesayunoRepository
            val menu = repo.menuState.value
            val announcements = repo.announcementsState.value
            val latestAnnouncement = announcements.firstOrNull()

            val views = RemoteViews(context.packageName, R.layout.widget_desayuno)

            val intent = Intent(context, ActividadPrincipal::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_announcement_title, pendingIntent)

            if (latestAnnouncement != null) {
                views.setTextViewText(R.id.widget_announcement_title, "📢 ${latestAnnouncement.title}")
                views.setTextViewText(R.id.widget_announcement_text, latestAnnouncement.message)
            } else {
                views.setTextViewText(R.id.widget_announcement_title, "📢 Desayunos Deliciosos")
                views.setTextViewText(R.id.widget_announcement_text, "¡Haz tu pedido en la app y recógelo sin colas!")
            }

            val stockText = if (menu.isEmpty()) "Abra la app para actualizar la disponibilidad" else
                "Carta: ${menu.count { it.isAvailable && it.availableStock > 0 }} productos disponibles · ${menu.sumOf { it.availableStock }} unidades"
            views.setTextViewText(R.id.widget_stock_info, stockText)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, DesayunoWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                for (id in appWidgetIds) {
                    updateAppWidget(context, appWidgetManager, id)
                }
            }
        }
    }
}
