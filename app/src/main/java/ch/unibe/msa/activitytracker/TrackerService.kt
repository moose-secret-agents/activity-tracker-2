package ch.unibe.msa.activitytracker

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import org.jetbrains.anko.*

public class TrackerService : Service(), AnkoLogger, ConnectionCallbacks, OnConnectionFailedListener {

    var gApiClient: GoogleApiClient? = null
    var locRequest: LocationRequest? = null
    val locListener = LocListener()
    val sender: Sender by lazy { Sender(defaultSharedPreferences.getString("url", "localhost")) }

    override fun onBind(intent: Intent?): IBinder? {
        info("Bound to service with intent $intent")
        return null
    }

    override fun onCreate() {
        super.onCreate()
        info("TrackerService created")

        // Create a location request
        locRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)       // 10 seconds, in milliseconds
                .setFastestInterval(1 * 1000) // 1 second, in milliseconds
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        info("TrackerService started")

        if (intent == null) {
            // Android OS killed our Service and started it again because of
            info("Service was killed by Android")
        }

        // Connect to play services
        if (isPlayServiceAvailable()) {
            gApiClient = GoogleApiClient.Builder(this)
                    .addApi(ActivityRecognition.API)
                    .addApi(LocationServices.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build()

            //Connect to Google API
            gApiClient?.connect()
        }

        return Service.START_STICKY_COMPATIBILITY
    }

    override fun onDestroy() {
        super.onDestroy()
        info("TrackerService destroyed")
        stopTracking()
    }

    private fun isPlayServiceAvailable(): Boolean {
        return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this) == ConnectionResult.SUCCESS
    }

    override fun onConnected(p0: Bundle?) {
        info("Connection established")
        startTracking()
    }

    override fun onConnectionSuspended(p0: Int) {
        info("Connection suspended")
    }

    override fun onConnectionFailed(p0: ConnectionResult?) {
        info("Connection Failed with $p0")
    }

    private fun startTracking() {
        // Initiate activity tracking
        val intent = intentFor<ActivityRecognitionService>()
        val callbackIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        ActivityRecognition.ActivityRecognitionApi.requestActivityUpdates(gApiClient, 0, callbackIntent)

        // Initiate location tracking
        LocationServices.FusedLocationApi.requestLocationUpdates(gApiClient, locRequest, locListener)
    }

    private fun stopTracking() {
        // Stop activity tracking
        val intent = intentFor<ActivityRecognitionService>()
        val callbackIntent = PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        // Wait while the api client is not connected, fixes error when pressing start/stop button very fast
        //do { } while (!(gApiClient?.isConnected ?: false))
        ActivityRecognition.ActivityRecognitionApi.removeActivityUpdates(gApiClient, callbackIntent)

        // Stop location tracking
        LocationServices.FusedLocationApi.removeLocationUpdates(gApiClient, locListener)

        // Disconnect GMS Client
        gApiClient?.disconnect()
    }

    inner class LocListener : LocationListener {
        override fun onLocationChanged(location: Location?) {
            val latitude = location?.latitude ?: 0.0
            val longitude = location?.longitude ?: 0.0

            info("New Location found: $latitude, $longitude")
            async { sender.send(Data.Location(latitude = latitude, longitude = longitude)) }

            notifyOthers(latitude, longitude)
        }

        fun notifyOthers(latitude: Double, longitude: Double) {
            // Notify MainActivity about new activity via broadcast
            val bcIntent = Intent(Constants.ACTION_NEW_LOCATION)
                    .putExtra("latitude", latitude)
                    .putExtra("longitude", longitude)
            sendBroadcast(bcIntent)
        }
    }
}
