package cv.rootnode.ambient.legacy

import android.app.Activity
import android.hardware.Camera
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.TextView
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.io.StringWriter
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

        // Legacy Camera API compatibility hint for very old devices/ROMs
        @Suppress("DEPRECATION")
        surface.holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        val cams = try { Camera.getNumberOfCameras() } catch (_: Exception) { -1 }
        status.text = "Legacy agent ready | sdk=${Build.VERSION.SDK_INT} | cams=$cams"
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        try {
            val cams = Camera.getNumberOfCameras()
            if (cams <= 0) {
                status.text = "Camera error: no cameras detected"
                return
            }

            val backId = findBackCameraId()
            camera = if (backId >= 0) Camera.open(backId) else Camera.open()

            camera?.apply {
                try {
                    setDisplayOrientation(90)
                } catch (_: Exception) {}

                try {
                    val p = parameters
                    val size = p.supportedPreviewSizes?.firstOrNull()
                    if (size != null) {
                        p.setPreviewSize(size.width, size.height)
                    }
                    parameters = p
                } catch (_: Exception) {}

                setPreviewDisplay(holder)
                setPreviewCallback(this@MainActivity)
                startPreview()
            }

            val info = if (backId >= 0) "back=$backId" else "default camera"
            status.text = "Camera started ($info)"
        } catch (e: Throwable) {
            status.text = formatError("camera_start", e)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopCamera()
    }

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
                    put("metadata", JSONObject().apply {
                        put("source", "android-legacy")
                        put("type", "motion")
                    })
                }
                val conn = (URL(ingestUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 4000
                    readTimeout = 4000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                val code = conn.responseCode
                conn.inputStream.close()
                conn.disconnect()
                runOnUiThread { status.text = "Motion sent OK (HTTP $code)" }
            } catch (e: Throwable) {
                runOnUiThread { status.text = formatError("send_event", e) }
            }
        }
    }

    private fun stopCamera() {
        try {
            camera?.setPreviewCallback(null)
            camera?.stopPreview()
            camera?.release()
        } catch (_: Exception) {
        } finally {
            camera = null
        }
    }

    private fun findBackCameraId(): Int {
        return try {
            val info = Camera.CameraInfo()
            for (i in 0 until Camera.getNumberOfCameras()) {
                Camera.getCameraInfo(i, info)
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) return i
            }
            -1
        } catch (_: Exception) {
            -1
        }
    }

    private fun formatError(stage: String, e: Throwable): String {
        val cls = e.javaClass.simpleName
        val msg = e.message ?: "<no-message>"
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val first = sw.toString().lineSequence().drop(1).firstOrNull() ?: ""
        return "ERR[$stage] $cls: $msg\n$first"
    }

    override fun onDestroy() {
        stopCamera()
        super.onDestroy()
    }
}
