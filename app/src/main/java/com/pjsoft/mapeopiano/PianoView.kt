package com.pjsoft.mapeopiano

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.floor
class PianoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val NUM_WHITE_KEYS = 14

    // Patrón estándar de teclas negras (para un grupo de 14 teclas)
    private val BLACK_KEYS_PATTERN = booleanArrayOf(
        true, true, false,
        true, true, true, false,
        true, true, false,
        true, true, true, false
    )

    // Estado de teclas (blancas y negras)
    private val whiteKeysPressed = BooleanArray(NUM_WHITE_KEYS)
    private val blackKeysPressed = BooleanArray(BLACK_KEYS_PATTERN.size)

    //  Colores
    private val whiteKeyPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val whiteKeyPressedPaint = Paint().apply {
        color = Color.parseColor("#FFEB3B") // Amarillo suave
        style = Paint.Style.FILL
    }

    private val whiteKeyBorderPaint = Paint().apply {
        color = Color.parseColor("#9E9E9E")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val blackKeyPaint = Paint().apply {
        color = Color.parseColor("#222222")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val blackKeyPressedPaint = Paint().apply {
        color = Color.parseColor("#616161")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val whiteKeyWidth = width / NUM_WHITE_KEYS.toFloat()
        val blackKeyWidth = whiteKeyWidth * 0.6f
        val blackKeyHeight = height * 0.6f

        // DIBUJAR TECLAS BLANCAS
        for (i in 0 until NUM_WHITE_KEYS) {
            val left = i * whiteKeyWidth
            val right = left + whiteKeyWidth

            val paintToUse =
                if (whiteKeysPressed[i]) whiteKeyPressedPaint else whiteKeyPaint

            canvas.drawRect(left, 0f, right, height.toFloat(), paintToUse)
            canvas.drawRect(left, 0f, right, height.toFloat(), whiteKeyBorderPaint)
        }

        // DIBUJAR TECLAS NEGRAS ENCIMA
        for (i in BLACK_KEYS_PATTERN.indices) {
            if (!BLACK_KEYS_PATTERN[i]) continue

            val whiteKeyWidthF = width / NUM_WHITE_KEYS.toFloat()
            val centerX = (i + 1) * whiteKeyWidthF
            val left = centerX - blackKeyWidth / 2
            val right = centerX + blackKeyWidth / 2

            val paintToUse =
                if (blackKeysPressed[i]) blackKeyPressedPaint else blackKeyPaint

            canvas.drawRect(left, 0f, right, blackKeyHeight, paintToUse)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        val whiteKeyWidth = width / NUM_WHITE_KEYS.toFloat()
        val blackKeyWidth = whiteKeyWidth * 0.6f
        val blackKeyHeight = height * 0.6f

        when (event.action) {

            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // RESET all keys
                whiteKeysPressed.fill(false)
                blackKeysPressed.fill(false)

                // Primero verificar negras (tienen prioridad)
                for (i in BLACK_KEYS_PATTERN.indices) {
                    if (!BLACK_KEYS_PATTERN[i]) continue

                    val centerX = (i + 1) * whiteKeyWidth
                    val left = centerX - blackKeyWidth / 2
                    val right = centerX + blackKeyWidth / 2

                    if (x in left..right && y < blackKeyHeight) {
                        blackKeysPressed[i] = true
                        invalidate()
                        return true
                    }
                }

                // Si no fue negra → verificar blanca
                val whiteIndex = floor(x / whiteKeyWidth).toInt()
                if (whiteIndex in 0 until NUM_WHITE_KEYS) {
                    whiteKeysPressed[whiteIndex] = true
                }

                invalidate()
            }

            MotionEvent.ACTION_UP -> {
                // Soltar todo
                whiteKeysPressed.fill(false)
                blackKeysPressed.fill(false)
                invalidate()
            }
        }

        return true
    }
}