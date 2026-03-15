package com.liquidmusicglass.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.liquidmusicglass.engine.PlayerController
import com.liquidmusicglass.ui.navigation.BottomBar
import com.liquidmusicglass.ui.player.FullPlayer
import com.liquidmusicglass.ui.player.MiniPlayer
import com.liquidmusicglass.ui.screens.HomeScreen
import com.liquidmusicglass.ui.screens.SettingsScreen
import kotlinx.coroutines.launch

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedIndex by remember { mutableIntStateOf(3) }
    var settingsOpen by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionGranted = granted
        if (granted) PlayerController.init(context)
    }

    LaunchedEffect(Unit) {
        if (permissionGranted) PlayerController.init(context)
        else permissionLauncher.launch(Manifest.permission.READ_MEDIA_AUDIO)
    }

    // ── Состояние из PlayerController ──
    val currentTrack by PlayerController.currentTrack.collectAsState()
    val isPlaying by PlayerController.isPlaying.collectAsState()
    val currentPositionMs by PlayerController.currentPositionMs.collectAsState()
    val durationMs by PlayerController.durationMs.collectAsState()
    val volume by PlayerController.volume.collectAsState()
    val autoMixEnabled by PlayerController.autoMixEnabled.collectAsState()
    val isMixing by PlayerController.isMixing.collectAsState()

    val trackTitle = currentTrack?.title ?: "No track"
    val artistName = currentTrack?.artist ?: "—"

    // ── Drag-driven анимация: 0f = мини, 1f = полный ──
    val expandProgress = remember { Animatable(0f) }
    var screenHeightPx by remember { mutableStateOf(1f) }

    fun animateExpand() {
        scope.launch {
            expandProgress.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        }
    }

    fun animateCollapse() {
        scope.launch {
            expandProgress.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        }
    }

    // Мини-плеер исчезает быстро
    val miniAlpha = (1f - expandProgress.value * 3f).coerceIn(0f, 1f)

    val rootBackdrop: LayerBackdrop = rememberLayerBackdrop()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { screenHeightPx = it.size.height.toFloat() }
    ) {
        // ── Контент (экраны) — layerBackdrop захватывает фон ──
        // Экраны, использующие drawBackdrop(rootBackdrop), вынесены наружу
        Box(
            modifier = Modifier
                .fillMaxSize()
                .layerBackdrop(rootBackdrop)
        ) {
            when (selectedIndex) {
                0 -> HomeScreen()
                1 -> HomeScreen() // New — пока заглушка
                2 -> HomeScreen() // Library — пока заглушка
                else -> HomeScreen()
            }
        }

        // SettingsScreen как страница — рядом с layerBackdrop, не внутри него ✓
        if (selectedIndex == 3) {
            SettingsScreen(
                autoMixEnabled = autoMixEnabled,
                onAutoMixChange = { PlayerController.setAutoMix(it) },
                onBack = { selectedIndex = 0 },
                backdrop = rootBackdrop
            )
        }

        // ── Mini Player + Bottom Bar ──
        if (miniAlpha > 0.01f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .graphicsLayer { alpha = miniAlpha }
                    // Drag UP на мини-плеере → раскрыть
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                if (screenHeightPx > 0f) {
                                    val delta = -dragAmount / screenHeightPx
                                    scope.launch {
                                        expandProgress.snapTo(
                                            (expandProgress.value + delta).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                            },
                            onDragEnd = {
                                if (expandProgress.value > 0.15f) animateExpand()
                                else animateCollapse()
                            },
                            onDragCancel = { animateCollapse() }
                        )
                    }
            ) {
                MiniPlayer(
                    trackTitle = trackTitle,
                    artistName = artistName,
                    isPlaying = isPlaying,
                    albumArtUri = currentTrack?.albumArtUri,
                    backdrop = rootBackdrop,
                    onExpand = { animateExpand() },
                    onPlayPause = { PlayerController.togglePlayPause(context) }
                )

                BottomBar(
                    selectedIndex = selectedIndex,
                    onItemSelected = { selectedIndex = it },
                    backdrop = rootBackdrop
                )

                Spacer(
                    modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
                )
            }
        }

        // ── Full Player — обложка летит из мини-плеера ──
        FullPlayer(
            expandProgress = expandProgress.value,
            trackTitle = trackTitle,
            artistName = artistName,
            isPlaying = isPlaying,
            albumArtUri = currentTrack?.albumArtUri,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            volume = volume,
            isMixing = isMixing,
            onClose = { animateCollapse() },
            onDrag = { dragAmountPx ->
                if (screenHeightPx > 0f) {
                    val delta = dragAmountPx / screenHeightPx
                    scope.launch {
                        expandProgress.snapTo(
                            (expandProgress.value - delta).coerceIn(0f, 1f)
                        )
                    }
                }
            },
            onDragEnd = {
                if (expandProgress.value < 0.85f) animateCollapse()
                else animateExpand()
            },
            onPlayPause = { PlayerController.togglePlayPause(context) },
            onSkipNext = { PlayerController.skipNext(context) },
            onSkipPrevious = { PlayerController.skipPrevious(context) },
            onSeek = { PlayerController.seekTo(it) },
            onVolumeChange = { PlayerController.setVolume(it) },
            onOpenSettings = { settingsOpen = true }
        )

        // ── Settings overlay (из плеера) ──
        AnimatedVisibility(
            visible = settingsOpen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(340, easing = FastOutSlowInEasing)
            ) + fadeIn(animationSpec = tween(250)),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            ) + fadeOut(animationSpec = tween(200))
        ) {
            SettingsScreen(
                autoMixEnabled = autoMixEnabled,
                onAutoMixChange = { PlayerController.setAutoMix(it) },
                onBack = { settingsOpen = false },
                backdrop = rootBackdrop
            )
        }
    }
}
