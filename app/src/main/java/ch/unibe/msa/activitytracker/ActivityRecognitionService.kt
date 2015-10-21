package ch.unibe.msa.activitytracker

import android.app.IntentService
import android.content.Intent
import com.google.android.gms.location.ActivityRecognitionResult;
import com.google.android.gms.location.DetectedActivity;
import org.jetbrains.anko.*

class ActivityRecognitionService : IntentService("activity-rec-service"), AnkoLogger {

    override fun onCreate() {
        super.onCreate()
    }

    override fun onHandleIntent(intent: Intent?) {
        if (ActivityRecognitionResult.hasResult(intent)) {
            val result = ActivityRecognitionResult.extractResult(intent)
            val mostProbableActivity = result.mostProbableActivity

            val activityText = mostProbableActivity.getActivityString()
            val confidence = mostProbableActivity.confidence

            info("Activity detected: $activityText ($confidence)")


            defaultSharedPreferences.edit().putString("ACTIVITY", activityText).putInt("ACTIVITY_CONFIDENCE", confidence).commit()

            /*async {
                val sender = Sender(defaultSharedPreferences.getString("url", "localhost"))
                sender.send(Data.Activity(activity = activityText, confidence = confidence))
            }*/

            notifyOthers(activityText, confidence)
        }
    }

    private fun notifyOthers(activityText: String, confidence: Int) {
        // Notify MainActivity about new activity via broadcast
        val bcIntent = Intent(Constants.ACTION_NEW_ACTIVITY)
                .putExtra("activity", activityText)
                .putExtra("confidence", confidence)

        sendBroadcast(bcIntent)
    }

    fun DetectedActivity.getActivityString(): String {
        return when (this.type) {
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            else -> "NOT_RECOGNIZED"
        }
    }
}