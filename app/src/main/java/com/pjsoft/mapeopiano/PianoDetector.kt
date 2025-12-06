package com.pjsoft.mapeopiano

import android.graphics.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class DetectedKey(
    val index: Int,
    val rect: RectF,
    val isBlack: Boolean
)

class PianoDetector(
    private val gradSmoothRadius: Int = 9
) {
    // Para debugging
    var lastColGrad: FloatArray? = null
    var lastRowEnergy: IntArray? = null

    fun detectPianoRect(src: Bitmap): Rect? {
        val w = src.width
        val h = src.height

        val gray = bitmapToGray(src)

        // ↓↓↓ MUCHO MÁS SENSIBLE
        val rowEnergy = IntArray(h)
        for (y in 0 until h) {
            var s = 0
            for (x in 0 until w - 1) {
                s += abs(gray[y * w + x] - gray[y * w + x + 1])
            }
            rowEnergy[y] = s
        }

        lastRowEnergy = rowEnergy

        val smoothRow = smoothInt(rowEnergy, 12)
        val maxRow = smoothRow.maxOrNull() ?: return null
        if (maxRow < 500) return null   //ANTES: 10% del pico → muy estricto

        val thresholdRow = (maxRow * 0.15).toInt()

        var minY = -1
        var maxY = -1

        for (y in 0 until h) {
            if (smoothRow[y] >= thresholdRow) {
                minY = y
                break
            }
        }

        if (minY == -1) return null

        for (y in h - 1 downTo 0) {
            if (smoothRow[y] >= thresholdRow) {
                maxY = y
                break
            }
        }

        if (maxY - minY < 40) return null

        // -------- Columnas --------
        val colGrad = columnGradient(gray, w, h)
        lastColGrad = colGrad

        val smoothCols = smoothFloat(colGrad, gradSmoothRadius)
        val maxC = smoothCols.maxOrNull() ?: return null

        if (maxC < 800) return null  //ANTES: 3000 → imposible en muchos teléfonos

        val thresholdCol = maxC * 0.07f

        var minX = -1
        var maxX = -1

        for (x in 0 until w) {
            if (smoothCols[x] >= thresholdCol) {
                minX = x
                break
            }
        }

        for (x in w - 1 downTo 0) {
            if (smoothCols[x] >= thresholdCol) {
                maxX = x
                break
            }
        }

        if (minX == -1 || maxX == -1) return null
        if (maxX - minX < 60) return null

        return Rect(minX, minY, maxX, maxY)
    }

    fun detectKeys(src: Bitmap, pianoRect: Rect): List<DetectedKey> {
        val w = src.width
        val h = src.height

        val gray = bitmapToGray(src)

        val px = pianoRect.left
        val py = pianoRect.top
        val pw = pianoRect.width()
        val ph = pianoRect.height()

        if (pw <= 0 || ph <= 0) return emptyList()

        val whiteKeyCount = 52  // moderno, 88 teclas totales
        val keyWidth = pw.toFloat() / whiteKeyCount

        val result = mutableListOf<DetectedKey>()

        for (i in 0 until whiteKeyCount) {
            val x1 = (px + i * keyWidth).toInt()
            val x2 = (px + (i + 1) * keyWidth).toInt()

            val y1 = py
            val y2 = py + ph

            // medir oscuridad (solo parte superior)
            var sum = 0
            var count = 0

            val sampleTop = (ph * 0.25f).toInt()

            for (y in y1 until (y1 + sampleTop)) {
                for (x in x1 until x2) {
                    val idx = y * w + x
                    if (idx in gray.indices) {
                        sum += gray[idx]
                        count++
                    }
                }
            }

            val avg = if (count > 0) sum / count else 255

            val isBlack = avg < 80  // umbral de oscuridad típico para teclas negras

            result.add(
                DetectedKey(
                    index = i,
                    rect = RectF(x1.toFloat(), y1.toFloat(), x2.toFloat(), y2.toFloat()),
                    isBlack = isBlack
                )
            )
        }

        return result
    }



    // ----------------------------
    //   HELPERS
    // ----------------------------

    private fun bitmapToGray(bmp: Bitmap): IntArray {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val g = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val gVal = (c shr 8) and 0xFF
            val b = c and 0xFF
            g[i] = (0.3 * r + 0.59 * gVal + 0.11 * b).toInt()
        }
        return g
    }

    private fun columnGradient(gray: IntArray, w: Int, h: Int): FloatArray {
        val arr = FloatArray(w)
        for (x in 0 until w - 1) {
            var s = 0f
            for (y in 0 until h) {
                val i = y * w + x
                s += abs(gray[i] - gray[i + 1])
            }
            arr[x] = s
        }
        arr[w - 1] = arr[w - 2]
        return arr
    }

    private fun smoothFloat(input: FloatArray, radius: Int): FloatArray {
        val n = input.size
        val r = max(1, radius)
        val out = FloatArray(n)

        for (i in 0 until n) {
            val a = max(0, i - r)
            val b = min(n - 1, i + r)
            var s = 0f
            for (j in a..b) s += input[j]
            out[i] = s / (b - a + 1)
        }
        return out
    }

    private fun smoothInt(input: IntArray, radius: Int): IntArray {
        val n = input.size
        val r = max(1, radius)
        val out = IntArray(n)

        for (i in 0 until n) {
            val a = max(0, i - r)
            val b = min(n - 1, i + r)
            var s = 0
            for (j in a..b) s += input[j]
            out[i] = s / (b - a + 1)
        }
        return out
    }
}
