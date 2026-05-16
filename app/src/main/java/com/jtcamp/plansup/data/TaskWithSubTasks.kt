package com.jtcamp.plansup.data

import androidx.room.Embedded
import androidx.room.Relation

data class TaskWithSubTasks(
    @Embedded val task: PlanTask,
    @Relation(
        parentColumn = "id",
        entityColumn = "taskId",
    )
    val subTasks: List<SubTask>,
)
