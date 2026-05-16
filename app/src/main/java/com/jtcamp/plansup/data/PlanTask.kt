package com.jtcamp.plansup.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class RepeatMode {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
}

@Entity(tableName = "plan_tasks")
data class PlanTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    /** 任务创建日期，格式 yyyy-MM-dd */
    val date: String,
    val isCompleted: Boolean = false,
    /** 子任务间休息时间（秒） */
    val interSubTaskRestSeconds: Int = 60,
    val repeatMode: RepeatMode = RepeatMode.NONE,
    val repeatDaysOfWeek: String = "",
    val reminderTime: String = "",
    val reminderDate: String = "",
    val tag: String = "",
)
