package com.jtcamp.plansup.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.VibratorManager
import com.jtcamp.plansup.data.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra("taskId", -1)
        if (taskId == -1L) return

        playAlert(context)

        NotificationHelper.createChannel(context)

        CoroutineScope(Dispatchers.IO).launch {
            val task = AppDatabase.getInstance(context).planTaskDao().getTaskById(taskId) ?: return@launch
            NotificationHelper.showNotification(context, taskId, task.title)
        }
    }

    private fun playAlert(context: Context) {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION) ?: return
        try {
            val player = MediaPlayer.create(context, uri)
            player?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()
            )
            player?.setOnCompletionListener { it.release() }
            player?.start()
        } catch (_: Exception) {
        }

        try {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } catch (_: Exception) {
        }
    }
}
