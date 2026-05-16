package com.jtcamp.plansup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.ui.NavDisplay
import com.jtcamp.plansup.reminder.NotificationHelper
import com.jtcamp.plansup.ui.theme.PlanSupTheme
import com.jtcamp.plansup.viewmodel.SubTaskTimerViewModel
import com.jtcamp.plansup.viewmodel.TaskDetailViewModel
import com.jtcamp.plansup.viewmodel.TaskListViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannel(this)
        enableEdgeToEdge()
        setContent {
            PlanSupTheme {
                PlanSupApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

data object TaskListRoute
data object CalendarRoute
data object ProfileRoute
data class TaskDetailRoute(val taskId: Long)
data class SubTaskTimerRoute(val subTaskId: Long)
data class SequentialSubTaskTimerRoute(val taskId: Long)

private val tabRoutes = setOf(TaskListRoute, CalendarRoute, ProfileRoute)

private data class TabConfig(
    val route: Any,
    val label: String,
    val icon: ImageVector,
)

private val tabs = listOf(
    TabConfig(TaskListRoute, "任务", Icons.Filled.CheckCircle),
    TabConfig(CalendarRoute, "日历", Icons.Filled.CalendarMonth),
    TabConfig(ProfileRoute, "我的", Icons.Filled.Person),
)

@Composable
fun PlanSupApp() {
    val context = LocalContext.current
    val activity = context as? ComponentActivity
    val backStack = remember { mutableStateListOf<Any>(TaskListRoute) }

    val taskListViewModel: TaskListViewModel = viewModel()
    val taskDetailViewModel: TaskDetailViewModel = viewModel()
    val timerViewModel: SubTaskTimerViewModel = viewModel()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { _ -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val currentIntent = activity?.intent
    LaunchedEffect(currentIntent) {
        val intent = activity?.intent ?: return@LaunchedEffect
        val taskId = intent.getLongExtra(NotificationHelper.EXTRA_TASK_ID, -1L)
        if (taskId > 0) {
            backStack.add(TaskDetailRoute(taskId))
            intent.removeExtra(NotificationHelper.EXTRA_TASK_ID)
        }
        if (intent.getBooleanExtra("openTimer", false)) {
            intent.removeExtra("openTimer")
            if (timerViewModel.state.value.subTask != null) {
                val alreadyOnTimer = backStack.lastOrNull() is SubTaskTimerRoute
                    || backStack.lastOrNull() is SequentialSubTaskTimerRoute
                if (!alreadyOnTimer) {
                    backStack.add(SubTaskTimerRoute(0))
                }
            }
        }
    }

    val currentRoute = backStack.lastOrNull()
    val showBottomBar = currentRoute in tabRoutes
    val selectedTab = tabs.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(70.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            tabs.forEachIndexed { index, tab ->
                                val selected = index == selectedTab
                                val color = if (selected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(70.dp)
                                        .clickable {
                                            if (currentRoute != tab.route) {
                                                backStack.clear()
                                                backStack.add(tab.route)
                                            }
                                        },
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Icon(
                                        tab.icon,
                                        contentDescription = tab.label,
                                        tint = color,
                                        modifier = Modifier.size(22.dp),
                                    )
                                    Text(
                                        tab.label,
                                        color = color,
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = { key ->
                    when (key) {
                        is TaskListRoute -> NavEntry(key) {
                            TaskListScreen(
                                viewModel = taskListViewModel,
                                onNavigateToDetail = { taskId ->
                                    backStack.add(TaskDetailRoute(taskId))
                                },
                            )
                        }
                        is CalendarRoute -> NavEntry(key) {
                            CalendarScreen(
                                onNavigateToDetail = { taskId ->
                                    backStack.add(TaskDetailRoute(taskId))
                                },
                            )
                        }
                        is ProfileRoute -> NavEntry(key) {
                            ProfileScreen()
                        }
                        is TaskDetailRoute -> NavEntry(key) {
                            TaskDetailScreen(
                                taskId = key.taskId,
                                viewModel = taskDetailViewModel,
                                onNavigateToTimer = { subTaskId ->
                                    backStack.add(SubTaskTimerRoute(subTaskId))
                                },
                                onNavigateToSequentialTimer = { taskId ->
                                    backStack.add(SequentialSubTaskTimerRoute(taskId))
                                },
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        is SubTaskTimerRoute -> NavEntry(key) {
                            SubTaskTimerScreen(
                                subTaskId = key.subTaskId,
                                viewModel = timerViewModel,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        is SequentialSubTaskTimerRoute -> NavEntry(key) {
                            SequentialSubTaskTimerLaucher(
                                taskId = key.taskId,
                                viewModel = timerViewModel,
                                onBack = { backStack.removeLastOrNull() },
                            )
                        }
                        else -> NavEntry(Unit) {
                            Text("Unknown route")
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun ProfileScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = androidx.compose.ui.Alignment.Center,
        ) {
            Text(
                text = "\uD83D\uDC64",
                style = MaterialTheme.typography.displayLarge,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "用户",
            style = MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun SequentialSubTaskTimerLaucher(
    taskId: Long,
    viewModel: SubTaskTimerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var launched by remember { mutableStateOf(false) }

    LaunchedEffect(taskId) {
        if (!launched) {
            launched = true
            val db = com.jtcamp.plansup.data.AppDatabase.getInstance(context)
            val task = db.planTaskDao().getTaskById(taskId) ?: return@LaunchedEffect
            val subTasks = db.planTaskDao().getSubTasksByTaskIdSync(taskId)
            val subTaskIds = subTasks.map { it.id }
            if (subTaskIds.isNotEmpty()) {
                viewModel.startSequentialSubTasks(subTaskIds, task.interSubTaskRestSeconds)
            }
        }
    }

    SubTaskTimerScreen(
        subTaskId = 0,
        viewModel = viewModel,
        onBack = onBack,
    )
}
