package cv.rootnode.ambient

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var statusText: TextView
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var prevLuma: Double? = null
    private var lastEventAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        statusText = findViewById(R.id.statusText)

        statusText.text = "Home Ambient Agent v0.2\nWaiting for permissions..."

        requestIfNeeded(arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        ))

        if (hasAllPermissions()) startCamera()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && hasAllPermissions()) startCamera()
        else statusText.text = "Permissions denied. Camera/audio required."
    }

    private fun hasAllPermissions(): Boolean {
        val perms = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        return perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
    }

    private fun requestIfNeeded(perms: Array<String>) {
        val missing = perms.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 1001)
        }
    }

    private fun startCamera() {
        statusText.text = "Camera started. Motion analyzer active..."
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor) { image -> analyzeFrame(image) } }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (e: Exception) {
                statusText.text = "Camera bind failed: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeFrame(image: ImageProxy) {
        try {
            val yBuffer = image.planes[0].buffer
            val data = ByteArray(yBuffer.remaining())
            yBuffer.get(data)

            var sum = 0L
            val step = 16
            var count = 0
            var i = 0
            while (i < data.size) {
                sum += (data[i].toInt() and 0xFF)
                count++
                i += step
            }

            val luma = if (count > 0) sum.toDouble() / count else 0.0
            val prev = prevLuma
            prevLuma = luma
            val motionScore = if (prev == null) 0.0 else abs(luma - prev) / 255.0
            val now = System.currentTimeMillis()

            if (motionScore > 0.06 && now - lastEventAt > 5000) {
                lastEventAt = now
                EventUploader.sendMotionEvent("android-tablet-124", motionScore)
                runOnUiThread {
                    statusText.text = "Motion detected: ${"%.3f".format(motionScore)}\nEvent sent"
                }
            }
        } finally {
            image.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
