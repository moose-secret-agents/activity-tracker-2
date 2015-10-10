package ch.unibe.msa.activitytracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.*

// Create Broadcast Receiver with the specified block as onReceive callback
public fun broadcastReceiver(init: (Context, Intent?) -> Unit): BroadcastReceiver {
    return object : BroadcastReceiver() {
        public override fun onReceive(context: Context, intent: Intent?) {
            init(context, intent)
        }
    }
}

fun Date.format(format: String = "yyyy-MM-dd HH:mm:ss"): String {
    val formatter = SimpleDateFormat(format)
    return formatter.format(this)
}

fun Date.toJsonDate(): String = format("yyyy-MM-dd'T'HH:mm:ss.SSS")