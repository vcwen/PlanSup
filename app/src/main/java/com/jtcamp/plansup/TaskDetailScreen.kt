package com.jtcamp.plansup

import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtcamp.plansup.data.PlanTask
import com.jtcamp.plansup.data.RepeatMode
import com.jtcamp.plansup.data.SubTask
import com.jtcamp.plansup.data.SubTaskType
import com.jtcamp.plansup.viewmodel.TaskDetailViewModel

private fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}小时${minutes}分钟"
        hours > 0 -> "${hours}小时"
        minutes > 0 && seconds > 0 -> "${minutes}分${seconds}秒"
        minutes > 0 -> "${minutes}分钟"
        else -> "${seconds}秒"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskDetailScreen(
    taskId: Long,
    viewModel: TaskDetailViewModel,
    onNavigateToTimer: (subTaskId: Long) -> Unit,
    onNavigateToSequentialTimer: (taskId: Long) -> Unit,
    onBack: () -> Unit,
) {
    viewModel.setTaskId(taskId)

    val taskWithSubTasks by viewModel.taskWithSubTasksFlow.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showAddSubTaskDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showEditTaskDialog by remember { mutableStateOf(false) }
    var editingSubTask by remember { mutableStateOf<SubTask?>(null) }

    val task = taskWithSubTasks?.task
    val subTasks = taskWithSubTasks?.subTasks ?: emptyList()

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        task?.title ?: "任务详情",
                        style = MaterialTheme.typography.headlineMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (task != null) {
                        IconButton(onClick = { showEditTaskDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "设置")
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        floatingActionButton = {
            if (task != null) {
                FloatingActionButton(
                    onClick = { showAddSubTaskDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "添加子任务")
                }
            }
        }
    ) { innerPadding ->
        if (task == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("加载中...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp,
                ),
            ) {
                if (task.description.isNotBlank()) {
                    item {
                        Text(
                            text = task.description,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }

                val totalEstimatedSeconds = subTasks.sumOf { it.totalDurationSeconds() }
                if (totalEstimatedSeconds > 0) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 4.dp),
                        ) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "预计总时长: ${formatDuration(totalEstimatedSeconds)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                if (subTasks.size >= 2) {
                    item {
                        Button(
                            onClick = { onNavigateToSequentialTimer(task.id) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("开始全部 (${subTasks.size}个子任务)")
                        }
                        Spacer(Modifier.height(4.dp))
                    }
                }

                if (subTasks.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "\uD83C\uDFCB\uFE0F",
                                    style = MaterialTheme.typography.displayMedium,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "还没有子任务",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "点击 + 添加子任务",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = subTasks,
                        key = { subTask -> subTask.id },
                    ) { subTask ->
                        SubTaskCard(
                            subTask = subTask,
                            onStartTimer = { onNavigateToTimer(subTask.id) },
                            onToggleComplete = { viewModel.toggleSubTaskCompletion(subTask) },
                            onEdit = {
                                editingSubTask = subTask
                            },
                            onDelete = { viewModel.deleteSubTask(subTask) },
                        )
                    }
                }
            }
        }
    }

    if (showAddSubTaskDialog) {
        AddSubTaskDialog(
            onDismiss = { showAddSubTaskDialog = false },
            onConfirm = { title, type, totalSets, setDetail, setDurationSeconds, restSeconds ->
                viewModel.addSubTask(title, type, totalSets, setDetail, setDurationSeconds, restSeconds)
                showAddSubTaskDialog = false
            }
        )
    }

    if (showSettingsDialog && task != null) {
        TaskSettingsDialog(
            task = task,
            onDismiss = { showSettingsDialog = false },
            onSave = { restSeconds, repeatMode, repeatDays, reminderTime, reminderDate ->
                viewModel.updateTaskSettings(task, restSeconds, repeatMode, repeatDays, reminderTime, reminderDate)
                showSettingsDialog = false
            },
        )
    }

    if (showEditTaskDialog && task != null) {
        EditTaskDialog(
            task = task,
            onDismiss = { showEditTaskDialog = false },
            onSave = { title, description ->
                viewModel.updateTaskBasicInfo(task, title, description)
                showEditTaskDialog = false
            },
        )
    }

    if (editingSubTask != null) {
        EditSubTaskDialog(
            subTask = editingSubTask!!,
            onDismiss = { editingSubTask = null },
            onSave = { updated: SubTask ->
                viewModel.updateSubTask(updated)
                editingSubTask = null
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SubTaskCard(
    subTask: SubTask,
    onStartTimer: () -> Unit,
    onToggleComplete: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val density = LocalDensity.current
    val actionButtonWidthPx = with(density) { 80.dp.toPx() }
    val totalActionWidthPx = actionButtonWidthPx * 2
    val coroutineScope = rememberCoroutineScope()

    val swipeState = remember(subTask.id) {
        AnchoredDraggableState(initialValue = 0)
    }
    SideEffect {
        swipeState.updateAnchors(
            DraggableAnchors {
                0 at 0f
                1 at -totalActionWidthPx
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium),
    ) {
        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f))
                    .clickable {
                        coroutineScope.launch {
                            swipeState.anchoredDrag(targetValue = 0) { anchors, target ->
                                dragTo(anchors.positionOf(target))
                            }
                        }
                        onEdit()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("编辑", color = Color.White, fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier
                    .width(80.dp)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .clickable {
                        coroutineScope.launch {
                            swipeState.anchoredDrag(targetValue = 0) { anchors, target ->
                                dragTo(anchors.positionOf(target))
                            }
                        }
                        onDelete()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "删除",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("删除", color = Color.White, fontSize = 11.sp)
                }
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(swipeState.requireOffset().roundToInt(), 0) }
                .anchoredDraggable(swipeState, Orientation.Horizontal),
            colors = CardDefaults.cardColors(
                containerColor = if (subTask.isCompleted) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.surface
                }
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        coroutineScope.launch {
                            swipeState.anchoredDrag(targetValue = 0) { anchors, target ->
                                dragTo(anchors.positionOf(target))
                            }
                        }
                        onToggleComplete()
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = if (subTask.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                        contentDescription = null,
                        tint = if (subTask.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = subTask.title,
                            style = MaterialTheme.typography.titleMedium,
                            textDecoration = if (subTask.isCompleted) TextDecoration.LineThrough else null,
                            color = if (subTask.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        if (subTask.type == SubTaskType.TIMED) {
                            Text(
                                text = "计时",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        val totalSec = subTask.totalDurationSeconds()
                        if (totalSec > 0) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = formatDuration(totalSec),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.FitnessCenter,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "${subTask.totalSets}组",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "每组${subTask.setDurationSeconds / 60}分钟",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Timer,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "休息${subTask.restDurationSeconds / 60}分钟",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (subTask.type == SubTaskType.REP_COUNT && subTask.setDetail.isNotBlank()) {
                        Text(
                            text = subTask.setDetail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!subTask.isCompleted) {
                    IconButton(onClick = onStartTimer) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "开始",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AddSubTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, type: SubTaskType, totalSets: Int, setDetail: String, setDurationSeconds: Int, restSeconds: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var isTimed by remember { mutableStateOf(false) }
    var totalSets by remember { mutableIntStateOf(3) }
    var setDetail by remember { mutableStateOf("") }
    var setDurationMinutes by remember { mutableIntStateOf(1) }
    var restMinutes by remember { mutableIntStateOf(5) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加子任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = !isTimed,
                        onClick = { isTimed = false },
                        label = { Text("计次") },
                    )
                    androidx.compose.material3.FilterChip(
                        selected = isTimed,
                        onClick = { isTimed = true },
                        label = { Text("计时") },
                    )
                }

                Column {
                    Text("组数: $totalSets", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = totalSets.toFloat(),
                        onValueChange = { totalSets = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                }

                if (!isTimed) {
                    OutlinedTextField(
                        value = setDetail,
                        onValueChange = { setDetail = it },
                        label = { Text("每组详情") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column {
                    Text("每组时长: $setDurationMinutes 分钟", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = setDurationMinutes.toFloat(),
                        onValueChange = { setDurationMinutes = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                }

                Column {
                    Text("休息时间: $restMinutes 分钟", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = restMinutes.toFloat(),
                        onValueChange = { restMinutes = it.toInt() },
                        valueRange = 1f..15f,
                        steps = 13,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        val type = if (isTimed) SubTaskType.TIMED else SubTaskType.REP_COUNT
                        onConfirm(
                            title.trim(),
                            type,
                            totalSets,
                            setDetail.trim(),
                            setDurationMinutes * 60,
                            restMinutes * 60,
                        )
                    }
                },
                enabled = title.isNotBlank(),
            ) {
                Text("添加")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskSettingsDialog(
    task: PlanTask,
    onDismiss: () -> Unit,
    onSave: (restSeconds: Int, repeatMode: RepeatMode, repeatDaysOfWeek: String, reminderTime: String, reminderDate: String) -> Unit,
) {
    var restSeconds by remember { mutableIntStateOf(task.interSubTaskRestSeconds) }
    var repeatMode by remember { mutableStateOf(task.repeatMode) }
    var selectedDays = remember {
        mutableStateListOf(
            *task.repeatDaysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }.toTypedArray()
        )
    }
    var reminderTime by remember { mutableStateOf(task.reminderTime) }
    var reminderDate by remember { mutableStateOf(task.reminderDate) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        val now = java.util.Calendar.getInstance()
        val initialHour = reminderTime.substringBefore(":").toIntOrNull() ?: now.get(java.util.Calendar.HOUR_OF_DAY)
        val initialMinute = reminderTime.substringAfter(":").toIntOrNull() ?: now.get(java.util.Calendar.MINUTE)
        val timePickerState = remember {
            androidx.compose.material3.TimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
        }
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.TimePicker(state = timePickerState)
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            reminderTime = "%02d:%02d".format(timePickerState.hour, timePickerState.minute)
                            showTimePicker = false
                        }) { Text("确定") }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = remember {
            androidx.compose.material3.DatePickerState(
                locale = java.util.Locale.getDefault(),
                initialSelectedDateMillis = null,
            )
        }
        Dialog(onDismissRequest = { showDatePicker = false }) {
            Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
                Column {
                    androidx.compose.material3.DatePicker(state = datePickerState)
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            val millis = datePickerState.selectedDateMillis
                            if (millis != null) {
                                reminderDate = java.time.Instant.ofEpochMilli(millis)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .toLocalDate()
                                    .toString()
                            }
                            showDatePicker = false
                        }) { Text("确定") }
                    }
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(max = 360.dp),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        title = { Text("任务设置") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        "子任务间休息时间: ${restSeconds / 60}分${restSeconds % 60}秒",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Slider(
                        value = restSeconds.toFloat(),
                        onValueChange = { restSeconds = it.toInt() },
                        valueRange = 0f..600f,
                        steps = 19,
                    )
                }

                Column {
                    var repeatExpanded by remember { mutableStateOf(false) }
                    Text("重复", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    ExposedDropdownMenuBox(
                        expanded = repeatExpanded,
                        onExpandedChange = { repeatExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = when (repeatMode) {
                                RepeatMode.NONE -> "不重复"
                                RepeatMode.DAILY -> "每天"
                                RepeatMode.WEEKLY -> "每周"
                                RepeatMode.MONTHLY -> "每月"
                            },
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = repeatExpanded) },
                            modifier = Modifier
                                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = repeatExpanded,
                            onDismissRequest = { repeatExpanded = false },
                        ) {
                            RepeatMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            when (mode) {
                                                RepeatMode.NONE -> "不重复"
                                                RepeatMode.DAILY -> "每天"
                                                RepeatMode.WEEKLY -> "每周"
                                                RepeatMode.MONTHLY -> "每月"
                                            }
                                        )
                                    },
                                    onClick = {
                                        repeatMode = mode
                                        repeatExpanded = false
                                    },
                                )
                            }
                        }
                    }
                }

                if (repeatMode == RepeatMode.WEEKLY) {
                    Column {
                        Text("选择周几", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            val weekDays = listOf(
                                java.util.Calendar.MONDAY to "一",
                                java.util.Calendar.TUESDAY to "二",
                                java.util.Calendar.WEDNESDAY to "三",
                                java.util.Calendar.THURSDAY to "四",
                                java.util.Calendar.FRIDAY to "五",
                                java.util.Calendar.SATURDAY to "六",
                                java.util.Calendar.SUNDAY to "日",
                            )
                            weekDays.forEach { (dayValue, label) ->
                                val selected = dayValue in selectedDays
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(MaterialTheme.shapes.small)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primaryContainer
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) Color.Transparent
                                                else MaterialTheme.colorScheme.outline,
                                            shape = MaterialTheme.shapes.small,
                                        )
                                        .clickable {
                                            if (selected) selectedDays.remove(dayValue)
                                            else selectedDays.add(dayValue)
                                        }
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        label,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                Column {
                    Text("提醒", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showTimePicker = true },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(if (reminderTime.isNotBlank()) reminderTime else "选择时间")
                        }
                        if (repeatMode == RepeatMode.NONE) {
                            OutlinedButton(
                                onClick = { showDatePicker = true },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(if (reminderDate.isNotBlank()) reminderDate else "选择日期")
                            }
                        }
                    }
                    if (reminderTime.isNotBlank()) {
                        TextButton(onClick = {
                            reminderTime = ""
                            reminderDate = ""
                        }) {
                            Text("清除提醒")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val days = if (repeatMode == RepeatMode.WEEKLY) {
                    selectedDays.sorted().joinToString(",")
                } else ""
                onSave(restSeconds, repeatMode, days, reminderTime, reminderDate)
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EditSubTaskDialog(
    subTask: SubTask,
    onDismiss: () -> Unit,
    onSave: (SubTask) -> Unit,
) {
    var title by remember { mutableStateOf(subTask.title) }
    var isTimed by remember { mutableStateOf(subTask.type == SubTaskType.TIMED) }
    var totalSets by remember { mutableIntStateOf(subTask.totalSets) }
    var setDetail by remember { mutableStateOf(subTask.setDetail) }
    var setDurationMinutes by remember { mutableIntStateOf(subTask.setDurationSeconds / 60) }
    var restMinutes by remember { mutableIntStateOf(subTask.restDurationSeconds / 60) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑子任务") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    androidx.compose.material3.FilterChip(
                        selected = !isTimed,
                        onClick = { isTimed = false },
                        label = { Text("计次") },
                    )
                    androidx.compose.material3.FilterChip(
                        selected = isTimed,
                        onClick = { isTimed = true },
                        label = { Text("计时") },
                    )
                }

                Column {
                    Text("组数: $totalSets", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = totalSets.toFloat(),
                        onValueChange = { totalSets = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                }

                if (!isTimed) {
                    OutlinedTextField(
                        value = setDetail,
                        onValueChange = { setDetail = it },
                        label = { Text("每组详情") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Column {
                    Text("每组时长: $setDurationMinutes 分钟", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = setDurationMinutes.toFloat(),
                        onValueChange = { setDurationMinutes = it.toInt() },
                        valueRange = 1f..10f,
                        steps = 8,
                    )
                }

                Column {
                    Text("休息时间: $restMinutes 分钟", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = restMinutes.toFloat(),
                        onValueChange = { restMinutes = it.toInt() },
                        valueRange = 1f..15f,
                        steps = 13,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        val type = if (isTimed) SubTaskType.TIMED else SubTaskType.REP_COUNT
                        onSave(
                            subTask.copy(
                                title = title.trim(),
                                type = type,
                                totalSets = totalSets,
                                setDetail = setDetail.trim(),
                                setDurationSeconds = setDurationMinutes * 60,
                                restDurationSeconds = restMinutes * 60,
                            )
                        )
                    }
                },
                enabled = title.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun EditTaskDialog(
    task: PlanTask,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String) -> Unit,
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑任务") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("任务名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述（可选）") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank()) onSave(title.trim(), description.trim()) },
                enabled = title.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

