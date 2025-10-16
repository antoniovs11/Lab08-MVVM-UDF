package com.example.lab08

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.example.lab08.data.Task
import com.example.lab08.data.TaskDatabase
import com.example.lab08.notifications.NotificationHelper
import com.example.lab08.ui.theme.Lab08Theme
import com.example.lab08.viewmodel.FilterMode
import com.example.lab08.viewmodel.SortMode
import com.example.lab08.viewmodel.TaskViewModel

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crear canal de notificaciones
        NotificationHelper.createNotificationChannel(this)

        // Pedir permiso (solo Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val perm = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, perm)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(perm)
            }
        }

        // Base de datos y ViewModel
        val db = Room.databaseBuilder(applicationContext, TaskDatabase::class.java, "task_db").build()
        val dao = db.taskDao()
        val viewModel = TaskViewModel(dao)

        setContent {
            Lab08Theme {
                TaskScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskScreen(viewModel: TaskViewModel) {
    val tasks by viewModel.tasks.collectAsState()
    val search by viewModel.searchQuery.collectAsState()
    val filter by viewModel.filterMode.collectAsState()
    val sort by viewModel.sortMode.collectAsState()

    var newText by remember { mutableStateOf("") }
    var newCategory by remember { mutableStateOf("") }
    var newPriority by remember { mutableStateOf("MEDIUM") }
    var editTask by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestor de Tareas") },
                actions = {
                    IconButton(onClick = {
                        val next = when (sort) {
                            SortMode.BY_NAME -> SortMode.BY_DATE
                            SortMode.BY_DATE -> SortMode.BY_STATE
                            SortMode.BY_STATE -> SortMode.BY_NAME
                        }
                        viewModel.setSortMode(next)
                    }) {
                        Icon(Icons.Default.Menu, contentDescription = "Ordenar")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(16.dp).padding(padding)) {

            // 🔍 Buscar
            OutlinedTextField(
                value = search,
                onValueChange = { viewModel.updateSearchQuery(it) },
                label = { Text("Buscar tarea o categoría") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            // 🔘 Filtros
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = filter == FilterMode.ALL,
                    onClick = { viewModel.setFilterMode(FilterMode.ALL) },
                    label = { Text("Todas") }
                )
                FilterChip(
                    selected = filter == FilterMode.PENDING,
                    onClick = { viewModel.setFilterMode(FilterMode.PENDING) },
                    label = { Text("Pendientes") }
                )
                FilterChip(
                    selected = filter == FilterMode.COMPLETED,
                    onClick = { viewModel.setFilterMode(FilterMode.COMPLETED) },
                    label = { Text("Completadas") }
                )
            }

            Spacer(Modifier.height(8.dp))

            // ➕ Nueva tarea
            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it },
                label = { Text("Descripción") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = newCategory,
                onValueChange = { newCategory = it },
                label = { Text("Categoría") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            DropdownMenuBox(selected = newPriority, onSelected = { newPriority = it })
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (newText.isNotBlank()) {
                        val task = Task(
                            description = newText,
                            category = newCategory.ifBlank { null },
                            priority = newPriority
                        )
                        viewModel.addTask(task, null)
                        newText = ""
                        newCategory = ""
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Agregar tarea")
            }

            Spacer(Modifier.height(12.dp))

            // 📋 Lista de tareas
            LazyColumn {
                items(tasks, key = { it.id }) { task ->
                    TaskItem(
                        task = task,
                        onToggle = { viewModel.toggleCompletion(task, null) },
                        onEdit = { editTask = task },
                        onDelete = { viewModel.deleteTask(task) }
                    )
                }
            }

            // ✏️ Editar tarea
            if (editTask != null) {
                EditDialog(
                    task = editTask!!,
                    onDismiss = { editTask = null },
                    onSave = { updated ->
                        viewModel.updateTask(updated, null)
                        editTask = null
                    }
                )
            }
        }
    }
}

@Composable
fun TaskItem(task: Task, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(task.description)
                Text(
                    "Prioridad: ${task.priority} | ${task.category ?: "Sin categoría"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Row {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.Check,
                        contentDescription = "Completar"
                    )
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                }
            }
        }
    }
}

@Composable
fun EditDialog(task: Task, onDismiss: () -> Unit, onSave: (Task) -> Unit) {
    var text by remember { mutableStateOf(task.description) }
    var category by remember { mutableStateOf(task.category ?: "") }
    var priority by remember { mutableStateOf(task.priority) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar tarea") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Descripción") }
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Categoría") }
                )
                DropdownMenuBox(selected = priority, onSelected = { priority = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(task.copy(description = text, category = category.ifBlank { null }, priority = priority))
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun DropdownMenuBox(selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Button(onClick = { expanded = true }) {
            Text(selected)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            listOf("HIGH", "MEDIUM", "LOW").forEach {
                DropdownMenuItem(
                    text = { Text(it) },
                    onClick = { onSelected(it); expanded = false }
                )
            }
        }
    }
}
