package com.jtcamp.plansup.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtcamp.plansup.data.AppDatabase
import com.jtcamp.plansup.data.PlanTask
import com.jtcamp.plansup.data.RepeatMode
import com.jtcamp.plansup.data.SubTask
import com.jtcamp.plansup.data.SubTaskType
import com.jtcamp.plansup.data.TaskWithSubTasks
import com.jtcamp.plansup.reminder.AlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).planTaskDao()

    private var _taskId: Long = 0

    val taskWithSubTasks: StateFlow<TaskWithSubTasks?> = run {
        dao.getTaskWithSubTasks(0)
            .map { null }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    private var _flow: StateFlow<TaskWithSubTasks?> = taskWithSubTasks

    fun setTaskId(taskId: Long) {
        if (_taskId == taskId) return
        _taskId = taskId
        _flow = dao.getTaskWithSubTasks(taskId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    val taskWithSubTasksFlow: StateFlow<TaskWithSubTasks?>
        get() = _flow

    fun addSubTask(
        title: String,
        type: SubTaskType = SubTaskType.REP_COUNT,
        totalSets: Int = 3,
        setDetail: String = "",
        setDurationSeconds: Int = 60,
        restDurationSeconds: Int = 300,
    ) {
        viewModelScope.launch {
            val currentSubTasks = _flow.value?.subTasks ?: emptyList()
            dao.insertSubTask(
                SubTask(
                    taskId = _taskId,
                    title = title,
                    type = type,
                    totalSets = totalSets,
                    setDetail = setDetail,
                    setDurationSeconds = setDurationSeconds,
                    restDurationSeconds = restDurationSeconds,
                    sortOrder = currentSubTasks.size,
                )
            )
        }
    }

    fun deleteSubTask(subTask: SubTask) {
        viewModelScope.launch {
            dao.deleteSubTask(subTask)
        }
    }

    fun updateSubTask(subTask: SubTask) {
        viewModelScope.launch {
            dao.updateSubTask(subTask)
        }
    }

    fun toggleSubTaskCompletion(subTask: SubTask) {
        viewModelScope.launch {
            dao.updateSubTask(subTask.copy(isCompleted = !subTask.isCompleted))
        }
    }

    fun updateTaskCompletion(task: PlanTask) {
        viewModelScope.launch {
            dao.updateTask(task)
        }
    }

    fun updateTaskBasicInfo(task: PlanTask, title: String, description: String) {
        viewModelScope.launch {
            dao.updateTask(task.copy(title = title, description = description))
        }
    }

    fun updateTaskSettings(
        task: PlanTask,
        restSeconds: Int,
        repeatMode: RepeatMode,
        repeatDaysOfWeek: String,
        reminderTime: String,
        reminderDate: String,
    ) {
        viewModelScope.launch {
            val updated = task.copy(
                interSubTaskRestSeconds = restSeconds,
                repeatMode = repeatMode,
                repeatDaysOfWeek = repeatDaysOfWeek,
                reminderTime = reminderTime,
                reminderDate = reminderDate,
            )
            dao.updateTask(updated)

            AlarmScheduler.cancel(getApplication(), task.id)
            if (reminderTime.isNotBlank()) {
                val millis = AlarmScheduler.nextReminderMillis(
                    reminderTime,
                    if (repeatMode == RepeatMode.NONE) reminderDate else "",
                    task.date,
                )
                AlarmScheduler.schedule(getApplication(), task.id, millis)
            }
        }
    }
}
