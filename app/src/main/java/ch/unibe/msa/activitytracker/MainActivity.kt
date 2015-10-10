package ch.unibe.msa.activitytracker

import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Bundle
import android.support.design.widget.FloatingActionButton
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.AppCompatTextView
import android.support.v7.widget.Toolbar
import android.text.method.ScrollingMovementMethod
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ScrollView
import org.jetbrains.anko.*
import java.util.*

class MainActivity : AppCompatActivity(), AnkoLogger {

    val intentFilter = IntentFilter()
    var receiver: BroadcastReceiver? = null

    init {
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
            //Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG).setAction("Action", null).show()
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

        registerReceiver(receiver, intentFilter)
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
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun isTracking(): Boolean {
        val names = activityManager.getRunningServices(Int.MAX_VALUE).map { it.service.className }
        val serviceName = TrackerService::class.java.toString()
        return  names.any { it.equals(serviceName.replace("class ", "")) }
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