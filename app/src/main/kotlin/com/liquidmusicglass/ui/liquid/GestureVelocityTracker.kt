package com.liquidmusicglass.ui.liquid

import androidx.compose.ui.geometry.Offset

class GestureVelocityTracker {

    private var last = Offset.Zero

    fun addPosition(position: Offset) {
        last = position
    }

    fun calculateVelocity(): Offset {
        return last
    }
}