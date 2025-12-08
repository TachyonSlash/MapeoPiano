package com.pjsoft.mapeopiano

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.ImageFormat
import android.media.Image
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker.HandLandmarkerOptions
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.io.ByteArrayOutputStream
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var pianoView: PianoView
    private lateinit var handLandmarker: HandLandmarker

    // detector puro (no requiere OpenCV)
    private val pianoDetector = PianoDetector()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview = findViewById(R.id.cameraPreview)
        pianoView = findViewById(R.id.pianoView)

        requestCameraPermission()
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            setupHandDetector()
            startCamera()
        } else {
            val launcher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    setupHandDetector()
                    startCamera()
                } else {
                    Log.e("MainActivity", "Permiso de cámara denegado")
                }
            }
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun setupHandDetector() {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("hand_landmarker.task")
            .build()

        val options = HandLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setNumHands(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result: HandLandmarkerResult, _: Any? ->
                // Nota: la firma puede variar por versión; aquí sólo usamos result
                handleHandResult(result)
            }
            .setErrorListener { e: Throwable ->
                Log.e("HandLandmarker", "Error: ${e.message}", e)
            }
            .build()

        handLandmarker = HandLandmarker.createFromOptions(this, options)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(cameraPreview.surfaceProvider)
            }

            val analysis = ImageAnalysis.Builder().build()
            analysis.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
                processImage(imageProxy)
            }

            val selector = androidx.camera.core.CameraSelector.DEFAULT_BACK_CAMERA

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                selector,
                preview,
                analysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    @OptIn(ExperimentalGetImage::class)
    private fun processImage(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        thread {
            // 1) convertir a Bitmap (tu función actual)
            val bitmap = imageProxyToBitmap(imageProxy)

            if (bitmap != null) {
                // 2) enviar a MediaPipe (detección de manos)
                val mpImage = BitmapImageBuilder(bitmap).build()
                handLandmarker.detectAsync(mpImage, imageProxy.imageInfo.timestamp)

                // 3) detectar piano (con PianoDetector puro)
                val detectedRect = pianoDetector.detectPianoRect(bitmap)
                if (detectedRect != null) {
                    Log.i("PianoDetector", "Piano detectado: $detectedRect")
                    // manda rect en coordenadas del bitmap; PianoView debe convertir si es necesario
                    pianoView.post {
                        pianoView.updateDetectedPianoRect(detectedRect)
                    }
                    // Opcional: detectar keys y mandarlas al pianoView
                    val keys = pianoDetector.detectKeys(bitmap, detectedRect)
                    pianoView.post {
                        pianoView.updateDetectedKeys(keys)
                    }
                }
            }

            imageProxy.close()
        }
    }

    // kotlin
    fun detectKeys(src: Bitmap, pianoRect: Rect): List<DetectedKey> {
        return pianoDetector.detectKeys(src, pianoRect)
    }



    private fun handleHandResult(result: HandLandmarkerResult) {
        if (result.landmarks().isNotEmpty()) {
            val hand = result.landmarks()[0]
            val index = hand[8]

            // convierte coordenadas normalizadas -> pixeles de PreviewView
            pianoView.post {
                pianoView.updateFingerPosition(
                    index.x() * pianoView.width,
                    index.y() * pianoView.height
                )
            }
        }
    }

    @OptIn(ExperimentalGetImage::class)
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null
        val nv21 = yuv420ToNv21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val imageBytes = out.toByteArray()
        var bmp = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        val rotation = imageProxy.imageInfo.rotationDegrees
        if (rotation != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotation.toFloat())
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }
        return bmp
    }

    private fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val ySize = width * height
        val uvSize = width * height / 2

        val out = ByteArray(ySize + uvSize)

        var outputPos = 0

        val yRowStride = yPlane.rowStride
        val yBuffer = yPlane.buffer

        for (row in 0 until height) {
            yBuffer.position(row * yRowStride)
            yBuffer.get(out, outputPos, width)
            outputPos += width
        }

        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val uvHeight = height / 2
        val uvWidth = width / 2

        for (row in 0 until uvHeight) {
            for (col in 0 until uvWidth) {
                val uIndex = row * uvRowStride + col * uvPixelStride
                val vIndex = uIndex

                out[outputPos++] = vBuffer.get(vIndex) // V
                out[outputPos++] = uBuffer.get(uIndex) // U
            }
        }

        return out
    }
}
