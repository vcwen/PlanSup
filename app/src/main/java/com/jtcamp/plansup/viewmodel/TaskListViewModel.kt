package com.jtcamp.plansup.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jtcamp.plansup.data.AppDatabase
import com.jtcamp.plansup.data.PlanTask
import com.jtcamp.plansup.data.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar

class TaskListViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = AppDatabase.getInstance(application).planTaskDao()

    private val today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private val todayTasks = dao.getTasksByDate(today)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val repeatingTasks = dao.getRepeatingTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val allTasks = dao.getAllTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allTags: StateFlow<List<String>> = dao.getAllTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val selectedTag = MutableStateFlow<String?>(null)

    val tasks: StateFlow<List<PlanTask>> = combine(
        todayTasks,
        repeatingTasks,
        allTasks,
        selectedTag,
    ) { today, repeating, all, tag ->
        val source = if (tag != null) {
            val matching = all.filter { it.tag == tag }
            val repeatingForTag = matching.filter { matchesToday(it) }
            val existingIds = today.map { it.id }.toSet()
            (today.filter { it.tag == tag } + repeatingForTag.filter { it.id !in existingIds })
                .sortedBy { it.id }
        } else {
            val matching = repeating.filter { matchesToday(it) }
            val existingIds = today.map { it.id }.toSet()
            (today + matching.filter { it.id !in existingIds }).sortedBy { it.id }
        }
        source
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _taskDurations = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val taskDurations: StateFlow<Map<Long, Int>> = _taskDurations

    init {
        loadDurations()
    }

    private fun loadDurations() {
        viewModelScope.launch {
            val subTasks = dao.getAllSubTasks()
            _taskDurations.value = subTasks.groupBy { it.taskId }.mapValues { (_, subs) ->
                subs.sumOf { it.totalDurationSeconds() }
            }
        }
    }

    private fun matchesToday(task: PlanTask): Boolean {
        if (task.repeatMode == RepeatMode.NONE) return false
        val now = LocalDate.now()
        return when (task.repeatMode) {
            RepeatMode.DAILY -> true
            RepeatMode.WEEKLY -> {
                val javaDayOfWeek = when (now.dayOfWeek) {
                    DayOfWeek.MONDAY -> Calendar.MONDAY
                    DayOfWeek.TUESDAY -> Calendar.TUESDAY
                    DayOfWeek.WEDNESDAY -> Calendar.WEDNESDAY
                    DayOfWeek.THURSDAY -> Calendar.THURSDAY
                    DayOfWeek.FRIDAY -> Calendar.FRIDAY
                    DayOfWeek.SATURDAY -> Calendar.SATURDAY
                    DayOfWeek.SUNDAY -> Calendar.SUNDAY
                }
                task.repeatDaysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.contains(javaDayOfWeek)
            }
            RepeatMode.MONTHLY -> {
                val createdDay = LocalDate.parse(task.date, DateTimeFormatter.ISO_LOCAL_DATE).dayOfMonth
                now.dayOfMonth == createdDay
            }
            RepeatMode.NONE -> false
        }
    }

    fun selectTag(tag: String?) {
        selectedTag.value = tag
    }

    fun addTask(title: String, description: String = "", tag: String = "") {
        viewModelScope.launch {
            dao.insertTask(
                PlanTask(
                    title = title,
                    description = description,
                    date = today,
                    tag = tag,
                )
            )
        }
    }

    fun deleteTask(task: PlanTask) {
        viewModelScope.launch {
            dao.deleteTask(task)
        }
    }

    fun toggleTaskCompletion(task: PlanTask) {
        viewModelScope.launch {
            dao.updateTask(task.copy(isCompleted = !task.isCompleted))
        }
    }

}
