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
import ir.keyvanadili.noghteyab.util.TrackUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private lateinit var fusedClient: FusedLocationProviderClient
    private var callback: LocationCallback? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // State used to filter GPS noise out of recorded tracks.
    private var recordingSessionTrackId: Long? = null
    private var lastRecordedLat: Double? = null
    private var lastRecordedLng: Double? = null
    private var lastRecordedTime: Long = 0L

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
                if (LocationRepository.isRecording.value && trackId != null && shouldRecordPoint(location, trackId)) {
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

    /**
     * Filters out GPS noise so recorded tracks come out smooth instead of jagged:
     * - drops low-accuracy fixes (common near tall buildings / indoors)
     * - drops fixes too close to the last recorded point (jitter while stationary/slow)
     * - but always records at least every [MAX_STATIONARY_INTERVAL_MS] so real stops still show up
     */
    private fun shouldRecordPoint(location: android.location.Location, trackId: Long): Boolean {
        if (recordingSessionTrackId != trackId) {
            // New recording session: reset filter state.
            recordingSessionTrackId = trackId
            lastRecordedLat = null
            lastRecordedLng = null
            lastRecordedTime = 0L
        }

        if (location.hasAccuracy() && location.accuracy > MIN_ACCURACY_METERS) {
            return false
        }

        val now = System.currentTimeMillis()
        val prevLat = lastRecordedLat
        val prevLng = lastRecordedLng

        val movedEnough = if (prevLat != null && prevLng != null) {
            TrackUtil.haversineMeters(
                prevLat, prevLng, location.latitude, location.longitude
            ) >= MIN_DISTANCE_METERS
        } else {
            true
        }
        val longEnoughSinceLastPoint = (now - lastRecordedTime) >= MAX_STATIONARY_INTERVAL_MS

        if (movedEnough || longEnoughSinceLastPoint) {
            lastRecordedLat = location.latitude
            lastRecordedLng = location.longitude
            lastRecordedTime = now
            return true
        }
        return false
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
        private const val MIN_ACCURACY_METERS = 25f
        private const val MIN_DISTANCE_METERS = 8.0
        private const val MAX_STATIONARY_INTERVAL_MS = 30_000L
    }
}
