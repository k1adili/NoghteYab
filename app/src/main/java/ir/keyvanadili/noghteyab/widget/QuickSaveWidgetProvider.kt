package ir.keyvanadili.noghteyab.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import ir.keyvanadili.noghteyab.R
import ir.keyvanadili.noghteyab.data.AppDatabase
import ir.keyvanadili.noghteyab.data.GeoPoint
import ir.keyvanadili.noghteyab.ui.NameEntryActivity
import ir.keyvanadili.noghteyab.util.LocationUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class QuickSaveWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_save)
            val intent = Intent(context, QuickSaveWidgetProvider::class.java).apply {
                action = ACTION_QUICK_SAVE
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_icon, pendingIntent)
            views.setOnClickPendingIntent(R.id.widget_label, pendingIntent)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_QUICK_SAVE) return

        if (!LocationUtil.hasLocationPermission(context)) {
            Toast.makeText(context, context.getString(R.string.location_permission_needed), Toast.LENGTH_LONG).show()
            val openApp = Intent(context, ir.keyvanadili.noghteyab.ui.MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(openApp)
            return
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val location = LocationUtil.getQuickLocation(context)
                if (location == null) {
                    withContextMain { Toast.makeText(context, context.getString(R.string.location_not_found), Toast.LENGTH_LONG).show() }
                    return@launch
                }

                val dao = AppDatabase.getInstance(context).geoPointDao()
                val point = GeoPoint(
                    name = "",
                    category = "",
                    latitude = location.latitude,
                    longitude = location.longitude,
                    timestamp = System.currentTimeMillis()
                )
                val newId = dao.insert(point)

                // Open the lightweight naming screen right away on top of whatever is on screen
                val nameIntent = Intent(context, NameEntryActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(NameEntryActivity.EXTRA_POINT_ID, newId)
                }
                context.startActivity(nameIntent)

                showSavedNotification(context)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun withContextMain(block: () -> Unit) {
        kotlinx.coroutines.withContext(Dispatchers.Main) { block() }
    }

    private fun showSavedNotification(context: Context) {
        val channelId = "quick_save"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                channelId, "ذخیره سریع", android.app.NotificationManager.IMPORTANCE_DEFAULT
            )
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager)
                .createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(context.getString(R.string.point_saved))
            .setContentText(context.getString(R.string.tap_to_name_it))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(SAVED_NOTIF_ID, notification)
    }

    companion object {
        const val ACTION_QUICK_SAVE = "ir.keyvanadili.noghteyab.ACTION_QUICK_SAVE"
        const val SAVED_NOTIF_ID = 77
    }
}
