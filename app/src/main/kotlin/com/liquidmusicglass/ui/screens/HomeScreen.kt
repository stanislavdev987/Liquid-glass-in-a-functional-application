package com.liquidmusicglass.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Composable
fun HomeScreen() {
    val scroll = rememberScrollState()
    val screenBackdrop = rememberLayerBackdrop()

    Box(modifier = Modifier.fillMaxSize()) {
        // Фон — layerBackdrop захватывает только этот слой
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFFF0000),
                            Color(0xFFFF7A00),
                            Color(0xFFFFFF00),
                            Color(0xFF00FF00),
                            Color(0xFF00FFFF),
                            Color(0xFF0066FF),
                            Color(0xFF8A2BE2),
                            Color(0xFFFF00FF)
                        )
                    )
                )
                .layerBackdrop(screenBackdrop)
        )

        // Контент — рядом с layerBackdrop, не внутри него
        // drawBackdrop(screenBackdrop) в GlassCard работает корректно
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBars))

            Text(text = "Music", fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(10.dp))
            Text(text = "kyant0/backdrop 2.0.0-alpha03 — chromaticAberration", color = Color.White)
            Spacer(modifier = Modifier.height(24.dp))

            repeat(12) { index ->
                GlassCard(index = index + 1, backdrop = screenBackdrop)
                Spacer(modifier = Modifier.height(20.dp))
            }

            Spacer(modifier = Modifier.height(220.dp))
        }
    }
}

@Composable
private fun GlassCard(index: Int, backdrop: LayerBackdrop) {
    val shape = RoundedCornerShape(34.dp)

    val gradients = listOf(
        listOf(Color(0xFFFF3D3D), Color(0xFFFF9A00)),
        listOf(Color(0xFFFFFF00), Color(0xFF00FF6A)),
        listOf(Color(0xFF00E5FF), Color(0xFF2979FF)),
        listOf(Color(0xFF7C4DFF), Color(0xFFFF2FD2)),
        listOf(Color(0xFFFF6A00), Color(0xFFFF1744)),
        listOf(Color(0xFF00C853), Color(0xFF00B0FF))
    )
    val pair = gradients[index % gradients.size]

    Box(modifier = Modifier.fillMaxWidth().height(140.dp)) {
        // Цветной фон карточки
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(brush = Brush.linearGradient(pair), shape = shape)
        )

        // Glass layer 1: blur + vibrancy
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        blur(16.dp.toPx())
                        vibrancy()
                    },
                    onDrawSurface = {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.06f),
                                    Color.Transparent
                                )
                            ),
                            blendMode = BlendMode.SrcOver
                        )
                    }
                )
        )

        // Glass layer 2: lens + chromaticAberration
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { shape },
                    effects = {
                        blur(8.dp.toPx())
                        vibrancy()
                        lens(
                            refractionHeight = 34.dp.toPx(),
                            refractionAmount = -80.dp.toPx(),
                            chromaticAberration = true
                        )
                    },
                    onDrawSurface = {
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.15f),
                                    Color.Transparent
                                )
                            ),
                            blendMode = BlendMode.SrcOver
                        )
                    }
                )
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.32f),
                            Color.White.copy(alpha = 0.10f)
                        )
                    ),
                    shape = shape
                )
        )

        // Контент
        Column(
            modifier = Modifier.matchParentSize().padding(20.dp)
        ) {
            Text(text = "Playlist $index", fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "Liquid glass + chromatic aberration", color = Color.White.copy(alpha = 0.88f))
        }
    }
}
