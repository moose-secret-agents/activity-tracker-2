package ch.unibe.msa.activitytracker

import android.content.Intent
import android.util.Base64
import com.github.salomonbrys.kotson.*
import com.goebl.david.Webb
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.jetbrains.anko.*
import java.util.*

interface Sendable {
    val Data: JsonObject
    val Type: String

}

class Data : AnkoLogger{
    data class Location(val timestamp: Date = Date(), val latitude: Double, val longitude: Double) : Sendable {
        override val Data: JsonObject
            get() = jsonObject("type" to Type, "timestamp" to timestamp.toJsonDate(), "latitude" to latitude, "longitude" to longitude)
        override val Type: String
            get() = "location"
    }

    data class Activity(val timestamp: Date = Date(), val activity: String, val confidence: Int) : Sendable {
        override val Data: JsonObject
            get() = jsonObject("type" to Type, "timestamp" to timestamp.toJsonDate(), "activity" to activity, "certainty" to confidence)
        override val Type: String
            get() = "activity"
    }
    data class User(val username: String) : Sendable {
        override val Type: String
            get() = "user"
        override val Data: JsonObject
            get() = jsonObject("username" to username);


    }
}

class Sender(val uri: String, val username: String, val password: String) {
    companion object  {
        val client = Webb.create()
    }
    init {
        val username = username
        val password = password
    }

    fun send(data: String): String{
        val actualUri = "http://$uri"
        println("Sending data to $actualUri: $data")
        var encodedCredentials = "Basic " + Base64.encodeToString(
        ("$username:$password").toByteArray(),
        Base64.NO_WRAP);
        return client.post(actualUri).param("data", data).header("Authorization",encodedCredentials).asString().statusLine.toString()

    }

    fun send(vararg sendables: Sendable) : String{
        val json = JsonArray()
        json.addAll(sendables.map { it.Data })
        return send(json.toString())
    }

}
