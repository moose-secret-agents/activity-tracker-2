package ch.unibe.msa.activitytracker

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Bundle
import android.os.IBinder
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.util.Base64
import com.github.salomonbrys.kotson.addAll
import com.goebl.david.Webb
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.common.api.GoogleApiClient.ConnectionCallbacks
import com.google.android.gms.common.api.GoogleApiClient.OnConnectionFailedListener
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.gson.JsonArray
import org.jetbrains.anko.*

public class TrackerService : Service(), AnkoLogger, ConnectionCallbacks, OnConnectionFailedListener {

    var gApiClient: GoogleApiClient? = null
    var locRequest: LocationRequest? = null
    val locListener = LocListener()
    //val client = Webb.create()
    val sender = Sender("192.168.1.114:3000/api/v1/2/dataPoint","sweattoscoretest","test") //Change to local IP

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

        gApiClient = GoogleApiClient.Builder(this)
                .addApi(ActivityRecognition.API)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        info("TrackerService started")

        if (intent == null) {
            // Android OS killed our Service and started it again because of
            info("Service was killed by Android")
        }

        // Connect to play services
        if (isPlayServiceAvailable()) {
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

        showNotification()
    }

    private fun stopTracking() {
        if (!(gApiClient?.isConnected ?: false)) return

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

        cancelNotification()
    }

    private fun showNotification() {
        val i = intentFor<MainActivity>()
        val pi = PendingIntent.getActivity(this, 1, i, Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT)

        val notification = NotificationCompat.Builder(this)
                .setContentTitle("Activity Tracker")
                .setContentText("Activity Tracker is tracking your activity")
                .setSmallIcon(R.drawable.ic_stat_maps_directions_walk)
                .setOngoing(true)
                .addAction(android.R.drawable.ic_media_pause, "Stop Tracking", pi)
                .setColor(ContextCompat.getColor(this, R.color.colorAccent))
                .setContentIntent(pi)
                .build()

        notificationManager.notify(Constants.NOTIFICATION_ID, notification)
    }

    private fun cancelNotification() {
        notificationManager.cancel(Constants.NOTIFICATION_ID)
    }

    inner class LocListener : LocationListener {
        override fun onLocationChanged(location: Location?) {
            val latitude = location?.latitude ?: 0.0
            val longitude = location?.longitude ?: 0.0
            val elevation = location?.altitude ?: 0.0

            info("New Location found: $latitude, $longitude")

            var response = ""
            async {
                response = sender.send(Data.Location(latitude = latitude, longitude = longitude),
                        Data.Activity(activity = defaultSharedPreferences.getString("ACTIVITY", "UNKNOWN"),
                                confidence = defaultSharedPreferences.getInt("ACTIVITY_CONFIDENCE", 100)), Data.User(defaultSharedPreferences.getString("username", "")))
            }

                /*send("192.168.43.155:3000/api/v1/dataPoint",Data.Location(latitude = latitude, longitude = longitude),
                        Data.Activity(activity = defaultSharedPreferences.getString("ACTIVITY","UNKNOWN"),
                                confidence = defaultSharedPreferences.getInt("ACTIVITY_CONFIDENCE",100)),Data.User(defaultSharedPreferences.getString("username","")))*/


            notifyOthers(latitude, longitude, elevation)
            notifyOthers(response)

        }

        fun notifyOthers(latitude: Double, longitude: Double, elevation: Double) {
            // Notify MainActivity about new activity via broadcast
            val bcIntent = Intent(Constants.ACTION_NEW_LOCATION)
                    .putExtra("latitude", latitude)
                    .putExtra("longitude", longitude)
                    .putExtra("elevation", elevation)
            sendBroadcast(bcIntent)
        }
        fun notifyOthers(response: String) {
            // Notify MainActivity about new activity via broadcast
            val bcIntent = Intent(Constants.ACTION_RESPONSE)
                    .putExtra("response", response)
            sendBroadcast(bcIntent)
        }
    }




    /*fun send(data: String, uri: String) {
        val actualUri = "http://$uri"
        println("Sending data to $actualUri: $data")
        var encodedCredentials = "Basic " + Base64.encodeToString(
                ("sweattoscoretest" + ":" + "test").toByteArray(),
                Base64.NO_WRAP);
        this.notifyOthers(client.post(actualUri).param("data", data).header("Authorization",encodedCredentials).asString().statusLine.toString())
    }

    fun send(uri: String, vararg sendables: Sendable) {
        val json = JsonArray()
        json.addAll(sendables.map { it.Data })
        send(json.toString(), uri)
    }
    fun notifyOthers(response: String) {
        // Notify MainActivity about new activity via broadcast
        val bcIntent = Intent(Constants.ACTION_RESPONSE)
                .putExtra("response", response)
        sendBroadcast(bcIntent)
    }*/

}
