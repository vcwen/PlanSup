package com.jtcamp.plansup.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import com.jtcamp.plansup.MainActivity
import com.jtcamp.plansup.R
import com.jtcamp.plansup.viewmodel.SubTaskTimerViewModel
import com.jtcamp.plansup.viewmodel.TimerPhase
import com.jtcamp.plansup.viewmodel.TimerState

class TimerForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "plansup_timer_foreground"
        private const val NOTIFICATION_ID = 2001

        private const val ACTION_START = "com.jtcamp.plansup.action.TIMER_START"
        private const val ACTION_STOP_SERVICE = "com.jtcamp.plansup.action.TIMER_STOP"
        private const val ACTION_UPDATE = "com.jtcamp.plansup.action.TIMER_UPDATE"
        private const val ACTION_SKIP = "com.jtcamp.plansup.action.TIMER_SKIP"
        private const val ACTION_STOP_TIMER = "com.jtcamp.plansup.action.TIMER_STOP_TIMER"
        private const val ACTION_COMPLETE_SET = "com.jtcamp.plansup.action.TIMER_COMPLETE_SET"
        private const val ACTION_TOGGLE_PAUSE = "com.jtcamp.plansup.action.TIMER_TOGGLE_PAUSE"
        private const val ACTION_RESTART = "com.jtcamp.plansup.action.TIMER_RESTART"

        private const val EXTRA_TITLE = "extra_title"

        private var mediaSession: MediaSessionCompat? = null
        private var viewModel: SubTaskTimerViewModel? = null

        fun bindViewModel(vm: SubTaskTimerViewModel) {
            viewModel = vm
        }

        fun unbindViewModel() {
            viewModel = null
        }

        fun start(context: Context, title: String) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.startService(intent)
        }

        fun updateNotification(context: Context, state: TimerState) {
            val intent = Intent(context, TimerForegroundService::class.java).apply {
                action = ACTION_UPDATE
            }
            intent.putExtra("phase", state.phase.name)
            intent.putExtra("title", state.subTask?.title ?: "")
            intent.putExtra("currentSet", state.currentSet)
            intent.putExtra("totalSets", state.subTask?.totalSets ?: 1)
            intent.putExtra("remainingSeconds", state.remainingSeconds)
            intent.putExtra("totalRestSeconds", state.totalRestSeconds)
            intent.putExtra("countdownSeconds", state.countdownSeconds)
            intent.putExtra("setRemainingSeconds", state.setRemainingSeconds)
            intent.putExtra("setTotalSeconds", state.setTotalSeconds)
            intent.putExtra("currentSubTaskIndex", state.currentSubTaskIndex)
            intent.putExtra("totalSubTaskCount", state.totalSubTaskCount)
            intent.putExtra("isPaused", state.isPaused)
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        initMediaSession()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "训练中"
                mediaSession?.isActive = true
                startForeground(NOTIFICATION_ID, buildNotification(title, "", null, null))
            }
            ACTION_UPDATE -> {
                val phase = intent.getStringExtra("phase") ?: return START_NOT_STICKY
                val title = intent.getStringExtra("title") ?: ""
                val currentSet = intent.getIntExtra("currentSet", 1)
                val totalSets = intent.getIntExtra("totalSets", 1)
                val remainingSeconds = intent.getIntExtra("remainingSeconds", 0)
                val totalRestSeconds = intent.getIntExtra("totalRestSeconds", 0)
                val countdownSeconds = intent.getIntExtra("countdownSeconds", 3)
                val setRemainingSeconds = intent.getIntExtra("setRemainingSeconds", 0)
                val setTotalSeconds = intent.getIntExtra("setTotalSeconds", 0)
                val isPaused = intent.getBooleanExtra("isPaused", false)
                handleUpdate(
                    phase, title, currentSet, totalSets,
                    remainingSeconds, totalRestSeconds, countdownSeconds,
                    setRemainingSeconds, setTotalSeconds, isPaused,
                )
            }
            ACTION_SKIP -> {
                viewModel?.skipRest()
                viewModel?.skipSubTaskRest()
            }
            ACTION_COMPLETE_SET -> {
                viewModel?.completeSet()
            }
            ACTION_STOP_TIMER -> {
                viewModel?.stop()
            }
            ACTION_TOGGLE_PAUSE -> {
                viewModel?.togglePause()
            }
            ACTION_RESTART -> {
                viewModel?.restartPhase()
            }
            ACTION_STOP_SERVICE -> {
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaSession?.release()
        mediaSession = null
    }

    private fun initMediaSession() {
        if (mediaSession != null) return
        mediaSession = MediaSessionCompat(this, "TimerMediaSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    viewModel?.togglePause()
                }
                override fun onPause() {
                    viewModel?.togglePause()
                }
                override fun onSkipToPrevious() {
                    viewModel?.restartPhase()
                }
                override fun onSkipToNext() {
                    viewModel?.skipRest()
                    viewModel?.skipSubTaskRest()
                }
                override fun onStop() {
                    viewModel?.stop()
                }
            })
        }
    }

    private fun handleUpdate(
        phaseName: String, title: String, currentSet: Int, totalSets: Int,
        remainingSeconds: Int, totalRestSeconds: Int, countdownSeconds: Int,
        setRemainingSeconds: Int, setTotalSeconds: Int, isPaused: Boolean,
    ) {
        val phase = runCatching { TimerPhase.valueOf(phaseName) }.getOrDefault(TimerPhase.COMPLETED)

        val phaseLabel: String
        val timeText: String
        val setInfo: String
        val positionMs: Long
        val durationMs: Long

        val pauseSuffix = if (isPaused) " (已暂停)" else ""

        when (phase) {
            TimerPhase.ANNOUNCING -> {
                phaseLabel = "准备开始"
                timeText = ""
                setInfo = "第 $currentSet/$totalSets 组"
                positionMs = 0L
                durationMs = 0L
            }
            TimerPhase.COUNTDOWN -> {
                phaseLabel = "倒计时$pauseSuffix"
                timeText = "${countdownSeconds}s"
                setInfo = "第 $currentSet/$totalSets 组"
                positionMs = (3 - countdownSeconds) * 1000L
                durationMs = 3000L
            }
            TimerPhase.EXERCISING -> {
                phaseLabel = "训练中"
                timeText = ""
                setInfo = "第 $currentSet/$totalSets 组"
                positionMs = 0L
                durationMs = 0L
            }
            TimerPhase.EXERCISING_TIMED -> {
                phaseLabel = "训练中$pauseSuffix"
                timeText = formatSeconds(setRemainingSeconds)
                setInfo = "第 $currentSet/$totalSets 组"
                positionMs = (setTotalSeconds - setRemainingSeconds) * 1000L
                durationMs = setTotalSeconds * 1000L
            }
            TimerPhase.RESTING -> {
                phaseLabel = "休息中$pauseSuffix"
                timeText = formatSeconds(remainingSeconds)
                setInfo = "下一组: ${currentSet + 1}/$totalSets"
                positionMs = (totalRestSeconds - remainingSeconds) * 1000L
                durationMs = totalRestSeconds * 1000L
            }
            TimerPhase.SUBTASK_RESTING -> {
                phaseLabel = "子任务间休息$pauseSuffix"
                timeText = formatSeconds(remainingSeconds)
                setInfo = ""
                positionMs = 0L
                durationMs = 0L
            }
            TimerPhase.COMPLETED -> {
                phaseLabel = "已完成"
                timeText = "✓"
                setInfo = "共 $totalSets 组"
                positionMs = 0L
                durationMs = 0L
            }
        }

        updateMediaSession(title, phaseLabel, positionMs, durationMs, phase, isPaused)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(title, phaseLabel, timeText, setInfo, phase, isPaused))
    }

    private fun updateMediaSession(
        title: String, phaseLabel: String,
        positionMs: Long, durationMs: Long, phase: TimerPhase, isPaused: Boolean,
    ) {
        val session = mediaSession ?: return

        val state = when {
            phase == TimerPhase.COMPLETED -> PlaybackStateCompat.STATE_STOPPED
            isPaused -> PlaybackStateCompat.STATE_PAUSED
            phase == TimerPhase.EXERCISING -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_PLAYING
        }
        val actions = when (phase) {
            TimerPhase.EXERCISING -> PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
            TimerPhase.EXERCISING_TIMED, TimerPhase.RESTING, TimerPhase.SUBTASK_RESTING, TimerPhase.COUNTDOWN ->
                PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_STOP
            else -> PlaybackStateCompat.ACTION_STOP
        }

        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(state, positionMs, 1f)
                .build()
        )

        session.setMetadata(
            android.support.v4.media.MediaMetadataCompat.Builder()
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, phaseLabel)
                .putLong(android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
                .build()
        )
    }

    private fun buildNotification(
        title: String, phaseLabel: String,
        timeText: String?, setInfo: String?,
        phase: TimerPhase? = null, isPaused: Boolean = false,
    ): Notification {
        createChannel()

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("openTimer", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val contentText = if (timeText.isNullOrBlank()) phaseLabel else "$phaseLabel $timeText"

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(contentText)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession?.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )

        when (phase) {
            TimerPhase.EXERCISING -> {
                builder.addAction(R.drawable.ic_action_restart, "重新开始", actionPendingIntent(ACTION_RESTART))
                builder.addAction(R.drawable.ic_action_complete, "完成本组", actionPendingIntent(ACTION_COMPLETE_SET))
            }
            TimerPhase.EXERCISING_TIMED -> {
                val playPauseIcon = if (isPaused) R.drawable.ic_action_play else R.drawable.ic_action_pause
                val playPauseLabel = if (isPaused) "继续" else "暂停"
                builder.addAction(playPauseIcon, playPauseLabel, actionPendingIntent(ACTION_TOGGLE_PAUSE))
                builder.addAction(R.drawable.ic_action_restart, "重新开始", actionPendingIntent(ACTION_RESTART))
                builder.addAction(R.drawable.ic_action_skip, "跳过本组", actionPendingIntent(ACTION_COMPLETE_SET))
            }
            TimerPhase.COUNTDOWN -> {
                val playPauseIcon = if (isPaused) R.drawable.ic_action_play else R.drawable.ic_action_pause
                val playPauseLabel = if (isPaused) "继续" else "暂停"
                builder.addAction(playPauseIcon, playPauseLabel, actionPendingIntent(ACTION_TOGGLE_PAUSE))
                builder.addAction(R.drawable.ic_action_restart, "重新开始", actionPendingIntent(ACTION_RESTART))
            }
            TimerPhase.RESTING, TimerPhase.SUBTASK_RESTING -> {
                val playPauseIcon = if (isPaused) R.drawable.ic_action_play else R.drawable.ic_action_pause
                val playPauseLabel = if (isPaused) "继续" else "暂停"
                builder.addAction(playPauseIcon, playPauseLabel, actionPendingIntent(ACTION_TOGGLE_PAUSE))
                builder.addAction(R.drawable.ic_action_restart, "重新开始", actionPendingIntent(ACTION_RESTART))
                builder.addAction(R.drawable.ic_action_skip, "跳过休息", actionPendingIntent(ACTION_SKIP))
            }
            else -> {
                builder.addAction(R.drawable.ic_action_stop, "停止", actionPendingIntent(ACTION_STOP_TIMER))
            }
        }

        if (!setInfo.isNullOrBlank()) {
            builder.setSubText(setInfo)
        }

        return builder.build()
    }

    private fun actionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, TimerForegroundService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "训练计时",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "训练计时器运行状态"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun formatSeconds(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "%d:%02d".format(min, sec)
    }
}
