package cv.rootnode.ambient.legacy

import android.app.Activity
import android.hardware.Camera
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.math.abs

class MainActivity : Activity(), SurfaceHolder.Callback, Camera.PreviewCallback {
    private lateinit var surface: SurfaceView
    private lateinit var status: TextView
    private var camera: Camera? = null
    private var prevAvg = -1.0
    private var lastSent = 0L
    private val ingestUrl = "http://192.168.88.50:8070/v1/events"
    private val deviceId = "android-kindle-legacy"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        surface = findViewById(R.id.cameraSurface)
        status = findViewById(R.id.statusText)
        surface.holder.addCallback(this)
        status.text = "Legacy agent ready (Android 4.4 mode)"
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            camera = Camera.open().apply {
                setPreviewDisplay(holder)
                setPreviewCallback(this@MainActivity)
                startPreview()
            }
            status.text = "Camera started"
        } catch (e: Exception) {
            status.text = "Camera error: ${e.message}"
        }
    }
    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
    override fun surfaceDestroyed(holder: SurfaceHolder) { stopCamera() }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        if (data == null || data.isEmpty()) return
        var sum = 0L
        var cnt = 0
        var i = 0
        while (i < data.size) {
            sum += (data[i].toInt() and 0xFF)
            cnt++
            i += 32
        }
        val avg = if (cnt > 0) sum.toDouble() / cnt else 0.0
        val motion = if (prevAvg < 0) 0.0 else abs(avg - prevAvg) / 255.0
        prevAvg = avg
        val now = System.currentTimeMillis()
        if (motion > 0.07 && now - lastSent > 7000) {
            lastSent = now
            status.text = "Motion ${"%.3f".format(motion)} / sending"
            sendEvent(motion)
        }
    }

    private fun sendEvent(motion: Double) {
        thread {
            try {
                val payload = JSONObject().apply {
                    put("id", "evt-" + UUID.randomUUID())
                    put("device_id", deviceId)
                    put("ts", Instant.now().toString())
                    put("motion_score", motion)
                    put("metadata", JSONObject().apply { put("source", "android-legacy"); put("type", "motion") })
                }
                val conn = (URL(ingestUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 4000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                conn.inputStream.close(); conn.disconnect()
                runOnUiThread { status.text = "Motion sent OK" }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Send fail: ${e.javaClass.simpleName}" }
            }
        }
    }

    private fun stopCamera() {
        camera?.setPreviewCallback(null)
        camera?.stopPreview()
        camera?.release()
        camera = null
    }

    override fun onDestroy() {
        stopCamera()
        super.onDestroy()
    }
}
