package com.pjsoft.mapeopiano.models

import android.graphics.RectF

data class PianoKey(
    val noteName: String,
    val midi: Int,
    val rect: RectF,
    val isBlack: Boolean
)
