package com.jtcamp.plansup.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanTaskDao {

    // ---- PlanTask ----

    @Query("SELECT * FROM plan_tasks WHERE date = :date ORDER BY id ASC")
    fun getTasksByDate(date: String): Flow<List<PlanTask>>

    @Query("SELECT * FROM plan_tasks ORDER BY id ASC")
    fun getAllTasks(): Flow<List<PlanTask>>

    @Query("SELECT DISTINCT tag FROM plan_tasks WHERE tag != '' ORDER BY tag ASC")
    fun getAllTags(): Flow<List<String>>

    @Query("SELECT * FROM plan_tasks WHERE repeatMode != 'NONE'")
    fun getRepeatingTasks(): Flow<List<PlanTask>>

    @Query("SELECT * FROM sub_tasks")
    suspend fun getAllSubTasks(): List<SubTask>

    @Query("SELECT * FROM plan_tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Long): PlanTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: PlanTask): Long

    @Update
    suspend fun updateTask(task: PlanTask)

    @Delete
    suspend fun deleteTask(task: PlanTask)

    // ---- SubTask ----

    @Query("SELECT * FROM sub_tasks WHERE taskId = :taskId ORDER BY sortOrder ASC")
    fun getSubTasksByTaskId(taskId: Long): Flow<List<SubTask>>

    @Query("SELECT * FROM sub_tasks WHERE taskId = :taskId ORDER BY sortOrder ASC")
    suspend fun getSubTasksByTaskIdSync(taskId: Long): List<SubTask>

    @Query("SELECT * FROM sub_tasks WHERE id = :subTaskId")
    suspend fun getSubTaskById(subTaskId: Long): SubTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubTask(subTask: SubTask): Long

    @Update
    suspend fun updateSubTask(subTask: SubTask)

    @Delete
    suspend fun deleteSubTask(subTask: SubTask)

    // ---- Combined ----

    @Transaction
    @Query("SELECT * FROM plan_tasks WHERE id = :taskId")
    fun getTaskWithSubTasks(taskId: Long): Flow<TaskWithSubTasks?>
}
