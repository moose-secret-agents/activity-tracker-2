package ch.unibe.msa.activitytracker

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatTextView
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import org.jetbrains.anko.*
import java.util.*

class MainActivity : AppCompatActivity(), AnkoLogger {

    val intentFilter = IntentFilter()
    var receiver: BroadcastReceiver? = null

    init {
        // Listen for broadcast Actions from Location and Activity services
        intentFilter.addAction(Constants.ACTION_NEW_ACTIVITY)
        intentFilter.addAction(Constants.ACTION_NEW_LOCATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = find<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        val fab = find<FloatingActionButton>(R.id.fab)
        // If service was started before activity, set FAB icon to pause
        if (isTracking()) fab.setImageDrawable(resources.getDrawable(android.R.drawable.ic_media_pause, theme))

        fab.onClick { view ->
            if (isTracking()) {
                fab.setImageDrawable(resources.getDrawable(android.R.drawable.ic_media_play, theme))
                stopTrackingService()
            } else {
                fab.setImageDrawable(resources.getDrawable(android.R.drawable.ic_media_pause, theme))
                startTrackingService()
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

                addToHistory("New Location: $latitude, $longitude")
            }
        }

        find<Button>(R.id.btn_save_url).onClick {
            val url = find<EditText>(R.id.ed_url).text.toString()
            defaultSharedPreferences.edit().putString("url", url).commit()
        }

        registerReceiver(receiver, intentFilter)
    }

    override fun onStart() {
        super.onStart()
        find<EditText>(R.id.ed_url).setText(defaultSharedPreferences.getString("url", "localhost:3000"))
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.itemId

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            info("Opening Settings")
            return true
        }
        if (id == R.id.action_clear_history) {
            find<AppCompatTextView>(R.id.txt_content).text = ""
        }

        return super.onOptionsItemSelected(item)
    }

    fun isTracking(): Boolean {
        val names = activityManager.getRunningServices(Int.MAX_VALUE).map { it.service.className }
        val serviceName = TrackerService::class.java.toString()
        return names.any { it.equals(serviceName.replace("class ", "")) }
    }

    fun startTrackingService() {
        info("Starting Tracking Service")
        startService(intentFor<TrackerService>())

        addToHistory("Tracking started")
        toast("Tracking started")
    }

    fun stopTrackingService() {
        info("Stopping Tracking Service")
        stopService(intentFor<TrackerService>())

        addToHistory("Tracking stopped")
        toast("Tracking stopped")
    }

    fun addToHistory(text: String) {
        find<AppCompatTextView>(R.id.txt_content).append("${Date().format("HH:mm:ss")}: $text\n")

        // Scroll to bottom to show newest entry
        val scrollView = find<ScrollView>(R.id.scrl_main)
        scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
    }
}
