package com.example.myappcitas

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myappcitas.ui.theme.MyAppCitasTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

// ==================== MODELO DE DATOS ====================
data class Task(
    val id: Int = 0,
    val title: String,
    val description: String,
    val priority: String, // "alta", "media", "baja"
    val dueDate: String,
    val isCompleted: Boolean = false
)

// ==================== BASE DE DATOS ====================
class TaskDatabaseHelper(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "tasks.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_TASKS = "tasks"

        private const val COL_ID = "id"
        private const val COL_TITLE = "title"
        private const val COL_DESCRIPTION = "description"
        private const val COL_PRIORITY = "priority"
        private const val COL_DUE_DATE = "due_date"
        private const val COL_IS_COMPLETED = "is_completed"
    }

    override fun onCreate(db: SQLiteDatabase?) {
        val createTable = """
            CREATE TABLE $TABLE_TASKS (
                $COL_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_TITLE TEXT NOT NULL,
                $COL_DESCRIPTION TEXT NOT NULL,
                $COL_PRIORITY TEXT NOT NULL,
                $COL_DUE_DATE TEXT NOT NULL,
                $COL_IS_COMPLETED INTEGER DEFAULT 0
            )
        """
        db?.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS $TABLE_TASKS")
        onCreate(db)
    }

    // CREATE
    fun insertTask(task: Task): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, task.title)
            put(COL_DESCRIPTION, task.description)
            put(COL_PRIORITY, task.priority)
            put(COL_DUE_DATE, task.dueDate)
            put(COL_IS_COMPLETED, if (task.isCompleted) 1 else 0)
        }
        return try {
            val result = db.insert(TABLE_TASKS, null, values)
            db.close()
            result != -1L
        } catch (e: Exception) {
            db.close()
            false
        }
    }

    // READ
    fun getAllTasks(): List<Task> {
        val db = readableDatabase
        val tasks = mutableListOf<Task>()
        val cursor = db.query(TABLE_TASKS, null, null, null, null, null, "$COL_DUE_DATE ASC")

        if (cursor.moveToFirst()) {
            do {
                tasks.add(
                    Task(
                        id = cursor.getInt(cursor.getColumnIndexOrThrow(COL_ID)),
                        title = cursor.getString(cursor.getColumnIndexOrThrow(COL_TITLE)),
                        description = cursor.getString(cursor.getColumnIndexOrThrow(COL_DESCRIPTION)),
                        priority = cursor.getString(cursor.getColumnIndexOrThrow(COL_PRIORITY)),
                        dueDate = cursor.getString(cursor.getColumnIndexOrThrow(COL_DUE_DATE)),
                        isCompleted = cursor.getInt(cursor.getColumnIndexOrThrow(COL_IS_COMPLETED)) == 1
                    )
                )
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return tasks
    }

    // UPDATE
    fun updateTask(task: Task): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COL_TITLE, task.title)
            put(COL_DESCRIPTION, task.description)
            put(COL_PRIORITY, task.priority)
            put(COL_DUE_DATE, task.dueDate)
            put(COL_IS_COMPLETED, if (task.isCompleted) 1 else 0)
        }
        return try {
            val result = db.update(TABLE_TASKS, values, "$COL_ID=?", arrayOf(task.id.toString()))
            db.close()
            result > 0
        } catch (e: Exception) {
            db.close()
            false
        }
    }

    // DELETE
    fun deleteTask(taskId: Int): Boolean {
        val db = writableDatabase
        return try {
            val result = db.delete(TABLE_TASKS, "$COL_ID=?", arrayOf(taskId.toString()))
            db.close()
            result > 0
        } catch (e: Exception) {
            db.close()
            false
        }
    }

    // Toggle completado
    fun toggleTaskCompletion(taskId: Int): Boolean {
        val db = writableDatabase
        return try {
            db.execSQL(
                "UPDATE $TABLE_TASKS SET $COL_IS_COMPLETED = NOT $COL_IS_COMPLETED WHERE $COL_ID = ?",
                arrayOf(taskId.toString())
            )
            db.close()
            true
        } catch (e: Exception) {
            db.close()
            false
        }
    }
}

// ==================== ACTIVIDAD PRINCIPAL ====================
class MainActivity : ComponentActivity() {
    private lateinit var dbHelper: TaskDatabaseHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dbHelper = TaskDatabaseHelper(this)

        setContent {
            MyAppCitasTheme {
                val navController = rememberNavController()
                NavigationGraph(navController = navController, dbHelper = dbHelper)
            }
        }
    }
}

// ==================== NAVEGACIÓN ====================
@Composable
fun NavigationGraph(navController: NavHostController, dbHelper: TaskDatabaseHelper) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") { HomeScreen(navController, dbHelper) }
        composable("create_step1") { CreateTaskStep1Screen(navController) }
        composable("create_step2/{title}/{description}/{priority}") { backStackEntry ->
            CreateTaskStep2Screen(
                navController,
                dbHelper,
                title = backStackEntry.arguments?.getString("title") ?: "",
                description = backStackEntry.arguments?.getString("description") ?: "",
                priority = backStackEntry.arguments?.getString("priority") ?: "media"
            )
        }
        composable("my_tasks") { MyTasksScreen(navController, dbHelper) }
        composable("edit_task/{taskId}") { backStackEntry ->
            EditTaskScreen(
                navController,
                dbHelper,
                taskId = backStackEntry.arguments?.getString("taskId")?.toIntOrNull() ?: 0
            )
        }
        composable("api_tasks") { ApiTasksScreen(navController, dbHelper) }
    }
}

// ==================== PANTALLA 1: HOME ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, dbHelper: TaskDatabaseHelper) {
    val tasks = remember { mutableStateOf(dbHelper.getAllTasks()) }

    LaunchedEffect(Unit) {
        tasks.value = dbHelper.getAllTasks()
    }

    val totalTasks = tasks.value.size
    val pendingTasks = tasks.value.count { !it.isCompleted }
    val completedTasks = tasks.value.count { it.isCompleted }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gestión de Tareas", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Resumen
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resumen", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatCard("Total", totalTasks.toString(), Color(0xFF6200EE))
                        StatCard("Pendientes", pendingTasks.toString(), Color(0xFFFF6F00))
                        StatCard("Completadas", completedTasks.toString(), Color(0xFF4CAF50))
                    }
                }
            }

            // Botones principales
            Button(
                onClick = { navController.navigate("create_step1") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6200EE))
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Crear Nueva Tarea", fontSize = 16.sp)
            }

            Button(
                onClick = { navController.navigate("my_tasks") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF03DAC5))
            ) {
                Icon(Icons.Default.List, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Ver Mis Tareas", fontSize = 16.sp)
            }

            Button(
                onClick = { navController.navigate("api_tasks") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF6F00))
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Tareas de la API", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, color: Color) {
    Card(
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.2f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
            Text(label, fontSize = 12.sp, color = color)
        }
    }
}

// ==================== PANTALLA 2: CREAR TAREA PASO 1 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskStep1Screen(navController: NavHostController) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf("media") }
    var titleError by remember { mutableStateOf("") }
    var descriptionError by remember { mutableStateOf("") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Tarea - Paso 1/2") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Información Básica", fontSize = 20.sp, fontWeight = FontWeight.Bold)

            // Título
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = if (it.isBlank()) "El título es requerido" else ""
                },
                label = { Text("Título *") },
                modifier = Modifier.fillMaxWidth(),
                isError = titleError.isNotEmpty(),
                supportingText = { if (titleError.isNotEmpty()) Text(titleError) }
            )

            // Descripción
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    descriptionError = if (it.isBlank()) "La descripción es requerida" else ""
                },
                label = { Text("Descripción *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                isError = descriptionError.isNotEmpty(),
                supportingText = { if (descriptionError.isNotEmpty()) Text(descriptionError) }
            )

            // Prioridad
            Text("Prioridad *", fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriorityButton("Alta", priority == "alta", Color(0xFFE53935)) {
                    priority = "alta"
                }
                PriorityButton("Media", priority == "media", Color(0xFFFB8C00)) {
                    priority = "media"
                }
                PriorityButton("Baja", priority == "baja", Color(0xFF43A047)) {
                    priority = "baja"
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Botón Siguiente
            Button(
                onClick = {
                    titleError = if (title.isBlank()) "El título es requerido" else ""
                    descriptionError = if (description.isBlank()) "La descripción es requerida" else ""

                    if (title.isNotBlank() && description.isNotBlank()) {
                        navController.navigate("create_step2/$title/$description/$priority")
                    } else {
                        Toast.makeText(context, "Complete todos los campos", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text("Siguiente", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

@Composable
fun RowScope.PriorityButton(text: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) color else Color.Gray.copy(alpha = 0.3f)
        )
    ) {
        Text(text, color = if (selected) Color.White else Color.Black)
    }
}

// ==================== PANTALLA 3: CREAR TAREA PASO 2 ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskStep2Screen(
    navController: NavHostController,
    dbHelper: TaskDatabaseHelper,
    title: String,
    description: String,
    priority: String
) {
    var selectedDate by remember { mutableStateOf("") }
    var showDatePicker by remember { mutableStateOf(false) }
    var dateError by remember { mutableStateOf("") }
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nueva Tarea - Paso 2/2") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Fecha Límite", fontSize = 20.sp, fontWeight = FontWeight.Bold)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Resumen:", fontWeight = FontWeight.Medium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Título: $title")
                    Text("Prioridad: ${priority.uppercase()}")
                }
            }

            OutlinedTextField(
                value = selectedDate,
                onValueChange = {},
                label = { Text("Fecha Límite *") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha")
                    }
                },
                isError = dateError.isNotEmpty(),
                supportingText = { if (dateError.isNotEmpty()) Text(dateError) }
            )

            if (showDatePicker) {
                DatePickerDialog(
                    onDateSelected = { date ->
                        val today = Calendar.getInstance()
                        today.set(Calendar.HOUR_OF_DAY, 0)
                        today.set(Calendar.MINUTE, 0)
                        today.set(Calendar.SECOND, 0)

                        if (date.before(today.time)) {
                            dateError = "La fecha no puede ser anterior a hoy"
                            selectedDate = ""
                        } else {
                            selectedDate = dateFormat.format(date)
                            dateError = ""
                        }
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (selectedDate.isBlank()) {
                        dateError = "Debe seleccionar una fecha"
                        Toast.makeText(context, "Seleccione una fecha límite", Toast.LENGTH_SHORT).show()
                    } else {
                        val task = Task(
                            title = title,
                            description = description,
                            priority = priority,
                            dueDate = selectedDate
                        )
                        if (dbHelper.insertTask(task)) {
                            Toast.makeText(context, "Tarea creada exitosamente", Toast.LENGTH_SHORT).show()
                            navController.navigate("home") {
                                popUpTo("home") { inclusive = true }
                            }
                        } else {
                            Toast.makeText(context, "Error al crear la tarea", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Guardar Tarea", fontSize = 16.sp)
            }
        }
    }
}

@Composable
fun DatePickerDialog(onDateSelected: (Date) -> Unit, onDismiss: () -> Unit) {
    val calendar = Calendar.getInstance()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleccionar Fecha") },
        text = {
            Column {
                Text("Seleccione una fecha (simulado)")
                Spacer(modifier = Modifier.height(16.dp))
                Text("Fecha actual: ${SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(calendar.time)}")
            }
        },
        confirmButton = {
            Button(onClick = { onDateSelected(calendar.time) }) {
                Text("Hoy")
            }
        },
        dismissButton = {
            Button(onClick = {
                calendar.add(Calendar.DAY_OF_MONTH, 7)
                onDateSelected(calendar.time)
            }) {
                Text("+7 días")
            }
        }
    )
}

// ==================== PANTALLA 4: MIS TAREAS ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyTasksScreen(navController: NavHostController, dbHelper: TaskDatabaseHelper) {
    var tasks by remember { mutableStateOf(dbHelper.getAllTasks()) }
    var filterStatus by remember { mutableStateOf("todos") }
    var filterPriority by remember { mutableStateOf("todas") }
    var showFilterMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        tasks = dbHelper.getAllTasks()
    }

    val filteredTasks = tasks.filter { task ->
        val statusMatch = when (filterStatus) {
            "pendientes" -> !task.isCompleted
            "completadas" -> task.isCompleted
            else -> true
        }
        val priorityMatch = when (filterPriority) {
            "todas" -> true
            else -> task.priority == filterPriority
        }
        statusMatch && priorityMatch
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mis Tareas") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterMenu = !showFilterMenu }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filtros")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate("create_step1") },
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Agregar")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (showFilterMenu) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Filtros", fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filterStatus == "todos",
                                onClick = { filterStatus = "todos" },
                                label = { Text("Todos") }
                            )
                            FilterChip(
                                selected = filterStatus == "pendientes",
                                onClick = { filterStatus = "pendientes" },
                                label = { Text("Pendientes") }
                            )
                            FilterChip(
                                selected = filterStatus == "completadas",
                                onClick = { filterStatus = "completadas" },
                                label = { Text("Completadas") }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = filterPriority == "todas",
                                onClick = { filterPriority = "todas" },
                                label = { Text("Todas") }
                            )
                            FilterChip(
                                selected = filterPriority == "alta",
                                onClick = { filterPriority = "alta" },
                                label = { Text("Alta") }
                            )
                            FilterChip(
                                selected = filterPriority == "media",
                                onClick = { filterPriority = "media" },
                                label = { Text("Media") }
                            )
                            FilterChip(
                                selected = filterPriority == "baja",
                                onClick = { filterPriority = "baja" },
                                label = { Text("Baja") }
                            )
                        }
                    }
                }
            }

            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.TaskAlt,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Gray
                        )
                        Text("No hay tareas", color = Color.Gray)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredTasks) { task ->
                        TaskCard(
                            task = task,
                            onToggle = {
                                if (dbHelper.toggleTaskCompletion(task.id)) {
                                    tasks = dbHelper.getAllTasks()
                                }
                            },
                            onEdit = {
                                navController.navigate("edit_task/${task.id}")
                            },
                            onDelete = {
                                if (dbHelper.deleteTask(task.id)) {
                                    Toast.makeText(context, "Tarea eliminada", Toast.LENGTH_SHORT).show()
                                    tasks = dbHelper.getAllTasks()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TaskCard(task: Task, onToggle: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val priorityColor = when (task.priority) {
        "alta" -> Color(0xFFE53935)
        "media" -> Color(0xFFFB8C00)
        else -> Color(0xFF43A047)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (task.isCompleted) Color.LightGray.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador de prioridad
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .background(priorityColor, CircleShape)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontWeight = FontWeight.Bold,
                    style = if (task.isCompleted) {
                        MaterialTheme.typography.bodyLarge.copy(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                    } else MaterialTheme.typography.bodyLarge
                )
                Text(task.description, fontSize = 12.sp, color = Color.Gray)
                Row {
                    Text(
                        text = task.priority.uppercase(),
                        fontSize = 10.sp,
                        color = priorityColor,
                        modifier = Modifier
                            .background(priorityColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("📅 ${task.dueDate}", fontSize = 12.sp)
                }
            }

            // Botones de acción
            IconButton(onClick = onToggle) {
                Icon(
                    if (task.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = "Toggle",
                    tint = if (task.isCompleted) Color(0xFF4CAF50) else Color.Gray
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Editar")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = Color.Red)
            }
        }
    }
}

// ==================== PANTALLA 5: EDITAR TAREA ====================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskScreen(navController: NavHostController, dbHelper: TaskDatabaseHelper, taskId: Int) {
    val allTasks = dbHelper.getAllTasks()
    val task = allTasks.find { it.id == taskId }

    if (task == null) {
        LaunchedEffect(Unit) {
            navController.popBackStack()
        }
        return
    }

    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var priority by remember { mutableStateOf(task.priority) }
    var dueDate by remember { mutableStateOf(task.dueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var titleError by remember { mutableStateOf("") }
    var descriptionError by remember { mutableStateOf("") }
    var dateError by remember { mutableStateOf("") }
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Editar Tarea") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Editar Información", fontSize = 20.sp, fontWeight = FontWeight.Bold)

            // Título
            OutlinedTextField(
                value = title,
                onValueChange = {
                    title = it
                    titleError = if (it.isBlank()) "El título es requerido" else ""
                },
                label = { Text("Título *") },
                modifier = Modifier.fillMaxWidth(),
                isError = titleError.isNotEmpty(),
                supportingText = { if (titleError.isNotEmpty()) Text(titleError) }
            )

            // Descripción
            OutlinedTextField(
                value = description,
                onValueChange = {
                    description = it
                    descriptionError = if (it.isBlank()) "La descripción es requerida" else ""
                },
                label = { Text("Descripción *") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                maxLines = 5,
                isError = descriptionError.isNotEmpty(),
                supportingText = { if (descriptionError.isNotEmpty()) Text(descriptionError) }
            )

            // Prioridad
            Text("Prioridad *", fontWeight = FontWeight.Medium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PriorityButton("Alta", priority == "alta", Color(0xFFE53935)) {
                    priority = "alta"
                }
                PriorityButton("Media", priority == "media", Color(0xFFFB8C00)) {
                    priority = "media"
                }
                PriorityButton("Baja", priority == "baja", Color(0xFF43A047)) {
                    priority = "baja"
                }
            }

            // Fecha límite
            OutlinedTextField(
                value = dueDate,
                onValueChange = {},
                label = { Text("Fecha Límite *") },
                modifier = Modifier.fillMaxWidth(),
                readOnly = true,
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Seleccionar fecha")
                    }
                },
                isError = dateError.isNotEmpty(),
                supportingText = { if (dateError.isNotEmpty()) Text(dateError) }
            )

            if (showDatePicker) {
                DatePickerDialog(
                    onDateSelected = { date ->
                        val today = Calendar.getInstance()
                        today.set(Calendar.HOUR_OF_DAY, 0)
                        today.set(Calendar.MINUTE, 0)
                        today.set(Calendar.SECOND, 0)

                        if (date.before(today.time)) {
                            dateError = "La fecha no puede ser anterior a hoy"
                        } else {
                            dueDate = dateFormat.format(date)
                            dateError = ""
                        }
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Botones
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancelar")
                }

                Button(
                    onClick = {
                        titleError = if (title.isBlank()) "El título es requerido" else ""
                        descriptionError = if (description.isBlank()) "La descripción es requerida" else ""

                        if (title.isNotBlank() && description.isNotBlank() && dueDate.isNotBlank()) {
                            val updatedTask = task.copy(
                                title = title,
                                description = description,
                                priority = priority,
                                dueDate = dueDate
                            )
                            if (dbHelper.updateTask(updatedTask)) {
                                Toast.makeText(context, "Tarea actualizada", Toast.LENGTH_SHORT).show()
                                navController.popBackStack()
                            } else {
                                Toast.makeText(context, "Error al actualizar", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Guardar")
                }
            }
        }
    }
}

// ==================== PANTALLA 6: TAREAS DE LA API ====================
data class ApiTask(
    val userId: Int,
    val id: Int,
    val title: String,
    val completed: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiTasksScreen(navController: NavHostController, dbHelper: TaskDatabaseHelper) {
    var apiTasks by remember { mutableStateOf<List<ApiTask>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        scope.launch {
            try {
                isLoading = true
                errorMessage = null
                val tasks = fetchTasksFromApi()
                apiTasks = tasks
                isLoading = false
            } catch (e: Exception) {
                isLoading = false
                errorMessage = "Error de conexión: ${e.message}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tareas de la API") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    isLoading = true
                                    errorMessage = null
                                    val tasks = fetchTasksFromApi()
                                    apiTasks = tasks
                                    isLoading = false
                                    Toast.makeText(context, "Actualizado", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    isLoading = false
                                    errorMessage = "Error de conexión: ${e.message}"
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Actualizar")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    // Estado de carga
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Cargando tareas desde la API...")
                    }
                }
                errorMessage != null -> {
                    // Estado de error
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.Red
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            errorMessage!!,
                            color = Color.Red,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    try {
                                        isLoading = true
                                        errorMessage = null
                                        val tasks = fetchTasksFromApi()
                                        apiTasks = tasks
                                        isLoading = false
                                    } catch (e: Exception) {
                                        isLoading = false
                                        errorMessage = "Error de conexión: ${e.message}"
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Reintentar")
                        }
                    }
                }
                apiTasks.isEmpty() -> {
                    // Sin datos
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No hay tareas disponibles", color = Color.Gray)
                    }
                }
                else -> {
                    // Lista de tareas
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        "📡 JSONPlaceholder API",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Text(
                                        "Mostrando ${apiTasks.size} tareas de ejemplo",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }

                        items(apiTasks.take(20)) { apiTask ->
                            ApiTaskCard(
                                apiTask = apiTask,
                                onAddToLocal = {
                                    val task = Task(
                                        title = apiTask.title,
                                        description = "Tarea importada desde JSONPlaceholder API (ID: ${apiTask.id})",
                                        priority = "media",
                                        dueDate = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                                            .format(Calendar.getInstance().apply {
                                                add(Calendar.DAY_OF_MONTH, 7)
                                            }.time),
                                        isCompleted = apiTask.completed
                                    )
                                    if (dbHelper.insertTask(task)) {
                                        Toast.makeText(
                                            context,
                                            "Tarea agregada a Mis Tareas",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            "Error al agregar tarea",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ApiTaskCard(apiTask: ApiTask, onAddToLocal: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (apiTask.completed)
                Color(0xFFE8F5E9)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (apiTask.completed) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (apiTask.completed) Color(0xFF4CAF50) else Color.Gray
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = apiTask.title,
                    fontWeight = FontWeight.Medium,
                    style = if (apiTask.completed) {
                        MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                    } else MaterialTheme.typography.bodyMedium
                )
                Text(
                    "ID: ${apiTask.id} | Usuario: ${apiTask.userId}",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            Button(
                onClick = onAddToLocal,
                modifier = Modifier.height(36.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Agregar", fontSize = 12.sp)
            }
        }
    }
}

// ==================== FUNCIÓN PARA CONSUMIR API ====================
suspend fun fetchTasksFromApi(): List<ApiTask> = withContext(Dispatchers.IO) {
    try {
        val url = URL("https://jsonplaceholder.typicode.com/todos")
        val connection = url.openConnection()
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val response = connection.getInputStream().bufferedReader().use { it.readText() }
        val jsonArray = JSONArray(response)

        val tasks = mutableListOf<ApiTask>()
        for (i in 0 until jsonArray.length()) {
            val jsonObject = jsonArray.getJSONObject(i)
            tasks.add(
                ApiTask(
                    userId = jsonObject.getInt("userId"),
                    id = jsonObject.getInt("id"),
                    title = jsonObject.getString("title"),
                    completed = jsonObject.getBoolean("completed")
                )
            )
        }
        tasks
    } catch (e: Exception) {
        throw Exception("No se pudo conectar a la API. Verifique su conexión a internet.")
    }
}