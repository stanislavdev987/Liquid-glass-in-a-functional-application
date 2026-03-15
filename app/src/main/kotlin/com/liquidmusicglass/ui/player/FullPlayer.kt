package com.liquidmusicglass.ui.player

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp

import com.liquidmusicglass.ui.glass.AlbumArtImage
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Composable
fun FullPlayer(
    expandProgress: Float,
    trackTitle: String,
    artistName: String,
    isPlaying: Boolean,
    albumArtUri: Uri?,
    currentPositionMs: Long,
    durationMs: Long,
    volume: Float,
    isMixing: Boolean,
    onClose: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onOpenSettings: () -> Unit
) {
    if (expandProgress <= 0.005f) return

    // Свой backdrop для стеклянных элементов поверх арта
    val playerBackdrop: LayerBackdrop = rememberLayerBackdrop()

    var isFavorite by remember { mutableStateOf(false) }

    val trackProgress = if (durationMs > 0)
        (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    val controlsAlpha = ((expandProgress - 0.5f) / 0.5f).coerceIn(0f, 1f)
    val bgAlpha = (expandProgress * 1.5f).coerceIn(0f, 1f)

    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenH = maxHeight
        val screenW = maxWidth
        val hPad = 24.dp

        // Обложка: откуда (мини) → куда (полный)
        val artStart = 44.dp
        val artEnd = screenW - hPad * 2
        val artYStart = screenH - 120.dp
        val artYEnd = 80.dp
        val artXStart = 20.dp
        val artXEnd = hPad
        val artSize = lerp(artStart, artEnd, expandProgress)
        val artY = lerp(artYStart, artYEnd, expandProgress)
        val artX = lerp(artXStart, artXEnd, expandProgress)
        val artCorner = lerp(12.dp, 20.dp, expandProgress)
        val contentY = artY + artSize + 28.dp

        // ══ Фон: арт захватывается playerBackdrop ══
        AlbumArtImage(
            uri = albumArtUri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = bgAlpha
                    scaleX = 1.15f
                    scaleY = 1.15f
                }
                .layerBackdrop(playerBackdrop)
        )

        // Тёмный градиент поверх (НЕ в layerBackdrop — рядом ✓)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bgAlpha }
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.50f),
                            Color.Black.copy(alpha = 0.28f),
                            Color.Black.copy(alpha = 0.28f),
                            Color.Black.copy(alpha = 0.60f)
                        )
                    )
                )
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            if (dragAmount > 0) { change.consume(); onDrag(dragAmount) }
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() }
                    )
                }
        )

        // ══ Handle ══
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = controlsAlpha }
                .padding(top = 52.dp)
                .clickable(remember { MutableInteractionSource() }, null) { onClose() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp).height(5.dp)
                    .background(Color.White.copy(alpha = 0.44f), RoundedCornerShape(100.dp))
            )
        }

        // ══ Обложка (летит из мини-плеера) ══
        Box(
            modifier = Modifier
                .offset {
                    with(density) { IntOffset(artX.roundToPx(), artY.roundToPx()) }
                }
                .size(artSize)
                .clip(RoundedCornerShape(artCorner))
        ) {
            AlbumArtImage(
                uri = albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Тонкий глянец сверху
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.06f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.12f)
                            )
                        )
                    )
            )
        }

        // ══ Контролы (появляются после 50%) ══
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = hPad)
                .offset { with(density) { IntOffset(0, contentY.roundToPx()) } }
                .graphicsLayer { alpha = controlsAlpha }
        ) {
            TrackInfoRow(
                trackTitle = trackTitle,
                artistName = artistName,
                isFavorite = isFavorite,
                onToggleFavorite = { isFavorite = !isFavorite },
                onOpenSettings = onOpenSettings
            )

            Spacer(modifier = Modifier.height(18.dp))

            ProgressSection(
                progress = trackProgress,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                showMixing = isMixing,
                onSeek = onSeek
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Главные кнопки — стеклянный Play/Pause
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkipBtn(Icons.Rounded.SkipPrevious, 48.dp, onSkipPrevious)
                Spacer(modifier = Modifier.width(32.dp))
                GlassPlayButton(isPlaying, playerBackdrop, onPlayPause)
                Spacer(modifier = Modifier.width(32.dp))
                SkipBtn(Icons.Rounded.SkipNext, 48.dp, onSkipNext)
            }

            Spacer(modifier = Modifier.height(24.dp))
            VolumeSection(volume = volume, onVolumeChange = onVolumeChange)
            Spacer(modifier = Modifier.height(24.dp))
            BottomButtons(backdrop = playerBackdrop)
        }
    }
}

// ══════════════════════════════════════════════
// Стеклянная кнопка Play/Pause — iOS 26 style
// ══════════════════════════════════════════════

@Composable
private fun GlassPlayButton(
    isPlaying: Boolean,
    backdrop: LayerBackdrop,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(76.dp)
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
                    drawRect(Color.White.copy(alpha = 0.2f))
                    drawRect(
                        color = Color.White.copy(alpha = 0.4f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            )
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(34.dp),
            tint = Color.White
        )
    }
}

// Стеклянная иконка-кнопка (для нижней панели)
@Composable
private fun GlassIconButton(
    icon: ImageVector,
    backdrop: LayerBackdrop,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .size(46.dp)
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
                    drawRect(Color.White.copy(alpha = 0.2f))
                    drawRect(
                        color = Color.White.copy(alpha = 0.4f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            )
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.90f), modifier = Modifier.size(20.dp))
    }
}

// ══════════════════════════════════════════════
// Вспомогательные composable
// ══════════════════════════════════════════════

@Composable
private fun SkipBtn(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(size + 16.dp)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(size))
    }
}

@Composable
private fun TrackInfoRow(
    trackTitle: String,
    artistName: String,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                trackTitle,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                artistName,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 17.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        SimpleBtn(Icons.Rounded.StarBorder, onClick = onToggleFavorite)
        Spacer(Modifier.width(6.dp))
        SimpleBtn(Icons.Rounded.MoreHoriz, onClick = onOpenSettings)
    }
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "${s / 60}:%02d".format(s % 60)
}

@Composable
private fun ProgressSection(
    progress: Float,
    currentPositionMs: Long,
    durationMs: Long,
    showMixing: Boolean,
    onSeek: (Long) -> Unit
) {
    val remaining = (durationMs - currentPositionMs).coerceAtLeast(0)
    var barW by remember { mutableFloatStateOf(1f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth().height(20.dp)
                .onGloballyPositioned { barW = it.size.width.toFloat() }
                .pointerInput(durationMs) {
                    detectTapGestures {
                        if (durationMs > 0 && barW > 0f)
                            onSeek(((it.x / barW).coerceIn(0f, 1f) * durationMs).toLong())
                    }
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        if (durationMs > 0 && barW > 0f)
                            onSeek(((change.position.x / barW).coerceIn(0f, 1f) * durationMs).toLong())
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(100.dp))
            ) {
                Box(
                    Modifier.fillMaxWidth(progress).height(4.dp)
                        .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(100.dp))
                )
            }
        }

        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(
                formatTime(currentPositionMs),
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                "-${formatTime(remaining)}",
                color = Color.White.copy(alpha = 0.58f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
            val mAlpha by animateFloatAsState(
                targetValue = if (showMixing) 1f else 0f,
                animationSpec = tween(if (showMixing) 600 else 400, easing = FastOutSlowInEasing),
                label = "mix"
            )
            if (mAlpha > 0f) {
                Text(
                    "Mixing",
                    color = Color.White.copy(alpha = 0.75f * mAlpha),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = mAlpha }
                )
            }
        }
    }
}

@Composable
private fun VolumeSection(volume: Float, onVolumeChange: (Float) -> Unit) {
    var barW by remember { mutableFloatStateOf(1f) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.VolumeDown, null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(10.dp))
        Box(
            Modifier.weight(1f).height(20.dp)
                .onGloballyPositioned { barW = it.size.width.toFloat() }
                .pointerInput(Unit) {
                    detectTapGestures {
                        if (barW > 0f) onVolumeChange((it.x / barW).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        if (barW > 0f) onVolumeChange((change.position.x / barW).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier.fillMaxWidth().height(4.dp)
                    .background(Color.White.copy(alpha = 0.20f), RoundedCornerShape(100.dp))
            ) {
                Box(
                    Modifier.fillMaxWidth(volume.coerceIn(0f, 1f)).height(4.dp)
                        .background(Color.White.copy(alpha = 0.90f), RoundedCornerShape(100.dp))
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            Icons.AutoMirrored.Rounded.VolumeUp, null,
            tint = Color.White.copy(alpha = 0.65f),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun BottomButtons(backdrop: LayerBackdrop) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        GlassIconButton(Icons.Rounded.ChatBubbleOutline, backdrop)
        GlassIconButton(Icons.Rounded.Cast, backdrop)
        GlassIconButton(Icons.AutoMirrored.Rounded.QueueMusic, backdrop)
    }
}

@Composable
private fun SimpleBtn(icon: ImageVector, onClick: () -> Unit = {}) {
    Box(
        Modifier.size(44.dp)
            .clickable(remember { MutableInteractionSource() }, null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = Color.White.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
    }
}
