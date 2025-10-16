package com.example.lab08.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.ExistingWorkPolicy
import androidx.work.workDataOf
import com.example.lab08.data.Task
import com.example.lab08.data.TaskDao
import com.example.lab08.notifications.NotifyWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

enum class FilterMode { ALL, COMPLETED, PENDING }
enum class SortMode { BY_NAME, BY_DATE, BY_STATE }

class TaskViewModel(private val dao: TaskDao) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _filterMode = MutableStateFlow(FilterMode.ALL)
    val filterMode: StateFlow<FilterMode> = _filterMode.asStateFlow()

    private val _sortMode = MutableStateFlow(SortMode.BY_DATE)
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()

    // tasks flow desde DAO
    private val rawTasks: Flow<List<Task>> = dao.getAllTasksFlow()

    // combinamos búsqueda + filtro + orden
    val tasks: StateFlow<List<Task>> = combine(rawTasks, _searchQuery, _filterMode, _sortMode) { list, query, filter, sort ->
        var result = list

        // filtro
        result = when (filter) {
            FilterMode.ALL -> result
            FilterMode.COMPLETED -> result.filter { it.isCompleted }
            FilterMode.PENDING -> result.filter { !it.isCompleted }
        }

        // búsqueda
        if (query.isNotBlank()) {
            result = result.filter { it.description.contains(query, ignoreCase = true) || (it.category?.contains(query, ignoreCase = true) ?: false) }
        }

        // orden
        result = when (sort) {
            SortMode.BY_NAME -> result.sortedBy { it.description.lowercase() }
            SortMode.BY_DATE -> result.sortedByDescending { it.createdAt }
            SortMode.BY_STATE -> result.sortedWith(compareBy({ it.isCompleted }, { it.createdAt }))
        }

        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updateSearchQuery(q: String) { _searchQuery.value = q }
    fun setFilterMode(m: FilterMode) { _filterMode.value = m }
    fun setSortMode(s: SortMode) { _sortMode.value = s }

    // CRUD
    fun addTask(task: Task, context: Context? = null) {
        viewModelScope.launch {
            val id = dao.insertTask(task).toInt()
            // Si es recurrente o tiene nextRunAt se programa notificación
            if (task.isRecurring || task.nextRunAt > 0L) {
                scheduleNotificationForTask(id, task, context)
            }
        }
    }

    fun updateTask(task: Task, context: Context? = null) {
        viewModelScope.launch {
            dao.updateTask(task)
            if (task.isRecurring || task.nextRunAt > 0L) {
                scheduleNotificationForTask(task.id, task, context)
            }
        }
    }

    fun toggleCompletion(task: Task, context: Context? = null) {
        viewModelScope.launch {
            val updated = task.copy(isCompleted = !task.isCompleted)
            dao.updateTask(updated)
            if (!updated.isCompleted && (updated.isRecurring || updated.nextRunAt > 0L)) {
                scheduleNotificationForTask(updated.id, updated, context)
            }
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { dao.deleteTaskById(task.id) }
    }

    fun deleteAll() {
        viewModelScope.launch { dao.deleteAllTasks() }
    }

    // Programar notificación usando WorkManager (OneTime). Para recurrente, re-agendar después de disparo.
    private fun scheduleNotificationForTask(taskId: Int, task: Task, context: Context?) {
        if (context == null) return
        // Cancela trabajos previos del mismo id
        val workName = "notify_task_$taskId"
        val delayMillis = if (task.nextRunAt > 0L) (task.nextRunAt - System.currentTimeMillis()).coerceAtLeast(0L) else 0L

        val data = workDataOf(
            "taskId" to taskId,
            "title" to "Recordatorio: ${task.description.take(40)}",
            "description" to task.description
        )

        val request = OneTimeWorkRequestBuilder<NotifyWorker>()
            .setInputData(data)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(workName)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
    }
}

