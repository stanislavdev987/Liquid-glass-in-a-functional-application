package com.liquidmusicglass.ui.liquid

import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.CoroutineScope

@Stable
class InteractiveHighlight(
    @Suppress("UNUSED_PARAMETER")
    animationScope: CoroutineScope,
    @Suppress("UNUSED_PARAMETER")
    position: (Size, Offset) -> Offset
) {
    val modifier: Modifier = Modifier
    val gestureModifier: Modifier = Modifier
}