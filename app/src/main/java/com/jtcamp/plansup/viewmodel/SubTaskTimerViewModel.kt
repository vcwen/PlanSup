package com.jtcamp.plansup.viewmodel

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtcamp.plansup.R
import com.jtcamp.plansup.data.AppDatabase
import com.jtcamp.plansup.data.SubTask
import com.jtcamp.plansup.data.SubTaskType
import com.jtcamp.plansup.timer.TimerForegroundService
import com.jtcamp.plansup.timer.TimerWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class TimerPhase {
    ANNOUNCING,
    COUNTDOWN,
    EXERCISING,
    EXERCISING_TIMED,
    RESTING,
    SUBTASK_RESTING,
    COMPLETED,
}

data class TimerState(
    val subTask: SubTask? = null,
    val currentSet: Int = 1,
    val phase: TimerPhase = TimerPhase.COUNTDOWN,
    val remainingSeconds: Int = 0,
    val totalRestSeconds: Int = 0,
    val countdownSeconds: Int = 3,
    val setRemainingSeconds: Int = 0,
    val setTotalSeconds: Int = 0,
    val pendingSubTaskIds: List<Long> = emptyList(),
    val interSubTaskRestSeconds: Int = 0,
    val currentSubTaskIndex: Int = 0,
    val totalSubTaskCount: Int = 1,
    val isPaused: Boolean = false,
)

class SubTaskTimerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).planTaskDao()

    private val _state = MutableStateFlow(TimerState())
    val state: StateFlow<TimerState> = _state

    private var timerJob: Job? = null

    private val notificationManager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val soundPool = SoundPool.Builder()
        .setMaxStreams(2)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val countdownStartSoundId = soundPool.load(application, R.raw.countdown_start, 1)
    private val countdownEndSoundId = soundPool.load(application, R.raw.countdown_end, 1)

    private var endSoundPlayedForCurrentSet = false

    companion object {
        private const val TAG = "SubTaskTimer"
        private const val CHANNEL_ID = "plansup_timer"
        private const val NOTIFICATION_ID = 1001
        private const val COUNTDOWN_DURATION = 3
        private const val COUNTDOWN_END_SOUND_SECONDS = 3
        private const val TTS_API_URL = "https://open.bigmodel.cn/api/coding/paas/v4/audio/speech"
        private const val TTS_API_KEY = "dbdd8f9ddeb9464abda0666b80fb28af.JhUa9L70E26mVzlF"
    }

    init {
        createNotificationChannel()
    }

    private suspend fun speakAndWait(text: String) {
        try {
            Log.d(TAG, "TTS: requesting audio for: $text")
            val wavBytes = withTimeoutOrNull(15000L) { fetchTtsAudio(text) }
            if (wavBytes == null) {
                Log.e(TAG, "TTS: API call timed out")
                return
            }
            Log.d(TAG, "TTS: got ${wavBytes.size} bytes, playing...")
            withTimeoutOrNull(15000L) { playAudioAndWait(wavBytes) }
            Log.d(TAG, "TTS: playback done")
        } catch (e: Exception) {
            Log.e(TAG, "TTS failed: ${e.message}", e)
        }
    }

    private suspend fun fetchTtsAudio(text: String): ByteArray = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("model", "glm-tts")
            put("input", text)
            put("voice", "female")
            put("speed", 1)
            put("volume", 1)
            put("response_format", "wav")
        }
        val connection = URL(TTS_API_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer $TTS_API_KEY")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = 10000
        connection.readTimeout = 10000
        connection.doOutput = true
        connection.outputStream.use { os ->
            os.write(json.toString().toByteArray(Charsets.UTF_8))
        }
        val code = connection.responseCode
        if (code != 200) {
            val err = connection.errorStream?.bufferedReader()?.readText() ?: "unknown"
            Log.e(TAG, "TTS: API error $code: $err")
            throw Exception("TTS API $code: $err")
        }
        connection.inputStream.use { it.readBytes() }
    }

    private suspend fun playAudioAndWait(wavBytes: ByteArray) = suspendCancellableCoroutine { cont ->
        val tempFile = File(getApplication<Application>().cacheDir, "tts_${System.currentTimeMillis()}.wav")
        tempFile.writeBytes(wavBytes)
        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(tempFile.absolutePath)
            player.setOnCompletionListener {
                player.release()
                tempFile.delete()
                cont.resume(Unit) {}
            }
            player.setOnErrorListener { _, _, _ ->
                player.release()
                tempFile.delete()
                cont.resume(Unit) {}
                true
            }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            player.release()
            tempFile.delete()
            cont.resume(Unit) {}
        }
        cont.invokeOnCancellation {
            try { player.release() } catch (_: Exception) {}
            tempFile.delete()
        }
    }

    private suspend fun runWallClockCountdown(
        totalSeconds: () -> Int,
        onTick: (Int) -> Unit,
        onFinished: () -> Unit,
    ) {
        val total = totalSeconds()
        val endTime = SystemClock.elapsedRealtime() + total * 1000L
        while (currentCoroutineContext().isActive) {
            val remainingMs = endTime - SystemClock.elapsedRealtime()
            if (remainingMs <= 0) {
                onTick(0)
                onFinished()
                break
            }
            val remaining = ((remainingMs + 999) / 1000).toInt()
            onTick(remaining)
            delay(200)
        }
    }

    private fun announceAndStartCountdown() {
        timerJob?.cancel()
        val subTask = _state.value.subTask ?: return
        _state.value = _state.value.copy(phase = TimerPhase.ANNOUNCING)
        ensureForegroundService(subTask.title)
        updateWidget()

        timerJob = viewModelScope.launch {
            speakAndWait("准备开始${subTask.title}")

            _state.value = _state.value.copy(phase = TimerPhase.COUNTDOWN)
            updateWidget()
            playCountdownStartSound()

            runWallClockCountdown(
                totalSeconds = { _state.value.countdownSeconds },
                onTick = { remaining ->
                    _state.value = _state.value.copy(countdownSeconds = remaining)
                    updateWidget()
                },
                onFinished = { onCountdownFinished() },
            )
        }
    }

    fun startSubTask(subTaskId: Long) {
        viewModelScope.launch {
            val subTask = dao.getSubTaskById(subTaskId) ?: return@launch
            endSoundPlayedForCurrentSet = false
            _state.value = TimerState(
                subTask = subTask,
                currentSet = 1,
                phase = TimerPhase.ANNOUNCING,
                countdownSeconds = COUNTDOWN_DURATION,
                totalRestSeconds = subTask.restDurationSeconds,
                setRemainingSeconds = if (subTask.type == SubTaskType.TIMED) subTask.setDurationSeconds else 0,
                setTotalSeconds = if (subTask.type == SubTaskType.TIMED) subTask.setDurationSeconds else 0,
            )
            announceAndStartCountdown()
        }
    }

    fun startSequentialSubTasks(subTaskIds: List<Long>, interRestSeconds: Int) {
        if (subTaskIds.isEmpty()) return
        viewModelScope.launch {
            val firstSubTask = dao.getSubTaskById(subTaskIds.first()) ?: return@launch
            val remaining = subTaskIds.drop(1)
            endSoundPlayedForCurrentSet = false
            _state.value = TimerState(
                subTask = firstSubTask,
                currentSet = 1,
                phase = TimerPhase.ANNOUNCING,
                countdownSeconds = COUNTDOWN_DURATION,
                totalRestSeconds = firstSubTask.restDurationSeconds,
                setRemainingSeconds = if (firstSubTask.type == SubTaskType.TIMED) firstSubTask.setDurationSeconds else 0,
                setTotalSeconds = if (firstSubTask.type == SubTaskType.TIMED) firstSubTask.setDurationSeconds else 0,
                pendingSubTaskIds = remaining,
                interSubTaskRestSeconds = interRestSeconds,
                currentSubTaskIndex = 1,
                totalSubTaskCount = subTaskIds.size,
            )
            announceAndStartCountdown()
        }
    }

    private fun startCountdown() {
        timerJob?.cancel()
        playCountdownStartSound()
        updateWidget()
        timerJob = viewModelScope.launch {
            runWallClockCountdown(
                totalSeconds = { _state.value.countdownSeconds },
                onTick = { remaining ->
                    _state.value = _state.value.copy(countdownSeconds = remaining)
                    updateWidget()
                },
                onFinished = { onCountdownFinished() },
            )
        }
    }

    private fun onCountdownFinished() {
        val current = _state.value
        val subTask = current.subTask ?: return
        val phase = if (subTask.type == SubTaskType.TIMED) {
            TimerPhase.EXERCISING_TIMED
        } else {
            TimerPhase.EXERCISING
        }
        _state.value = current.copy(phase = phase)
        updateWidget()

        if (phase == TimerPhase.EXERCISING_TIMED) {
            if (subTask.setDurationSeconds <= COUNTDOWN_END_SOUND_SECONDS) {
                playCountdownEndSound()
            }
            startSetTimer()
        }
    }

    private fun startSetTimer() {
        endSoundPlayedForCurrentSet = false
        val endSoundPlayedAt = BooleanArray(1)
        timerJob?.cancel()
        val totalSec = _state.value.setRemainingSeconds
        val endTime = SystemClock.elapsedRealtime() + totalSec * 1000L
        timerJob = viewModelScope.launch {
            while (isActive) {
                val remainingMs = endTime - SystemClock.elapsedRealtime()
                if (remainingMs <= 0) {
                    _state.value = _state.value.copy(setRemainingSeconds = 0)
                    updateWidget()
                    onSetTimerFinished()
                    break
                }
                val newRemaining = ((remainingMs + 999) / 1000).toInt()
                if (newRemaining == COUNTDOWN_END_SOUND_SECONDS && !endSoundPlayedAt[0]) {
                    endSoundPlayedAt[0] = true
                    playCountdownEndSound()
                }
                _state.value = _state.value.copy(setRemainingSeconds = newRemaining)
                updateWidget()
                delay(200)
            }
        }
    }

    private fun onSetTimerFinished() {
        completeSet()
    }

    fun completeSet() {
        val current = _state.value
        val subTask = current.subTask ?: return

        if (current.currentSet >= subTask.totalSets) {
            completeSubTask()
            return
        }

        _state.value = current.copy(
            phase = TimerPhase.RESTING,
            remainingSeconds = subTask.restDurationSeconds,
            totalRestSeconds = subTask.restDurationSeconds,
        )
        updateWidget()
        startRestCountdown()
    }

    private fun startRestCountdown() {
        timerJob?.cancel()
        updateWidget()
        timerJob = viewModelScope.launch {
            runWallClockCountdown(
                totalSeconds = { _state.value.remainingSeconds },
                onTick = { remaining ->
                    _state.value = _state.value.copy(remainingSeconds = remaining)
                    updateWidget()
                },
                onFinished = { onRestFinished() },
            )
        }
    }

    private fun onRestFinished() {
        val current = _state.value
        val subTask = current.subTask ?: return
        val nextSet = current.currentSet + 1

        showSetRestCompleteNotification(nextSet)

        _state.value = current.copy(
            phase = TimerPhase.COUNTDOWN,
            currentSet = nextSet,
            countdownSeconds = COUNTDOWN_DURATION,
            setRemainingSeconds = subTask.setDurationSeconds,
            setTotalSeconds = subTask.setDurationSeconds,
        )
        updateWidget()
        startCountdown()
    }

    private fun completeSubTask() {
        timerJob?.cancel()
        val current = _state.value
        val subTask = current.subTask ?: return

        viewModelScope.launch {
            dao.updateSubTask(subTask.copy(isCompleted = true))
        }

        if (current.pendingSubTaskIds.isNotEmpty()) {
            startInterSubTaskRest()
        } else {
            stopForegroundService()
            _state.value = current.copy(phase = TimerPhase.COMPLETED)
            updateWidget()
        }
    }

    private fun startInterSubTaskRest() {
        val current = _state.value
        val nextId = current.pendingSubTaskIds.first()
        val restSeconds = current.interSubTaskRestSeconds

        if (restSeconds <= 0) {
            startNextSubTask(nextId)
            return
        }

        _state.value = current.copy(
            phase = TimerPhase.SUBTASK_RESTING,
            remainingSeconds = restSeconds,
            totalRestSeconds = restSeconds,
        )
        updateWidget()

        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            runWallClockCountdown(
                totalSeconds = { _state.value.remainingSeconds },
                onTick = { remaining ->
                    _state.value = _state.value.copy(remainingSeconds = remaining)
                    updateWidget()
                },
                onFinished = {
                    val id = _state.value.pendingSubTaskIds.first()
                    startNextSubTask(id)
                },
            )
        }
    }

    fun skipSubTaskRest() {
        timerJob?.cancel()
        val current = _state.value
        if (current.pendingSubTaskIds.isEmpty()) return
        startNextSubTask(current.pendingSubTaskIds.first())
    }

    private fun startNextSubTask(subTaskId: Long) {
        viewModelScope.launch {
            val subTask = dao.getSubTaskById(subTaskId) ?: return@launch
            val current = _state.value
            val remaining = current.pendingSubTaskIds.drop(1)
            endSoundPlayedForCurrentSet = false
            _state.value = current.copy(
                subTask = subTask,
                currentSet = 1,
                phase = TimerPhase.ANNOUNCING,
                countdownSeconds = COUNTDOWN_DURATION,
                totalRestSeconds = subTask.restDurationSeconds,
                setRemainingSeconds = if (subTask.type == SubTaskType.TIMED) subTask.setDurationSeconds else 0,
                setTotalSeconds = if (subTask.type == SubTaskType.TIMED) subTask.setDurationSeconds else 0,
                pendingSubTaskIds = remaining,
                currentSubTaskIndex = current.currentSubTaskIndex + 1,
            )
            announceAndStartCountdown()
        }
    }

    fun skipRest() {
        timerJob?.cancel()
        onRestFinished()
    }

    fun stop() {
        timerJob?.cancel()
        timerJob = null
        _state.value = TimerState()
        stopForegroundService()
        updateWidget()
    }

    fun togglePause() {
        val current = _state.value
        if (current.isPaused) resumeTimer() else pauseTimer()
    }

    private fun pauseTimer() {
        val current = _state.value
        if (current.isPaused) return
        timerJob?.cancel()
        timerJob = null
        _state.value = current.copy(isPaused = true)
        updateWidget()
    }

    private fun resumeTimer() {
        val current = _state.value
        if (!current.isPaused) return
        _state.value = current.copy(isPaused = false)
        updateWidget()
        when (current.phase) {
            TimerPhase.COUNTDOWN -> {
                timerJob = viewModelScope.launch {
                    runWallClockCountdown(
                        totalSeconds = { _state.value.countdownSeconds },
                        onTick = { remaining -> _state.value = _state.value.copy(countdownSeconds = remaining) },
                        onFinished = { onCountdownFinished() },
                    )
                }
            }
            TimerPhase.EXERCISING -> { }
            TimerPhase.EXERCISING_TIMED -> {
                val endSoundPlayed = BooleanArray(1)
                timerJob = viewModelScope.launch {
                    val totalSec = _state.value.setRemainingSeconds
                    val endTime = SystemClock.elapsedRealtime() + totalSec * 1000L
                    while (isActive) {
                        val remainingMs = endTime - SystemClock.elapsedRealtime()
                        if (remainingMs <= 0) {
                            _state.value = _state.value.copy(setRemainingSeconds = 0)
                            updateWidget()
                            onSetTimerFinished()
                            break
                        }
                        val newRemaining = ((remainingMs + 999) / 1000).toInt()
                        if (newRemaining == COUNTDOWN_END_SOUND_SECONDS && !endSoundPlayed[0]) {
                            endSoundPlayed[0] = true
                            playCountdownEndSound()
                        }
                        _state.value = _state.value.copy(setRemainingSeconds = newRemaining)
                        updateWidget()
                        delay(200)
                    }
                }
            }
            TimerPhase.RESTING -> {
                timerJob = viewModelScope.launch {
                    runWallClockCountdown(
                        totalSeconds = { _state.value.remainingSeconds },
                        onTick = { remaining -> _state.value = _state.value.copy(remainingSeconds = remaining) },
                        onFinished = { onRestFinished() },
                    )
                }
            }
            TimerPhase.SUBTASK_RESTING -> {
                timerJob = viewModelScope.launch {
                    runWallClockCountdown(
                        totalSeconds = { _state.value.remainingSeconds },
                        onTick = { remaining -> _state.value = _state.value.copy(remainingSeconds = remaining) },
                        onFinished = {
                            val id = _state.value.pendingSubTaskIds.first()
                            startNextSubTask(id)
                        },
                    )
                }
            }
            else -> { }
        }
    }

    fun restartPhase() {
        val current = _state.value
        timerJob?.cancel()
        timerJob = null
        _state.value = current.copy(isPaused = false)
        when (current.phase) {
            TimerPhase.COUNTDOWN -> {
                _state.value = _state.value.copy(countdownSeconds = COUNTDOWN_DURATION)
                startCountdown()
            }
            TimerPhase.EXERCISING -> {
                updateWidget()
            }
            TimerPhase.EXERCISING_TIMED -> {
                endSoundPlayedForCurrentSet = false
                _state.value = _state.value.copy(setRemainingSeconds = _state.value.setTotalSeconds)
                startSetTimer()
            }
            TimerPhase.RESTING -> {
                _state.value = _state.value.copy(remainingSeconds = _state.value.totalRestSeconds)
                startRestCountdown()
            }
            TimerPhase.SUBTASK_RESTING -> {
                val restSeconds = _state.value.interSubTaskRestSeconds
                _state.value = _state.value.copy(remainingSeconds = restSeconds, totalRestSeconds = restSeconds)
                timerJob = viewModelScope.launch {
                    runWallClockCountdown(
                        totalSeconds = { _state.value.remainingSeconds },
                        onTick = { remaining -> _state.value = _state.value.copy(remainingSeconds = remaining) },
                        onFinished = {
                            val id = _state.value.pendingSubTaskIds.first()
                            startNextSubTask(id)
                        },
                    )
                }
            }
            else -> { }
        }
    }

    private fun playCountdownStartSound() {
        soundPool.play(countdownStartSoundId, 1f, 1f, 1, 0, 1f)
    }

    private fun playCountdownEndSound() {
        soundPool.play(countdownEndSoundId, 1f, 1f, 1, 0, 1f)
    }

    private fun showSetRestCompleteNotification(nextSet: Int) {
        val subTask = _state.value.subTask ?: return
        val notification = NotificationCompat.Builder(getApplication(), CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("休息结束")
            .setContentText("${subTask.title} - 开始第 ${nextSet}/${subTask.totalSets} 组")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "训练计时器",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "组间休息倒计时提醒"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private var isForegroundServiceRunning = false

    private val wakeLock: PowerManager.WakeLock =
        (application.getSystemService(Context.POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "plansup:timer_countdown")

    private var lastWidgetUpdateKey = Pair(-1, false)

    private fun updateWidget() {
        val state = _state.value
        val key = when (state.phase) {
            TimerPhase.COUNTDOWN -> state.countdownSeconds
            TimerPhase.EXERCISING_TIMED -> state.setRemainingSeconds
            TimerPhase.RESTING, TimerPhase.SUBTASK_RESTING -> state.remainingSeconds
            else -> -1
        }
        val dedupKey = key to state.isPaused
        if (dedupKey == lastWidgetUpdateKey && state.phase != TimerPhase.COMPLETED) return
        lastWidgetUpdateKey = dedupKey
        TimerWidgetUpdater.update(getApplication(), state)
        if (isForegroundServiceRunning) {
            TimerForegroundService.updateNotification(getApplication(), state)
        }
    }

    private fun ensureForegroundService(title: String) {
        if (!isForegroundServiceRunning) {
            isForegroundServiceRunning = true
            TimerForegroundService.bindViewModel(this)
            TimerForegroundService.start(getApplication(), title)
        }
        if (!wakeLock.isHeld) {
            wakeLock.acquire(4 * 60 * 60 * 1000L)
        }
    }

    private fun stopForegroundService() {
        if (isForegroundServiceRunning) {
            isForegroundServiceRunning = false
            TimerForegroundService.unbindViewModel()
            TimerForegroundService.stop(getApplication())
        }
        if (wakeLock.isHeld) {
            wakeLock.release()
        }
    }

    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        stopForegroundService()
        soundPool.release()
    }
}
