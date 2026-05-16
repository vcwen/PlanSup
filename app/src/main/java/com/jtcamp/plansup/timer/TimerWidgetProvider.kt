package com.jtcamp.plansup.timer

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.jtcamp.plansup.MainActivity
import com.jtcamp.plansup.R
import com.jtcamp.plansup.viewmodel.TimerPhase
import com.jtcamp.plansup.viewmodel.TimerState

class TimerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.timer_widget)
            val intent = Intent(context, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.timer_widget_root, pi)
            appWidgetManager.updateAppWidget(id, views)
        }
    }
}

object TimerWidgetUpdater {

    fun update(context: Context, state: TimerState) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val widgetIds = appWidgetManager.getAppWidgetIds(
            android.content.ComponentName(context, TimerWidgetProvider::class.java)
        )
        if (widgetIds.isEmpty()) return

        val views = RemoteViews(context.packageName, R.layout.timer_widget)

        val phaseText: String
        val timeText: String
        val setText: String

        val subTask = state.subTask
        when (state.phase) {
            TimerPhase.ANNOUNCING -> {
                phaseText = "准备开始"
                timeText = ""
                setText = subTask?.let { "第 ${state.currentSet}/${it.totalSets} 组" } ?: ""
            }
            TimerPhase.COUNTDOWN -> {
                phaseText = "倒计时"
                timeText = "${state.countdownSeconds}"
                setText = subTask?.let { "第 ${state.currentSet}/${it.totalSets} 组" } ?: ""
            }
            TimerPhase.EXERCISING -> {
                phaseText = "训练中"
                timeText = ""
                setText = subTask?.let { "第 ${state.currentSet}/${it.totalSets} 组" } ?: ""
            }
            TimerPhase.EXERCISING_TIMED -> {
                phaseText = if (state.isPaused) "训练中 ⏸" else "训练中"
                timeText = formatSeconds(state.setRemainingSeconds)
                setText = subTask?.let { "第 ${state.currentSet}/${it.totalSets} 组" } ?: ""
            }
            TimerPhase.RESTING -> {
                phaseText = if (state.isPaused) "休息中 ⏸" else "休息中"
                timeText = formatSeconds(state.remainingSeconds)
                setText = if (subTask != null) "下一组: ${state.currentSet + 1}/${subTask.totalSets}" else ""
            }
            TimerPhase.SUBTASK_RESTING -> {
                phaseText = if (state.isPaused) "休息中 ⏸" else "子任务间休息"
                timeText = formatSeconds(state.remainingSeconds)
                setText = "下一个 (${state.currentSubTaskIndex + 1}/${state.totalSubTaskCount})"
            }
            TimerPhase.COMPLETED -> {
                phaseText = "已完成"
                timeText = "✓"
                setText = subTask?.let { "共 ${it.totalSets} 组" } ?: ""
            }
        }

        views.setTextViewText(R.id.widget_phase, phaseText)
        views.setTextViewText(R.id.widget_title, subTask?.title ?: "计划监督")
        views.setTextViewText(R.id.widget_time, timeText)
        views.setTextViewText(R.id.widget_set_info, setText)

        views.setViewVisibility(R.id.widget_time,
            if (timeText.isBlank()) View.GONE else View.VISIBLE
        )

        val intent = Intent(context, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        views.setOnClickPendingIntent(R.id.widget_phase, pi)

        for (id in widgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    private fun formatSeconds(seconds: Int): String {
        val min = seconds / 60
        val sec = seconds % 60
        return "%d:%02d".format(min, sec)
    }
}
