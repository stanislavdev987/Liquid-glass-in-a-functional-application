package com.liquidmusicglass.ui.liquid

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    @Suppress("UNUSED_PARAMETER")
    visibilityThreshold: Float = 0.001f,
    private val initialScale: Float = 1f,
    private val pressedScale: Float = 1f,
    private val onDragStarted: DampedDragAnimation.() -> Unit = {},
    private val onDragStopped: DampedDragAnimation.() -> Unit = {},
    private val onDrag: DampedDragAnimation.(Offset, Offset) -> Unit = { _, _ -> }
) {
    private val animatable = Animatable(
        initialValue = initialValue.coerceIn(valueRange.start, valueRange.endInclusive)
    )

    var targetValue by mutableFloatStateOf(animatable.value)
        private set

    var velocity by mutableFloatStateOf(0f)
        private set

    var pressProgress by mutableFloatStateOf(0f)
        private set

    val value: Float
        get() = animatable.value

    val scaleX: Float
        get() = lerp(initialScale, pressedScale, pressProgress)

    val scaleY: Float
        get() = lerp(initialScale, pressedScale, pressProgress)

    fun updateValue(value: Float) {
        val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
        targetValue = clamped
        animationScope.launch {
            animatable.snapTo(clamped)
        }
    }

    fun animateToValue(value: Float) {
        val clamped = value.coerceIn(valueRange.start, valueRange.endInclusive)
        targetValue = clamped
        animationScope.launch {
            animatable.animateTo(
                targetValue = clamped,
                animationSpec = spring(
                    dampingRatio = 0.88f,
                    stiffness = 220f
                )
            )
        }
    }

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        detectDragGestures(
            onDragStart = {
                pressProgress = 1f
                this@DampedDragAnimation.onDragStarted()
            },
            onDragCancel = {
                velocity = 0f
                pressProgress = 0f
                this@DampedDragAnimation.onDragStopped()
            },
            onDragEnd = {
                velocity = 0f
                pressProgress = 0f
                this@DampedDragAnimation.onDragStopped()
            },
            onDrag = { change, dragAmount ->
                change.consume()
                velocity = dragAmount.x
                pressProgress = (0.55f + abs(dragAmount.x) / 1600f).coerceIn(0.55f, 1f)
                this@DampedDragAnimation.onDrag(change.position, dragAmount)
            }
        )
    }

    private fun lerp(start: Float, stop: Float, fraction: Float): Float {
        return start + (stop - start) * fraction
    }
}