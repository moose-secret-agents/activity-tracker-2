package ch.unibe.msa.activitytracker

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatTextView
import android.support.v7.widget.Toolbar
import android.util.JsonReader
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import org.jetbrains.anko.*
import org.json.JSONObject
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.*
import java.util.Formatter


class MainActivity : AppCompatActivity(), AnkoLogger {

    val intentFilter = IntentFilter()
    var receiver: BroadcastReceiver? = null

    init {
        // Listen for broadcast Actions from Location and Activity services
        intentFilter.addAction(Constants.ACTION_NEW_ACTIVITY)
        intentFilter.addAction(Constants.ACTION_NEW_LOCATION)
        intentFilter.addAction(Constants.ACTION_RESPONSE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = find<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val fab = find<FloatingActionButton>(R.id.fab)
        // If service was started before activity, set FAB icon to pause
        if (isTracking()) fab.image = resources.getDrawable(android.R.drawable.ic_media_pause, theme)

        fab.onClick { view ->
            if (isTracking()) {
                //fab.image = resources.getDrawable(android.R.drawable.ic_media_play, theme)
                fab.image = resources.getDrawable(android.R.drawable.ic_media_play, theme);

                stopTrackingService()
                var sender = Sender("${Constants.BASE_URL}/api/v1/TrainingSession",
                        defaultSharedPreferences.getString("username", ""),defaultSharedPreferences.getString("password", ""))
                sender.sessionID = defaultSharedPreferences.getString("sessionID","0").toInt()
                async {
                    sender.concludeSession()
                }
            } else {
                fab.image = resources.getDrawable(android.R.drawable.ic_media_pause, theme);
                var sender = Sender("${Constants.BASE_URL}/api/v1/TrainingSession",
                        defaultSharedPreferences.getString("username", ""),defaultSharedPreferences.getString("password", ""))

                startTracking()
            }
        }

        receiver = broadcastReceiver { context, intent ->
            if (intent == null) return@broadcastReceiver

            if (intent.action.equals(Constants.ACTION_NEW_ACTIVITY)) {
                val activity = intent.extras.getString("activity")
                val confidence = intent.extras.getInt("confidence")

                addToHistory("New Activity: $activity @ $confidence%")
            } else if (intent.action.equals(Constants.ACTION_NEW_LOCATION)) {
                val latitude = intent.extras.getDouble("latitude")
                val longitude = intent.extras.getDouble("longitude")
                val elevation = intent.extras.getDouble("elevation")

                addToHistory("New Location: $latitude, $longitude @ ${elevation}m")
            } else if (intent.action.equals(Constants.ACTION_RESPONSE)){
                val resp = intent.extras.getString("response")
                //addToHistory(resp)
                if(resp == "UNAUTHORIZED"){
                    stopTrackingService()
                    val fab = find<FloatingActionButton>(R.id.fab)
                    fab.image = resources.getDrawable(android.R.drawable.ic_media_play, theme)
                    find<LinearLayout>(R.id.background).setBackgroundColor(Color.RED);
                    toast("User Credentials Invalid, tracking stopped")
                    return@broadcastReceiver
                }
                else{
                    find<LinearLayout>(R.id.background).setBackgroundColor(Color.WHITE);
                }
                val json = JSONObject(resp)
                find<AppCompatTextView>(R.id.view_activity).setText(json.getString("activity"))
                var totalSecs = json.getString("duration").toDouble()
                var hours = Math.floor(totalSecs / 3600)
                var minutes = Math.floor((totalSecs % 3600) / 60)
                var seconds = totalSecs % 60
                var formatter = Formatter()
                val timeString = formatter.format("%02.0f:%02.0f:%02.0f", hours, minutes, seconds);
                find<AppCompatTextView>(R.id.view_duration).setText(timeString.toString())

                formatter = Formatter()
                val avgSpeed = formatter.format("%.1f km/h", json.getString("avg_speed").toDouble()*3.6);
                find<AppCompatTextView>(R.id.view_avg_speed).setText(avgSpeed.toString())

                formatter = Formatter()
                val speed = formatter.format("%.1f km/h", json.getString("current_speed").toDouble()*3.6);
                find<AppCompatTextView>(R.id.view_speed).setText(speed.toString())

                formatter = Formatter()
                val elevation = formatter.format("%.1f m", json.getString("elevation_gain").toDouble());
                find<AppCompatTextView>(R.id.view_elevation).setText(elevation.toString())

                formatter = Formatter()
                val distance = formatter.format("%.1f m", json.getString("distance").toDouble());
                find<AppCompatTextView>(R.id.view_distance).setText(distance.toString())

            }
        }

        find<Button>(R.id.btn_save_username).onClick {
            val username = find<EditText>(R.id.ed_username).text.toString()
            val password = find<EditText>(R.id.ed_password).text.toString()
            defaultSharedPreferences.edit().putString("username", username).putString("password",password).commit()
            toast("Credentials saved")
            find<LinearLayout>(R.id.background).setBackgroundColor(Color.WHITE);
        }

        registerReceiver(receiver, intentFilter)
    }

    override fun onStart() {
        super.onStart()
        find<EditText>(R.id.ed_username).setText(defaultSharedPreferences.getString("username", ""))
        find<EditText>(R.id.ed_password).setText(defaultSharedPreferences.getString("password", ""))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_settings -> return true
            R.id.action_clear_history -> {
                find<AppCompatTextView>(R.id.txt_content).text = ""
                return true
            }
            else -> return false
        }
    }

    fun isTracking(): Boolean {
        val names = activityManager.getRunningServices(Int.MAX_VALUE).map { it.service.className }
        val serviceName = TrackerService::class.java.toString()
        return names.any { it.equals(serviceName.replace("class ", "")) }
    }

    fun startTracking() {
        if (checkPermissions()) {
            startTrackingService()
        }
    }

    fun startTrackingService() {
        info("Starting Tracking Service")
        startService(intentFor<TrackerService>())

        addToHistory("Tracking started")
    }

    fun stopTrackingService() {
        info("Stopping Tracking Service")
        stopService(intentFor<TrackerService>())

        addToHistory("Tracking stopped")
    }

    fun addToHistory(text: String) {
        find<AppCompatTextView>(R.id.txt_content).append("${Date().format("HH:mm:ss")}: $text\n")

        // Scroll to bottom to show newest entry
        val scrollView = find<ScrollView>(R.id.scrl_main)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }

    fun checkPermissions(): Boolean {
        val deniedPermissions = listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE)
                .filter { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_DENIED }
                .toTypedArray()
        
        if (deniedPermissions.any()) {
            ActivityCompat.requestPermissions(this, deniedPermissions, Constants.PERMISSION_REQUEST_ID)
            return false
        }
        return true
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>?, grantResults: IntArray?) {
        if (grantResults == null) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            return
        }

        if (requestCode == Constants.PERMISSION_REQUEST_ID) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                toast("Permission Granted")
                startTrackingService()
            } else {
                toast("Some Permission NOT Granted")
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
