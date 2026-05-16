package com.jtcamp.plansup.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SubTaskType {
    REP_COUNT,
    TIMED,
}

@Entity(
    tableName = "sub_tasks",
    foreignKeys = [
        ForeignKey(
            entity = PlanTask::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("taskId")]
)
data class SubTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val taskId: Long,
    val title: String,
    val type: SubTaskType = SubTaskType.REP_COUNT,
    val totalSets: Int = 3,
    val setDetail: String = "",
    val setDurationSeconds: Int = 60,
    val restDurationSeconds: Int = 300,
    val sortOrder: Int = 0,
    val isCompleted: Boolean = false,
) {
    fun totalDurationSeconds(): Int =
        totalSets * setDurationSeconds + (totalSets - 1).coerceAtLeast(0) * restDurationSeconds
}
