package com.pjsoft.mapeopiano

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class PianoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ======== Estado de teclas tocadas ==========
    private val whiteKeysPressed = HashMap<Int, Boolean>()
    private val blackKeysPressed = HashMap<Int, Boolean>()

    // ======== Datos detectados por PianoDetector ==========
    private var detectedPianoRect: Rect? = null
    private var detectedKeys: List<DetectedKey> = emptyList()

    // ======== Posición del dedo detectado por IA ==========
    private var fingerX = -1f
    private var fingerY = -1f

    private val fingerPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }

    // ======== Colores ========
    private val pianoRectPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val whiteKeyPaint = Paint().apply { color = Color.WHITE }
    private val whiteKeyPressedPaint = Paint().apply { color = Color.parseColor("#FFEB3B") }
    private val whiteKeyBorderPaint = Paint().apply {
        color = Color.DKGRAY
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val blackKeyPaint = Paint().apply { color = Color.BLACK }
    private val blackKeyPressedPaint = Paint().apply { color = Color.parseColor("#555555") }

    private val detectedKeyPaint = Paint().apply {
        color = Color.CYAN
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    // ============================================================
    //      MÉTODOS EXPUESTOS PARA MainActivity / PianoDetector
    // ============================================================

    fun updateDetectedPianoRect(rect: Rect) {
        detectedPianoRect = rect
        invalidate()
    }

    fun updateDetectedKeys(keys: List<DetectedKey>) {
        detectedKeys = keys
        invalidate()
    }

    fun updateFingerPosition(x: Float, y: Float) {
        fingerX = x
        fingerY = y
        detectKeyFromFinger()
        invalidate()
    }

    // ============================================================
    //                  DETECCIÓN DEL DEDO EN EL PIANO
    // ============================================================
    private fun detectKeyFromFinger() {
        if (fingerX < 0 || fingerY < 0) return
        val rect = detectedPianoRect ?: return

        // Resetear todo
        whiteKeysPressed.clear()
        blackKeysPressed.clear()

        // Si el dedo no está dentro del piano
        if (!rect.contains(fingerX.toInt(), fingerY.toInt())) {
            return
        }

        // Revisar cada tecla detectada por el Detector
        for (key in detectedKeys) {
            if (key.rect.contains(fingerX, fingerY)) {
                if (key.isBlack) {
                    blackKeysPressed[key.index] = true
                } else {
                    whiteKeysPressed[key.index] = true
                }
                return
            }
        }
    }

    // ============================================================
    //                          DIBUJO
    // ============================================================
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // (1) Rectángulo detectado
        detectedPianoRect?.let {
            canvas.drawRect(it, pianoRectPaint)
        }

        // (2) Teclas detectadas
        detectedKeys.forEach { key ->
            canvas.drawRect(key.rect, detectedKeyPaint)
        }

        // (3) Teclas presionadas – blancas
        for ((index, pressed) in whiteKeysPressed) {
            if (pressed) {
                val key = detectedKeys.firstOrNull { !it.isBlack && it.index == index }
                key?.let {
                    canvas.drawRect(it.rect, whiteKeyPressedPaint)
                }
            }
        }

        // (4) Teclas presionadas – negras
        for ((index, pressed) in blackKeysPressed) {
            if (pressed) {
                val key = detectedKeys.firstOrNull { it.isBlack && it.index == index }
                key?.let {
                    canvas.drawRect(it.rect, blackKeyPressedPaint)
                }
            }
        }

        // (5) Dedo detectado
        if (fingerX >= 0 && fingerY >= 0) {
            canvas.drawCircle(fingerX, fingerY, 15f, fingerPaint)
        }
    }
}
