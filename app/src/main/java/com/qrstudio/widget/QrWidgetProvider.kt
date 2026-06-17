package com.qrstudio.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.widget.RemoteViews
import com.qrstudio.MainActivity
import com.qrstudio.R
import com.qrstudio.core.data.HistoryItem
import com.qrstudio.core.data.HistoryRepository
import com.qrstudio.core.qr.QrEncoder

/**
 * Home-screen widget showing the most recently pinned history entry as a QR
 * code, so a favourite payload (home Wi-Fi, contact card…) is one glance away.
 * Classic RemoteViews — no Glance dependency, per project conventions.
 */
class QrWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        render(context, appWidgetManager, appWidgetIds)
    }

    companion object {

        // Kept modest: RemoteViews bitmaps travel through binder IPC.
        private const val QR_SIZE_PX = 512

        /**
         * The entry the widget shows: the most recently *pinned* one (several
         * entries can be pinned for sorting; pinnedAt disambiguates).
         */
        fun pinnedEntry(items: List<HistoryItem>): HistoryItem? =
            items.filter { it.pinned }.maxByOrNull { it.pinnedAt ?: it.timestamp }

        /** Re-renders every placed widget; no-op when none is on the home screen. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, QrWidgetProvider::class.java))
            if (ids.isNotEmpty()) render(context, manager, ids)
        }

        // Runs on the receiver's main thread; fine at current volumes (one JSON
        // read + one ~512 px encode), revisit with goAsync() if it ever grows.
        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            // Idempotent: covers the widget being rendered before the app ran.
            HistoryRepository.init(context)
            val item = pinnedEntry(HistoryRepository.items.value)
            val views = RemoteViews(context.packageName, R.layout.widget_qr)

            val bitmap = item?.let {
                val style = QrEncoder.styleFor(
                    type = it.type,
                    foregroundArgb = it.foregroundArgb ?: Color.BLACK,
                    sizePx = QR_SIZE_PX
                )
                (QrEncoder.encode(it.content, style) as? QrEncoder.Result.Success)?.bitmap
            }

            if (item != null && bitmap != null) {
                views.setViewVisibility(R.id.widget_qr_image, View.VISIBLE)
                views.setViewVisibility(R.id.widget_qr_label, View.VISIBLE)
                views.setViewVisibility(R.id.widget_qr_empty, View.GONE)
                views.setImageViewBitmap(R.id.widget_qr_image, bitmap)
                views.setTextViewText(
                    R.id.widget_qr_label,
                    item.label?.takeIf { it.isNotBlank() } ?: item.type.frenchLabel
                )
            } else {
                views.setViewVisibility(R.id.widget_qr_image, View.GONE)
                views.setViewVisibility(R.id.widget_qr_label, View.GONE)
                views.setViewVisibility(R.id.widget_qr_empty, View.VISIBLE)
            }

            val launch = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, launch)
            ids.forEach { manager.updateAppWidget(it, views) }
        }
    }
}
