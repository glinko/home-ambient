package cv.rootnode.ambient.legacy

import android.app.Activity
import android.graphics.ImageFormat
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
    private var lastHeartbeatSent = 0L
    private var frameCount = 0
    private var lastFrameAt = 0L
    private val ingestUrl = "http://192.168.88.50:8070/v1/events"
    private val deviceId = "android-kindle-legacy"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        surface = findViewById(R.id.cameraSurface)
        status = findViewById(R.id.statusText)

        val holder = surface.holder
        holder.addCallback(this)
        @Suppress("DEPRECATION")
        holder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

        val cams = try { Camera.getNumberOfCameras() } catch (_: Exception) { -1 }
        status.text = "Legacy ready | sdk=${Build.VERSION.SDK_INT} | cams=$cams"
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        openAndStartCamera(holder, "surfaceCreated")
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        openAndStartCamera(holder, "surfaceChanged ${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        stopCamera()
    }

    private fun openAndStartCamera(holder: SurfaceHolder, stage: String) {
        try {
            if (camera == null) {
                val cams = Camera.getNumberOfCameras()
                if (cams <= 0) {
                    status.text = "Camera error: no cameras detected"
                    return
                }
                camera = try { Camera.open(0) } catch (_: Throwable) { Camera.open() }
            }

            camera?.apply {
                stopPreviewSafely()
                try { setPreviewCallbackWithBuffer(null) } catch (_: Exception) {}
                try { setPreviewCallback(null) } catch (_: Exception) {}

                val p = try { parameters } catch (_: Throwable) { null }
                if (p != null) {
                    try {
                        p.previewFormat = ImageFormat.NV21
                        val preferred = pickPreviewSize(p.supportedPreviewSizes)
                        if (preferred != null) p.setPreviewSize(preferred.width, preferred.height)
                        parameters = p
                    } catch (_: Exception) {}
                }

                try { setDisplayOrientation(90) } catch (_: Exception) {}
                setPreviewDisplay(holder)

                val pp = try { parameters } catch (_: Throwable) { null }
                val size = pp?.previewSize
                val fmt = pp?.previewFormat ?: ImageFormat.NV21
                if (size != null) {
                    val bits = ImageFormat.getBitsPerPixel(fmt).coerceAtLeast(12)
                    val bytes = size.width * size.height * bits / 8
                    addCallbackBuffer(ByteArray(bytes))
                    addCallbackBuffer(ByteArray(bytes))
                    setPreviewCallbackWithBuffer(this@MainActivity)
                } else {
                    setPreviewCallback(this@MainActivity)
                }

                startPreview()
            }

            val p2 = camera?.parameters
            val ps = p2?.previewSize
            status.text = "Camera started ($stage) | ${ps?.width}x${ps?.height} | wait frames..."

            val snapshotFrames = frameCount
            thread {
                Thread.sleep(4000)
                if (frameCount == snapshotFrames) {
                    runOnUiThread {
                        status.text = "ERR[preview] no frame callbacks after start (${stage})"
                    }
                }
            }
        } catch (e: Throwable) {
            status.text = formatError("camera_start/$stage", e)
        }
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
        frameCount++
        lastFrameAt = now

        if (frameCount % 20 == 0) {
            runOnUiThread {
                status.text = "Frames:$frameCount luma=${"%.1f".format(avg)} motion=${"%.3f".format(motion)}"
            }
        }

        if (motion > 0.04 && now - lastSent > 5000) {
            lastSent = now
            runOnUiThread { status.text = "Motion ${"%.3f".format(motion)} / sending" }
            sendEvent(motion, "motion")
        }

        try { camera?.addCallbackBuffer(data) } catch (_: Exception) {}
        // periodic heartbeat event so backend connectivity is always visible
        if (now - lastHeartbeatSent > 30000) {
            lastHeartbeatSent = now
            sendEvent(motion, "heartbeat")
        }
    }

    private fun sendEvent(motion: Double, type: String = "motion") {
        thread {
            try {
                val payload = JSONObject().apply {
                    put("id", "evt-" + UUID.randomUUID())
                    put("device_id", deviceId)
                    put("ts", Instant.now().toString())
                    put("motion_score", motion)
                    put("metadata", JSONObject().apply {
                        put("source", "android-legacy")
                        put("type", type)
                        put("frames", frameCount)
                        put("last_frame_age_ms", System.currentTimeMillis() - lastFrameAt)
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
                conn.inputStream.close(); conn.disconnect()
                runOnUiThread { status.text = "Motion sent OK (HTTP $code)" }
            } catch (e: Throwable) {
                runOnUiThread { status.text = formatError("send_event", e) }
            }
        }
    }

    private fun pickPreviewSize(sizes: List<Camera.Size>?): Camera.Size? {
        if (sizes.isNullOrEmpty()) return null
        return sizes.sortedBy { kotlin.math.abs(it.width * it.height - 640 * 480) }.firstOrNull()
    }

    private fun stopPreviewSafely() {
        try { camera?.stopPreview() } catch (_: Exception) {}
    }

    private fun stopCamera() {
        try {
            camera?.setPreviewCallbackWithBuffer(null)
            camera?.setPreviewCallback(null)
            camera?.stopPreview()
            camera?.release()
        } catch (_: Exception) {
        } finally {
            camera = null
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
