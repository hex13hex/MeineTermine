package com.example.meinetermine

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.example.meinetermine.ui.theme.MeineTermineTheme
import kotlinx.coroutines.launch
import java.util.Calendar
import androidx.compose.material3.AlertDialog
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import android.net.Uri
import android.content.ComponentName
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.EventAvailable
import android.net.ConnectivityManager
import android.net.Network
import android.app.Activity

fun isTimeConflict(
    newTime: String,
    existingTimes: List<String>
): Boolean {

    val newMinutes = newTime.split(":").let {
        it[0].toInt() * 60 + it[1].toInt()
    }

    val durationMinutes =
        (AppConfig.TERMIN_DURATION_MILLIS / (60 * 1000)).toInt()

    val newEnd = newMinutes + durationMinutes

    return existingTimes.any { existingTime ->

        val existingMinutes = existingTime.split(":").let {
            it[0].toInt() * 60 + it[1].toInt()
        }

        val existingEnd = existingMinutes + durationMinutes

        newMinutes < existingEnd && existingMinutes < newEnd
    }
}

class MainActivity : ComponentActivity() {

    private lateinit var database: TerminDatabase
    var googleDriveResultCallback: ((Boolean) -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = TerminDatabase.getDatabase(this)

        NotificationHelper.createNotificationChannel(this)

        if (
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }

        enableEdgeToEdge()

        setContent {
            MeineTermineTheme {
                App(database.terminDao())
            }
        }
    }

    override fun onActivityResult(
        requestCode: Int,
        resultCode: Int,
        data: Intent?
    ) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == GoogleDriveAuth.REQUEST_CODE_AUTH) {

            GoogleDriveAuth().handleAuthorizationResult(
                this,
                data
            ) { connected ->

                googleDriveResultCallback?.invoke(connected)
                googleDriveResultCallback = null
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch(Dispatchers.IO) {
            TerminMaintenance.scheduleAllUpcoming(
                applicationContext,
                database.terminDao()
            )
        }
    }
}

fun openExactAlarmSettings(context: Context) {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val intent = Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
        )
        context.startActivity(intent)
    }
}

fun getTerminSection(
    date: String
): String = when (DateTimeUtils.section(date)) {
    "Today" -> "Сегодня"
    "Tomorrow" -> "Завтра"
    "DayAfterTomorrow" -> "Послезавтра"
    else -> "Остальные"
}

fun isTerminInPast(date: String, time: String): Boolean {
    return DateTimeUtils.isPast(date, time)
}

@Composable
fun App(dao: TerminDao) {

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE)
                as ConnectivityManager

    var googleDriveConnected by remember {
        mutableStateOf(false)
    }

    var autoStartAllowed by remember {
        mutableStateOf(true)
    }

    DisposableEffect(connectivityManager) {

        val networkCallback = object : ConnectivityManager.NetworkCallback() {

            override fun onLost(network: Network) {
                googleDriveConnected = false
            }

            override fun onAvailable(network: Network) {

                GoogleDriveAuth().checkDriveAccess(context as Activity) { connected ->
                    googleDriveConnected = connected
                }
            }
        }

        connectivityManager.registerDefaultNetworkCallback(networkCallback)

        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    LaunchedEffect(Unit) {
        GoogleDriveAuth().checkDriveAccess(context as android.app.Activity) { connected ->
            googleDriveConnected = connected
        }
    }

    DisposableEffect(lifecycleOwner) {

        val observer = LifecycleEventObserver { _, event ->

            if (event == Lifecycle.Event.ON_RESUME) {
                autoStartAllowed =
                    AutoStartChecker.isAutoStartAllowed(context)
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        autoStartAllowed =
            AutoStartChecker.isAutoStartAllowed(context)
    }

    if (!autoStartAllowed) {
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text("Включите автозапуск")
            },
            text = {
                Text(
                    "Для корректной работы напоминаний после " +
                            "перезагрузки телефона необходимо включить " +
                            "фоновый автозапуск для приложения."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val intent = Intent().apply {
                                component = ComponentName(
                                    "com.miui.securitycenter",
                                    "com.miui.permcenter.autostart.AutoStartManagementActivity"
                                )
                            }

                            context.startActivity(intent)

                        } catch (e: Exception) {
                            val intent = Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                            ).apply {
                                data = Uri.parse(
                                    "package:${context.packageName}"
                                )
                            }

                            context.startActivity(intent)
                        }
                    }
                ) {
                    Text("Открыть настройки")
                }
            }
        )
    }

    var showAddScreen by remember { mutableStateOf(false) }
    var showStatisticsScreen by remember { mutableStateOf(false) }
    var editingTermin by remember { mutableStateOf<TerminEntity?>(null) }

    var conflictingTermin by remember {
        mutableStateOf<TerminEntity?>(null)
    }

    var importedTermine by remember {
        mutableStateOf<List<TerminEntity>?>(null)
    }

    var importError by remember {
        mutableStateOf(false)
    }

    val termine by dao.observeAll().collectAsState(initial = emptyList())
    val statisticsStore = remember { StatisticsStore(context.applicationContext) }
    var totalEverCreated by remember { mutableLongStateOf(0L) }

    LaunchedEffect(termine.size) {
        totalEverCreated = statisticsStore.getTotalEverCreated(termine.size)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->

        if (uri != null) {
            val json = BackupManager.createJson(termine)

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(json.toByteArray())
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->

        if (uri != null) {

            val json = context.contentResolver
                .openInputStream(uri)
                ?.bufferedReader()
                ?.use { it.readText() }

            if (json != null) {

                val parsedTermine = try {
                    BackupManager.parseJson(json)
                } catch (e: Exception) {
                    importError = true
                    null
                }

                if (parsedTermine == null) {
                    return@rememberLauncherForActivityResult
                }

                val termineToInsert = parsedTermine
                    .filter { termin ->
                        !isTerminInPast(termin.date, termin.time)
                    }
                    .map { termin ->
                        TerminEntity(
                            date = termin.date,
                            time = termin.time,
                            scheduledAtMillis = DateTimeUtils.toEpochMillis(termin.date, termin.time)!!,
                            description = termin.description
                        )
                    }

                importedTermine = termineToInsert
            }
        }
    }

    if (showAddScreen) {

        AddTerminScreen(
            terminToEdit = editingTermin,

            onBack = {
                showAddScreen = false
                editingTermin = null
            },

            onSave = { newTermin, terminType ->

                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.Dispatchers.IO
                ).launch {

                    val editedTermin = editingTermin
                    val termineToSave = when {
                        editedTermin != null -> listOf(newTermin.copy(id = editedTermin.id))
                        terminType == TerminType.RECURRING ->
                            DateTimeUtils.weeklyOccurrences(newTermin.date).mapNotNull { date ->
                                DateTimeUtils.toEpochMillis(date, newTermin.time)?.let { scheduledAtMillis ->
                                    newTermin.copy(
                                        date = date,
                                        scheduledAtMillis = scheduledAtMillis
                                    )
                                }
                            }
                        else -> listOf(newTermin)
                    }

                    val conflict = termineToSave.firstNotNullOfOrNull { candidate ->
                        dao.getTermineForDate(candidate.date, editedTermin?.id ?: 0L)
                            .firstOrNull { existing ->
                                isTimeConflict(candidate.time, listOf(existing.time))
                            }
                    }

                    if (conflict != null) {
                        withContext(Dispatchers.Main) {
                            conflictingTermin = conflict
                        }
                        return@launch
                    }

                    if (editedTermin != null) {
                        val updatedTermin = termineToSave.single()
                        ReminderScheduler.cancelReminders(context, updatedTermin.id)
                        ReminderScheduler.cancelAutoDelete(context, updatedTermin.id)
                        dao.update(updatedTermin)
                        ReminderScheduler.scheduleReminders(context, updatedTermin)
                        ReminderScheduler.scheduleAutoDelete(context, updatedTermin)
                    } else {
                        val alarmManager = context.getSystemService(Context.ALARM_SERVICE)
                                as android.app.AlarmManager
                        val needsExactAlarmPermission =
                            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S &&
                                    !alarmManager.canScheduleExactAlarms()

                        termineToSave.forEach { termin ->
                            val savedTermin = termin.copy(id = dao.insert(termin))
                            if (!needsExactAlarmPermission) {
                                ReminderScheduler.scheduleReminders(context, savedTermin)
                                ReminderScheduler.scheduleAutoDelete(context, savedTermin)
                            }
                        }
                        statisticsStore.addCreated(termineToSave.size)

                        withContext(Dispatchers.Main) {
                            if (needsExactAlarmPermission) openExactAlarmSettings(context)
                        }
                    }

                    withContext(Dispatchers.Main) {
                        showAddScreen = false
                        editingTermin = null
                    }
                }
            }
        )

    } else if (showStatisticsScreen) {

        StatisticsScreen(
            totalEverCreated = totalEverCreated,
            currentAppointments = termine.size,
            onBack = { showStatisticsScreen = false }
        )

    } else {

        TerminListScreen(
            termine = termine,
            googleDriveConnected = googleDriveConnected,

            onAddClick = {
                editingTermin = null
                showAddScreen = true
            },

            onEdit = { termin ->
                editingTermin = termin
                showAddScreen = true
            },

            onDelete = { termin ->

                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.Dispatchers.IO
                ).launch {

                    ReminderScheduler.cancelReminders(
                        context,
                        termin.id
                    )

                    ReminderScheduler.cancelAutoDelete(
                        context,
                        termin.id
                    )

                    dao.delete(termin)
                }
            },

            onExportClick = {
                exportLauncher.launch("termin_backup.json")
            },

            onImportClick = {
                importLauncher.launch(
                    arrayOf("application/json")
                )
            },

            onStatisticsClick = {
                showStatisticsScreen = true
            },

            onGoogleDriveClick = {

                val activity = context as android.app.Activity

                (activity as MainActivity).googleDriveResultCallback = { connected ->
                    googleDriveConnected = connected
                }

                GoogleDriveAuth().requestDriveAccess(
                    activity
                ) { connected ->
                    googleDriveConnected = connected
                }
            }
        )
    }

    if (conflictingTermin != null) {

        AlertDialog(
            onDismissRequest = {
                conflictingTermin = null
            },
            title = {
                Text("Время уже занято")
            },
            text = {
                Text(
                    "В это время у вас уже есть термин:\n\n" +
                            "${conflictingTermin!!.description}\n" +
                            "${conflictingTermin!!.date} в ${conflictingTermin!!.time}\n\n" +
                            "Пожалуйста, выберите другое время."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        conflictingTermin = null
                    }
                ) {
                    Text("Понятно")
                }
            }
        )
    }

    if (importedTermine != null) {
        AlertDialog(
            onDismissRequest = {
                importedTermine = null
            },
            title = {
                Text("Импорт терминов")
            },
            text = {
                Text(
                    "Все существующие термины будут удалены " +
                            "и заменены на ${importedTermine!!.size} импортированных.\n\n" +
                            "Продолжить?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val termine = importedTermine ?: return@Button

                        CoroutineScope(Dispatchers.IO).launch {
                            TerminMaintenance.cancelAll(context, dao.getAll())
                            dao.replaceAll(termine)
                            statisticsStore.addCreated(termine.size)

                            val savedTermine = dao.getAll()

                            withContext(Dispatchers.Main) {
                                importedTermine = null
                            }

                            for (termin in savedTermine) {
                                ReminderScheduler.scheduleReminders(context, termin)
                                ReminderScheduler.scheduleAutoDelete(context, termin)
                            }
                        }
                    }
                ) {
                    Text("Импортировать")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        importedTermine = null
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }

    if (importError) {
        AlertDialog(
            onDismissRequest = {
                importError = false
            },
            title = {
                Text("Ошибка импорта")
            },
            text = {
                Text(
                    "Не удалось прочитать файл.\n\n" +
                            "Проверьте, что выбран правильный файл резервной копии."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        importError = false
                    }
                ) {
                    Text("Понятно")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    totalEverCreated: Long,
    currentAppointments: Int,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Статистика") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Ваши записи",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = "Краткий обзор терминов в приложении",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            StatisticsMetricCard(
                icon = Icons.Default.History,
                label = "За всё время",
                value = totalEverCreated.toString(),
                description = "создано терминов"
            )

            StatisticsMetricCard(
                icon = Icons.Default.EventAvailable,
                label = "Сейчас",
                value = currentAppointments.toString(),
                description = "терминов в приложении"
            )
        }
    }
}

@Composable
fun StatisticsMetricCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, Color(0xFFD50000))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color(0xFFD50000),
                modifier = Modifier.padding(end = 18.dp)
            )
            Column {
                Text(label, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color(0xFFD50000)
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminListScreen(
    googleDriveConnected: Boolean,
    termine: List<TerminEntity>,
    onAddClick: () -> Unit,
    onEdit: (TerminEntity) -> Unit,
    onDelete: (TerminEntity) -> Unit,
    onExportClick: () -> Unit,
    onImportClick: () -> Unit,
    onStatisticsClick: () -> Unit,
    onGoogleDriveClick: () -> Unit,
) {

    Scaffold(

        topBar = {
            TopAppBar(
                title = {
                    Text("Мои термины")
                },
                actions = {

                    var menuExpanded by remember {
                        mutableStateOf(false)
                    }

                    IconButton(
                        onClick = {
                            menuExpanded = true
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Меню"
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = {
                            menuExpanded = false
                        }
                    ) {

                        DropdownMenuItem(
                            text = {
                                Text("Экспорт")
                            },
                            onClick = {
                                menuExpanded = false
                                onExportClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FileUpload,
                                    contentDescription = null
                                )
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Импорт")
                            },
                            onClick = {
                                menuExpanded = false
                                onImportClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null
                                )
                            }
                        )

                        DropdownMenuItem(
                            text = {
                                Text("Статистика")
                            },
                            onClick = {
                                menuExpanded = false
                                onStatisticsClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null
                                )
                            }
                        )

                        if (!googleDriveConnected) {
                            DropdownMenuItem(
                                text = {
                                    Text("Подключить Google Drive")
                                },
                                onClick = {
                                    menuExpanded = false
                                    onGoogleDriveClick()
                                }
                            )
                        }
                    }
                }
            )
        },

        floatingActionButton = {

            FloatingActionButton(
                onClick = onAddClick
            ) {

                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Добавить термин"
                )
            }
        }

    ) { innerPadding ->

        if (termine.isEmpty()) {

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),

                horizontalAlignment = Alignment.CenterHorizontally,

                verticalArrangement = Arrangement.Center
            ) {

                Text("Терминов пока нет")
            }

        } else {

            val todayTermine =
                termine.filter {
                    getTerminSection(it.date) == "Сегодня"
                }

            val tomorrowTermine =
                termine.filter {
                    getTerminSection(it.date) == "Завтра"
                }

            val dayAfterTomorrowTermine =
                termine.filter {
                    getTerminSection(it.date) == "Послезавтра"
                }

            val otherTermine =
                termine.filter {
                    getTerminSection(it.date) == "Остальные"
                }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {

                if (googleDriveConnected) {
                    item {
                        Text(
                            text = "Google Drive подключён",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            modifier = Modifier.padding(
                                bottom = 12.dp
                            )
                        )
                    }
                }

                if (todayTermine.isNotEmpty()) {

                    item {
                        Row(
                            modifier = Modifier.padding(
                                bottom = 12.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFF1565C0)
                            )

                            Spacer(
                                modifier = Modifier.width(8.dp)
                            )

                            Text(
                                text = "Сегодня",
                                style = MaterialTheme.typography.titleLarge,
                                color = Color(0xFF1565C0)
                            )
                        }
                    }

                    items(
                        items = todayTermine,
                        key = { termin -> termin.id }
                    ) { termin ->

                        TerminCard(
                            termin = termin,
                            onEdit = {
                                onEdit(termin)
                            },
                            onDelete = {
                                onDelete(termin)
                            }
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )
                    }
                }

                if (tomorrowTermine.isNotEmpty()) {

                    item {
                        Row(
                            modifier = Modifier.padding(
                                top = 16.dp,
                                bottom = 12.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFFD50000)
                            )

                            Spacer(
                                modifier = Modifier.width(8.dp)
                            )

                            Text(
                                text = "Завтра",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    items(
                        items = tomorrowTermine,
                        key = { termin -> termin.id }
                    ) { termin ->

                        TerminCard(
                            termin = termin,
                            onEdit = {
                                onEdit(termin)
                            },
                            onDelete = {
                                onDelete(termin)
                            }
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )
                    }
                }

                if (dayAfterTomorrowTermine.isNotEmpty()) {

                    item {
                        Row(
                            modifier = Modifier.padding(
                                top = 16.dp,
                                bottom = 12.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFFD50000)
                            )

                            Spacer(
                                modifier = Modifier.width(8.dp)
                            )

                            Text(
                                text = "Послезавтра",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    items(
                        items = dayAfterTomorrowTermine,
                        key = { termin -> termin.id }
                    ) { termin ->

                        TerminCard(
                            termin = termin,
                            onEdit = {
                                onEdit(termin)
                            },
                            onDelete = {
                                onDelete(termin)
                            }
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )
                    }
                }

                if (otherTermine.isNotEmpty()) {

                    item {
                        Row(
                            modifier = Modifier.padding(
                                top = 16.dp,
                                bottom = 12.dp
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Icon(
                                imageVector = Icons.Default.CalendarToday,
                                contentDescription = null,
                                tint = Color(0xFFD50000)
                            )

                            Spacer(
                                modifier = Modifier.width(8.dp)
                            )

                            Text(
                                text = "Остальные",
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                    }

                    items(
                        items = otherTermine,
                        key = { termin -> termin.id }
                    ) { termin ->

                        TerminCard(
                            termin = termin,
                            onEdit = {
                                onEdit(termin)
                            },
                            onDelete = {
                                onDelete(termin)
                            }
                        )

                        Spacer(
                            modifier = Modifier.height(12.dp)
                        )
                    }
                }

            }
        }
    }
}

@Composable
fun TerminCard(
    termin: TerminEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    val isPast = isTerminInPast(
        termin.date,
        termin.time
    )
    val isToday = DateTimeUtils.section(termin.date) == "Today"
    val accentColor = if (isToday) Color(0xFF1565C0) else Color(0xFFD50000)

    val cardColors =
        if (isPast) {
            CardDefaults.cardColors(
                containerColor = Color(0xFFD6D6D6),
                contentColor = Color(0xFF777777)
            )
        } else {
            CardDefaults.cardColors(
                containerColor = Color(0xFFF5F5F5),
                contentColor = Color(0xFF111111)
            )
        }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = cardColors,
        border = if (!isPast || isToday) {
            BorderStroke(
                1.dp,
                accentColor
            )
        } else {
            null
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Text(
                text = termin.date,
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = termin.time,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = termin.description,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {

                IconButton(
                    onClick = onEdit
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Изменить"
                    )
                }

                IconButton(
                    onClick = {
                        showDeleteDialog = true
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Удалить"
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {

        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text("Удалить термин?")
            },
            text = {
                Text(
                    "${termin.description}\n" +
                            "${termin.date}, ${termin.time}\n\n" +
                            "Термин будет удалён без возможности восстановления."
                )
            },
            confirmButton = {

                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {

                Button(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text("Отмена")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTerminScreen(
    terminToEdit: TerminEntity?,
    onBack: () -> Unit,
    onSave: (TerminEntity, TerminType) -> Unit
) {

    var date by remember { mutableStateOf(terminToEdit?.date ?: "") }
    var time by remember { mutableStateOf(terminToEdit?.time ?: "") }
    var description by remember { mutableStateOf(terminToEdit?.description ?: "") }
    var terminType by remember { mutableStateOf(TerminType.SINGLE) }

    val context = LocalContext.current

    Scaffold(

        topBar = {

            TopAppBar(

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {
                        Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад"
                        )
                    }
                },

                title = {
                    Text(
                        if (terminToEdit == null) {
                            "Добавить термин"
                        } else {
                            "Изменить термин"
                        }
                    )
                }
            )
        }

    ) { innerPadding ->

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {

            if (terminToEdit == null) {
                Text(
                    text = "Тип термина",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { terminType = TerminType.SINGLE },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (terminType == TerminType.SINGLE) {
                                Color(0xFFD50000)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (terminType == TerminType.SINGLE) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    ) {
                        Text("Одиночный")
                    }

                    OutlinedButton(
                        onClick = { terminType = TerminType.RECURRING },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (terminType == TerminType.RECURRING) {
                                Color(0xFFD50000)
                            } else {
                                MaterialTheme.colorScheme.surface
                            },
                            contentColor = if (terminType == TerminType.RECURRING) {
                                Color.White
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    ) {
                        Text("Повторяемый")
                    }
                }

                if (terminType == TerminType.RECURRING) {
                    Text(
                        text = "Будет создан термин каждую неделю в течение двух месяцев.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // ДАТА

            OutlinedButton(
                onClick = {

                    val calendar = Calendar.getInstance()

                    DatePickerDialog(
                        context,

                        { _, year, month, dayOfMonth ->

                            date = String.format(
                                "%02d.%02d.%04d",
                                dayOfMonth,
                                month + 1,
                                year
                            )
                        },

                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)

                    ).show()
                },

                modifier = Modifier.fillMaxWidth()
            ) {

                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    if (date.isEmpty()) {
                        "Выбрать дату"
                    } else {
                        date
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            // ВРЕМЯ

            OutlinedButton(
                onClick = {

                    val calendar = Calendar.getInstance()

                    TimePickerDialog(
                        context,

                        { _, hourOfDay, minute ->

                            time = String.format(
                                "%02d:%02d",
                                hourOfDay,
                                minute
                            )
                        },

                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),

                        true

                    ).show()
                },

                modifier = Modifier.fillMaxWidth()
            ) {

                Icon(
                    imageVector = Icons.Default.AccessTime,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text(
                    if (time.isEmpty()) {
                        "Выбрать время"
                    } else {
                        time
                    }
                )
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            // ОПИСАНИЕ

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = {
                    Text("Описание")
                },
                placeholder = {
                    Text("Например, врач")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = Color(0xFFD50000)
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            )

            Spacer(
                modifier = Modifier.height(24.dp)
            )

            // СОХРАНИТЬ

            Button(
                onClick = {
                    if (
                        date.isNotEmpty() &&
                        time.isNotEmpty() &&
                        description.isNotBlank()
                    ) {
                        onSave(
                            TerminEntity(
                                date = date,
                                time = time,
                                scheduledAtMillis = DateTimeUtils.toEpochMillis(date, time) ?: return@Button,
                                description = description.trim()
                            ),
                            terminType
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD50000)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Save,
                    contentDescription = null
                )

                Spacer(
                    modifier = Modifier.width(8.dp)
                )

                Text("Сохранить")
            }

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }
    }
}
