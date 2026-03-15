package com.liquidmusicglass.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.zIndex
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.launch
import kotlin.math.floor

@Immutable
private data class BottomNavItem(
    val icon: ImageVector,
    val label: String
)

@Composable
fun BottomBar(
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    backdrop: LayerBackdrop
) {
    val items = listOf(
        BottomNavItem(Icons.Rounded.Home, "Home"),
        BottomNavItem(Icons.Rounded.GridView, "New"),
        BottomNavItem(Icons.Rounded.LibraryMusic, "Library"),
        BottomNavItem(Icons.Rounded.Settings, "Settings")
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        NavigationCapsule(
            items = items,
            selectedIndex = selectedIndex,
            onItemSelected = onItemSelected,
            backdrop = backdrop,
            modifier = Modifier.weight(1f)
        )

        Spacer(modifier = Modifier.width(8.dp))

        SearchButton(backdrop = backdrop)
    }
}

@Composable
private fun NavigationCapsule(
    items: List<BottomNavItem>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    backdrop: LayerBackdrop,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val shape = RoundedCornerShape(100.dp)

    var indicatorPosition by remember { mutableStateOf(selectedIndex.toFloat()) }
    var isDragging by remember { mutableStateOf(false) }

    val hoveredIndex by remember(indicatorPosition, items) {
        derivedStateOf {
            indicatorPosition
                .fastRoundToInt()
                .fastCoerceIn(0, items.lastIndex)
        }
    }

    LaunchedEffect(selectedIndex) {
        if (!isDragging) {
            val start = indicatorPosition
            val end = selectedIndex.toFloat()
            val delta = end - start
            if (delta == 0f) return@LaunchedEffect

            val steps = 22
            for (i in 1..steps) {
                val t = i / steps.toFloat()
                val eased = 1f - (1f - t) * (1f - t)
                indicatorPosition = start + delta * eased
                kotlinx.coroutines.delay(8L)
            }
            indicatorPosition = end
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .height(64.dp)
            .clip(shape),
        contentAlignment = Alignment.CenterStart
    ) {
        val horizontalInset = 2.dp
        val verticalInset = 3.dp

        val horizontalInsetPx = with(density) { horizontalInset.toPx() }
        val slotWidthPx = (constraints.maxWidth.toFloat() - horizontalInsetPx * 2f) / items.size
        val slotWidthDp = with(density) { slotWidthPx.toDp() }
        val lastIndexFloat = items.lastIndex.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(12.dp.toPx())
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.15f))
                        drawRect(
                            color = Color.White.copy(alpha = 0.30f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                )
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(1f)
                .pointerInput(items.size) {
                    detectTapGestures { offset ->
                        val tappedIndex = floor(
                            ((offset.x - horizontalInsetPx).coerceAtLeast(0f)) / slotWidthPx
                        ).toInt().fastCoerceIn(0, items.lastIndex)

                        onItemSelected(tappedIndex)
                    }
                }
        )

        Box(
            modifier = Modifier
                .width(slotWidthDp)
                .fillMaxHeight()
                .zIndex(2f)
                .graphicsLayer {
                    translationX = horizontalInsetPx + indicatorPosition * slotWidthPx
                }
                .padding(horizontal = horizontalInset, vertical = verticalInset)
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        val indexDelta = delta / slotWidthPx
                        indicatorPosition = (indicatorPosition + indexDelta)
                            .fastCoerceIn(0f, lastIndexFloat)
                    },
                    onDragStarted = {
                        isDragging = true
                    },
                    onDragStopped = {
                        val targetIndex = indicatorPosition
                            .fastRoundToInt()
                            .fastCoerceIn(0, items.lastIndex)

                        scope.launch {
                            indicatorPosition = targetIndex.toFloat()
                        }

                        isDragging = false
                        onItemSelected(targetIndex)
                    }
                )
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        vibrancy()
                        blur(12.dp.toPx())
                        lens(
                            refractionHeight = 24.dp.toPx(),
                            refractionAmount = 32.dp.toPx(),
                            chromaticAberration = true
                        )
                    },
                    onDrawSurface = {
                        drawRect(Color.White.copy(alpha = 0.20f))
                        drawRect(
                            color = Color.White.copy(alpha = 0.40f),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                )
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(3f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEachIndexed { index, item ->
                val isActive = if (isDragging) hoveredIndex == index else selectedIndex == index

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    NavItemContent(
                        item = item,
                        selected = isActive
                    )
                }
            }
        }
    }
}

@Composable
private fun NavItemContent(
    item: BottomNavItem,
    selected: Boolean
) {
    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            Color(0xFF0A0A0F)
        } else {
            Color.White.copy(alpha = 0.75f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "icon_color"
    )

    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            Color(0xFF0A0A0F)
        } else {
            Color.White.copy(alpha = 0.65f)
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "label_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            modifier = Modifier.size(23.dp),
            tint = iconColor
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = item.label,
            color = labelColor,
            fontSize = 10.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
private fun SearchButton(
    backdrop: LayerBackdrop
) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { CircleShape },
                effects = {
                    vibrancy()
                    blur(12.dp.toPx())
                    lens(
                        refractionHeight = 24.dp.toPx(),
                        refractionAmount = 32.dp.toPx(),
                        chromaticAberration = true
                    )
                },
                onDrawSurface = {
                    drawRect(Color.White.copy(alpha = 0.20f))
                    drawRect(
                        color = Color.White.copy(alpha = 0.40f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            modifier = Modifier.size(26.dp),
            tint = Color.White
        )
    }
}