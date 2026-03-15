package com.liquidmusicglass.engine

import android.content.ContentUris
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.PI

/**
 * Движок воспроизведения с поддержкой AutoMix (crossfade)
 * как в Apple Music.
 *
 * Принцип работы AutoMix:
 * - Два MediaPlayer: primary (текущий) и crossfade (следующий)
 * - Когда до конца текущего трека остаётся [CROSSFADE_DURATION_MS],
 *   запускается следующий трек на crossfade-плеере
 * - Primary плавно затухает (1.0 → 0.0), crossfade плавно нарастает (0.0 → 1.0)
 * - Кривая громкости — equal-power (косинусная) для естественного восприятия
 * - После завершения: crossfade становится primary, старый освобождается
 * - UI показывает "Mixing" во время перехода
 */
object PlayerController {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // ── Два плеера для crossfade ──
    private var primaryPlayer: MediaPlayer? = null
    private var crossfadePlayer: MediaPlayer? = null

    private var positionJob: Job? = null
    private var crossfadeJob: Job? = null
    private var audioManager: AudioManager? = null

    private var queue = listOf<Track>()
    private var currentIndex = -1
    private var crossfadeStarted = false

    // Длительность кроссфейда (мс) — как в Apple Music (~12 сек)
    private const val CROSSFADE_DURATION_MS = 12_000L
    // Шаг обновления громкости при кроссфейде (мс)
    private const val CROSSFADE_STEP_MS = 50L
    // Шаг обновления позиции (мс)
    private const val POSITION_UPDATE_MS = 200L

    // ── Реактивное состояние ──

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack: StateFlow<Track?> = _currentTrack

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _volume = MutableStateFlow(0.7f)
    val volume: StateFlow<Float> = _volume

    private val _queue = MutableStateFlow<List<Track>>(emptyList())
    val queueFlow: StateFlow<List<Track>> = _queue

    private val _autoMixEnabled = MutableStateFlow(true)
    val autoMixEnabled: StateFlow<Boolean> = _autoMixEnabled

    /** true когда идёт кроссфейд-переход между треками */
    private val _isMixing = MutableStateFlow(false)
    val isMixing: StateFlow<Boolean> = _isMixing

    // ═══════════════════════════════════════════════════
    // Инициализация — сканирование музыки
    // ═══════════════════════════════════════════════════

    fun init(context: Context) {
        audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        audioManager?.let { am ->
            val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            _volume.value = if (max > 0) current.toFloat() / max.toFloat() else 0.7f
        }

        scope.launch(Dispatchers.IO) {
            val tracks = scanMusic(context)
            queue = tracks
            _queue.value = tracks

            if (tracks.isNotEmpty()) {
                currentIndex = 0
                _currentTrack.value = tracks[0]
                _durationMs.value = tracks[0].durationMs
            }
        }
    }

    private fun scanMusic(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(uri, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val title = cursor.getString(titleCol) ?: "Unknown"
                val artist = cursor.getString(artistCol) ?: "Unknown"
                val albumId = cursor.getLong(albumIdCol)
                val duration = cursor.getLong(durationCol)

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id
                )

                tracks.add(
                    Track(
                        id = id,
                        title = title,
                        artist = artist,
                        uri = contentUri,
                        durationMs = duration,
                        albumId = albumId
                    )
                )
            }
        }

        return tracks
    }

    // ═══════════════════════════════════════════════════
    // AutoMix
    // ═══════════════════════════════════════════════════

    fun setAutoMix(enabled: Boolean) {
        _autoMixEnabled.value = enabled
        if (!enabled) {
            cancelCrossfade()
        }
    }

    // ═══════════════════════════════════════════════════
    // Воспроизведение
    // ═══════════════════════════════════════════════════

    fun playTrack(context: Context, index: Int) {
        if (index !in queue.indices) return

        cancelCrossfade()
        releasePrimary()
        releaseCrossfade()

        currentIndex = index
        val track = queue[index]
        _currentTrack.value = track
        _durationMs.value = track.durationMs
        _currentPositionMs.value = 0L
        crossfadeStarted = false

        primaryPlayer = createPlayer(context, track) { mp ->
            mp.start()
            _isPlaying.value = true
            _durationMs.value = mp.duration.toLong()
            applyVolumeToPlayer(mp, _volume.value)
            startPositionUpdates(context)
        }
    }

    fun togglePlayPause(context: Context) {
        val mp = primaryPlayer
        if (mp == null) {
            if (queue.isNotEmpty()) {
                playTrack(context, if (currentIndex >= 0) currentIndex else 0)
            }
            return
        }

        if (mp.isPlaying) {
            mp.pause()
            crossfadePlayer?.pause()
            _isPlaying.value = false
            stopPositionUpdates()
        } else {
            mp.start()
            if (_isMixing.value) crossfadePlayer?.start()
            _isPlaying.value = true
            startPositionUpdates(context)
        }
    }

    fun skipNext(context: Context) {
        if (queue.isEmpty()) return
        val nextIndex = (currentIndex + 1) % queue.size
        playTrack(context, nextIndex)
    }

    fun skipPrevious(context: Context) {
        if (queue.isEmpty()) return

        val mp = primaryPlayer
        if (mp != null && safeGetPosition(mp) > 3000) {
            mp.seekTo(0)
            _currentPositionMs.value = 0L
            crossfadeStarted = false
            cancelCrossfade()
            return
        }

        val prevIndex = if (currentIndex > 0) currentIndex - 1 else queue.size - 1
        playTrack(context, prevIndex)
    }

    fun seekTo(positionMs: Long) {
        primaryPlayer?.seekTo(positionMs.toInt())
        _currentPositionMs.value = positionMs

        // Если перемотали назад за точку кроссфейда — отменить кроссфейд
        val duration = _durationMs.value
        val remaining = duration - positionMs
        if (remaining > CROSSFADE_DURATION_MS) {
            cancelCrossfade()
            crossfadeStarted = false
        }
    }

    fun setVolume(vol: Float) {
        val clamped = vol.coerceIn(0f, 1f)
        _volume.value = clamped

        // Применить к обоим плеерам (с учётом crossfade fade-уровней)
        // Если не в режиме mixing — просто ставим primary
        if (!_isMixing.value) {
            primaryPlayer?.let { applyVolumeToPlayer(it, clamped) }
        }
        // Если в mixing — громкость обновится на следующем шаге crossfade

        audioManager?.let { am ->
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val target = (clamped * max).toInt()
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
        }
    }

    // ═══════════════════════════════════════════════════
    // Создание плеера
    // ═══════════════════════════════════════════════════

    private val audioAttributes = AudioAttributes.Builder()
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .build()

    private fun createPlayer(
        context: Context,
        track: Track,
        onPrepared: (MediaPlayer) -> Unit
    ): MediaPlayer {
        return MediaPlayer().apply {
            setAudioAttributes(audioAttributes)
            setDataSource(context, track.uri)
            setOnPreparedListener { mp -> onPrepared(mp) }
            setOnErrorListener { _, _, _ ->
                _isPlaying.value = false
                true
            }
            prepareAsync()
        }
    }

    private fun applyVolumeToPlayer(mp: MediaPlayer, vol: Float) {
        try {
            mp.setVolume(vol, vol)
        } catch (_: IllegalStateException) { }
    }

    // ═══════════════════════════════════════════════════
    // Обновление позиции + запуск crossfade
    // ═══════════════════════════════════════════════════

    private fun startPositionUpdates(context: Context) {
        stopPositionUpdates()
        positionJob = scope.launch {
            while (true) {
                try {
                    val mp = primaryPlayer
                    if (mp != null && mp.isPlaying) {
                        val pos = mp.currentPosition.toLong()
                        val dur = mp.duration.toLong()
                        _currentPositionMs.value = pos
                        _durationMs.value = dur

                        // Проверяем нужно ли запустить кроссфейд
                        val remaining = dur - pos
                        if (_autoMixEnabled.value
                            && !crossfadeStarted
                            && remaining in 1..CROSSFADE_DURATION_MS
                            && queue.size > 1
                        ) {
                            crossfadeStarted = true
                            startCrossfade(context, remaining)
                        }
                    }
                } catch (_: IllegalStateException) { }
                delay(POSITION_UPDATE_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionJob?.cancel()
        positionJob = null
    }

    // ═══════════════════════════════════════════════════
    // Кроссфейд — сердце AutoMix
    //
    // Equal-power crossfade:
    //   outGain = cos(progress * π/2)
    //   inGain  = cos((1 - progress) * π/2)
    //
    // Это даёт -3dB в точке пересечения (50%),
    // что воспринимается ухом как постоянная громкость.
    // ═══════════════════════════════════════════════════

    private fun startCrossfade(context: Context, remainingMs: Long) {
        val nextIndex = (currentIndex + 1) % queue.size
        val nextTrack = queue[nextIndex]

        _isMixing.value = true

        // Подготовить следующий плеер
        crossfadePlayer = createPlayer(context, nextTrack) { mp ->
            // Начать с нулевой громкости
            applyVolumeToPlayer(mp, 0f)
            mp.start()

            // Запустить плавный переход
            crossfadeJob = scope.launch {
                val crossfadeDuration = remainingMs.coerceAtMost(CROSSFADE_DURATION_MS)
                val startTime = System.currentTimeMillis()

                while (true) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val progress = (elapsed.toFloat() / crossfadeDuration).coerceIn(0f, 1f)

                    // Equal-power crossfade кривая
                    val outGain = cos(progress * PI.toFloat() / 2f)
                    val inGain = cos((1f - progress) * PI.toFloat() / 2f)

                    val masterVol = _volume.value

                    primaryPlayer?.let { applyVolumeToPlayer(it, outGain * masterVol) }
                    crossfadePlayer?.let { applyVolumeToPlayer(it, inGain * masterVol) }

                    if (progress >= 1f) {
                        // Кроссфейд завершён — переключаемся
                        finishCrossfade(nextIndex, nextTrack, context)
                        break
                    }

                    delay(CROSSFADE_STEP_MS)
                }
            }
        }
    }

    private fun finishCrossfade(nextIndex: Int, nextTrack: Track, context: Context) {
        // Остановить и освободить старый primary
        releasePrimary()

        // Crossfade → primary
        primaryPlayer = crossfadePlayer
        crossfadePlayer = null

        // Обновить состояние
        currentIndex = nextIndex
        _currentTrack.value = nextTrack
        crossfadeStarted = false
        _isMixing.value = false

        // Убедиться что громкость на максимуме
        primaryPlayer?.let { applyVolumeToPlayer(it, _volume.value) }

        // Обновить duration нового трека
        try {
            primaryPlayer?.let {
                _durationMs.value = it.duration.toLong()
            }
        } catch (_: IllegalStateException) { }

        // OnCompletion для нового primary
        primaryPlayer?.setOnCompletionListener {
            skipNext(context)
        }

        // Перезапустить обновление позиции для нового трека
        startPositionUpdates(context)
    }

    private fun cancelCrossfade() {
        crossfadeJob?.cancel()
        crossfadeJob = null
        _isMixing.value = false
        releaseCrossfade()
    }

    // ═══════════════════════════════════════════════════
    // Освобождение ресурсов
    // ═══════════════════════════════════════════════════

    private fun releasePrimary() {
        try {
            primaryPlayer?.stop()
        } catch (_: Exception) { }
        try {
            primaryPlayer?.release()
        } catch (_: Exception) { }
        primaryPlayer = null
    }

    private fun releaseCrossfade() {
        try {
            crossfadePlayer?.stop()
        } catch (_: Exception) { }
        try {
            crossfadePlayer?.release()
        } catch (_: Exception) { }
        crossfadePlayer = null
    }

    private fun safeGetPosition(mp: MediaPlayer): Int {
        return try {
            mp.currentPosition
        } catch (_: IllegalStateException) {
            0
        }
    }
}
