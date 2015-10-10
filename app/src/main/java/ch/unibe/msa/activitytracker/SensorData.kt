package ch.unibe.msa.activitytracker

import com.github.salomonbrys.kotson.*
import com.goebl.david.Webb
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.util.*

interface Sendable {
    val Data: JsonObject
    val Type: String
}

class Data {
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
}

class Sender(val uri: String) {
    companion object  {
        val client = Webb.create()
    }

    fun send(data: String) {
        println("Sending data to $uri: $data")
        client.post(uri).param("data", data)
    }

    fun send(vararg sendables: Sendable) {
        val json = JsonArray()
        json.addAll(sendables.map { it.Data })
        send(json.toString())
    }
}
