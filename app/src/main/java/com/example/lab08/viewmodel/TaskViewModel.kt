package com.example.lab08.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab08.data.Task
import com.example.lab08.data.TaskDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class TaskViewModel(private val dao: TaskDao) : ViewModel() {

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks

    init {
        viewModelScope.launch {
            _tasks.value = dao.getAllTasks()
        }
    }

    fun addTask(description: String) {
        val newTask = Task(description = description)
        viewModelScope.launch {
            dao.insertTask(newTask)
            _tasks.value = dao.getAllTasks()
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updatedTask = task.copy(isCompleted = !task.isCompleted)
            dao.updateTask(updatedTask)
            _tasks.value = dao.getAllTasks()
        }
    }

    fun deleteAllTasks() {
        viewModelScope.launch {
            dao.deleteAllTasks()
            _tasks.value = emptyList()
        }
    }

    // ✅ Nuevo: actualizar descripción
    fun updateTaskDescription(task: Task, newDescription: String) {
        viewModelScope.launch {
            val updated = task.copy(description = newDescription)
            dao.updateTask(updated)
            _tasks.value = dao.getAllTasks()
        }
    }

    // ✅ Nuevo: eliminar tarea individual
    fun deleteTask(task: Task) {
        viewModelScope.launch {
            dao.deleteTaskById(task.id)
            _tasks.value = dao.getAllTasks()
        }
    }
}
