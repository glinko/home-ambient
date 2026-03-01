package cv.rootnode.ambient

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlin.concurrent.thread

object EventUploader {
    @Volatile
    var ingestUrl: String = "http://192.168.88.50:8070/v1/events"

    fun sendMotionEvent(deviceId: String, motionScore: Double) {
        thread(start = true) {
            try {
                val payload = JSONObject().apply {
                    put("id", "evt-" + UUID.randomUUID())
                    put("device_id", deviceId)
                    put("ts", Instant.now().toString())
                    put("motion_score", motionScore)
                    put("metadata", JSONObject().apply {
                        put("source", "android-agent")
                        put("type", "motion")
                    })
                }

                val conn = (URL(ingestUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3000
                    readTimeout = 3000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                conn.inputStream.close()
                conn.disconnect()
            } catch (_: Exception) {
            }
        }
    }
}
