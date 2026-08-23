package ir.keyvanadili.noghteyab.service

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import ir.keyvanadili.noghteyab.R
import ir.keyvanadili.noghteyab.data.AppDatabase
import ir.keyvanadili.noghteyab.data.TrackPointEntity
import ir.keyvanadili.noghteyab.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
    }

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        LocationRepository.setTracking(true)

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
            .setMinUpdateIntervalMillis(1500L)
            .build()

        callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                LocationRepository.update(location)

                val trackId = LocationRepository.currentTrackId.value
                if (LocationRepository.isRecording.value && trackId != null) {
                    serviceScope.launch {
                        val dao = AppDatabase.getInstance(applicationContext).trackDao()
                        dao.insertPoint(
                            TrackPointEntity(
                                trackId = trackId,
                                latitude = location.latitude,
                                longitude = location.longitude,
                                timestamp = System.currentTimeMillis()
                            )
                        )
                    }
                }
            }
        }
        fusedClient.requestLocationUpdates(request, callback!!, mainLooper)

        return START_STICKY
    }

    override fun onDestroy() {
        callback?.let { fusedClient.removeLocationUpdates(it) }
        LocationRepository.setTracking(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        val channelId = "location_tracking"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "ردیابی موقعیت", NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("نقطه‌یاب فعال است")
            .setContentText("در حال دریافت موقعیت مکانی...")
            .setSmallIcon(R.drawable.ic_location)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val NOTIF_ID = 42
    }
}
