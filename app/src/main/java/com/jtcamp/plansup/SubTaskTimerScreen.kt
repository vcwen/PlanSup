package com.jtcamp.plansup

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jtcamp.plansup.viewmodel.SubTaskTimerViewModel
import com.jtcamp.plansup.viewmodel.TimerPhase
import com.jtcamp.plansup.viewmodel.TimerState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubTaskTimerScreen(
    subTaskId: Long,
    viewModel: SubTaskTimerViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    val activity = LocalContext.current as? Activity
    DisposableEffect(activity) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    if (state.subTask == null) {
        viewModel.startSubTask(subTaskId)
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    val subTask = state.subTask
                    if (state.totalSubTaskCount > 1 && subTask != null) {
                        Column {
                            Text(
                                text = subTask.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "子任务 ${state.currentSubTaskIndex}/${state.totalSubTaskCount}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        Text(subTask?.title ?: "训练")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center,
        ) {
            when (state.phase) {
                TimerPhase.ANNOUNCING -> AnnouncingContent(state = state)
                TimerPhase.COUNTDOWN -> CountdownContent(state = state)
                TimerPhase.EXERCISING -> ExercisingContent(
                    state = state,
                    onCompleteSet = { viewModel.completeSet() },
                    onRestart = { viewModel.restartPhase() },
                )
                TimerPhase.EXERCISING_TIMED -> ExercisingTimedContent(
                    state = state,
                    onCompleteSet = { viewModel.completeSet() },
                    onTogglePause = { viewModel.togglePause() },
                    onRestart = { viewModel.restartPhase() },
                )
                TimerPhase.RESTING -> RestingContent(
                    state = state,
                    onSkipRest = { viewModel.skipRest() },
                    onTogglePause = { viewModel.togglePause() },
                    onRestart = { viewModel.restartPhase() },
                )
                TimerPhase.SUBTASK_RESTING -> SubTaskRestingContent(
                    state = state,
                    onSkipRest = { viewModel.skipSubTaskRest() },
                    onTogglePause = { viewModel.togglePause() },
                    onRestart = { viewModel.restartPhase() },
                )
                TimerPhase.COMPLETED -> CompletedContent(
                    state = state,
                    onBack = onBack,
                )
            }
        }
    }
}

@Composable
private fun AnnouncingContent(
    state: TimerState,
) {
    val subTask = state.subTask ?: return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "准备开始",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = subTask.title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (subTask.setDetail.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subTask.setDetail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(48.dp))

        Text(
            text = "第 ${state.currentSet} / ${subTask.totalSets} 组",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CountdownContent(
    state: TimerState,
) {
    val subTask = state.subTask ?: return
    val seconds = state.countdownSeconds

    val animatedScale by animateFloatAsState(
        targetValue = if (seconds > 0) 1f else 0.5f,
        animationSpec = tween(durationMillis = 600, easing = LinearEasing),
        label = "countdownScale",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "第 ${state.currentSet} / ${subTask.totalSets} 组",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (subTask.setDetail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subTask.setDetail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(48.dp))

        Box(contentAlignment = Alignment.Center) {
            val primaryColor = MaterialTheme.colorScheme.primary
            val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
            val strokeWidth = 12.dp
            val density = LocalDensity.current
            val strokeWidthPx = with(density) { strokeWidth.toPx() }

            val progress = seconds.toFloat() / 3f
            val animatedProgress by animateFloatAsState(
                targetValue = progress,
                animationSpec = tween(durationMillis = 300, easing = LinearEasing),
                label = "countdownProgress",
            )

            Canvas(modifier = Modifier.size(220.dp)) {
                val canvasSize = size.minDimension
                val radius = (canvasSize - strokeWidthPx * 2) / 2
                val arcSize = Size(radius * 2, radius * 2)
                val topLeft = Offset(
                    x = (canvasSize - radius * 2) / 2,
                    y = (canvasSize - radius * 2) / 2,
                )

                drawArc(
                    color = backgroundColor,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                )

                drawArc(
                    color = primaryColor,
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                )
            }

            Text(
                text = "$seconds",
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "准备开始",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ExercisingContent(
    state: TimerState,
    onCompleteSet: () -> Unit,
    onRestart: () -> Unit,
) {
    val subTask = state.subTask ?: return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "第 ${state.currentSet} / ${subTask.totalSets} 组",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        if (subTask.setDetail.isNotBlank()) {
            Text(
                text = subTask.setDetail,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onCompleteSet,
            modifier = Modifier.padding(horizontal = 32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("完成本组", style = MaterialTheme.typography.titleMedium)
        }

        Spacer(Modifier.height(12.dp))

        OutlinedButton(onClick = onRestart) {
            Icon(Icons.Filled.Replay, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("重新开始")
        }

        if (state.currentSet < subTask.totalSets) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "完成后休息 ${subTask.restDurationSeconds / 60} 分钟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "最后一组！加油！",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun SubTaskRestingContent(
    state: TimerState,
    onSkipRest: () -> Unit,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
) {
    val progress = if (state.totalRestSeconds > 0) {
        state.remainingSeconds.toFloat() / state.totalRestSeconds.toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "subtaskRestProgress",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (state.isPaused) "休息已暂停" else "子任务间休息",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "${state.subTask?.title ?: ""} 已完成",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(32.dp))

        CircularCountdown(
            progress = animatedProgress,
            remainingSeconds = state.remainingSeconds,
        )

        Spacer(Modifier.height(16.dp))

        if (state.pendingSubTaskIds.isNotEmpty()) {
            Text(
                text = "下一个子任务 (${state.currentSubTaskIndex + 1}/${state.totalSubTaskCount})",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onTogglePause) {
                Icon(
                    if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (state.isPaused) "继续" else "暂停")
            }
            OutlinedButton(onClick = onRestart) {
                Icon(Icons.Filled.Replay, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("重来")
            }
            OutlinedButton(onClick = onSkipRest) {
                Icon(Icons.Filled.SkipNext, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("跳过")
            }
        }
    }
}

@Composable
private fun ExercisingTimedContent(
    state: TimerState,
    onCompleteSet: () -> Unit,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
) {
    val subTask = state.subTask ?: return

    val progress = if (state.setTotalSeconds > 0) {
        state.setRemainingSeconds.toFloat() / state.setTotalSeconds.toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "setProgress",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (state.isPaused) "已暂停" else "第 ${state.currentSet} / ${subTask.totalSets} 组",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(32.dp))

        CircularCountdown(
            progress = animatedProgress,
            remainingSeconds = state.setRemainingSeconds,
        )

        Spacer(Modifier.height(16.dp))

        if (subTask.setDetail.isNotBlank()) {
            Text(
                text = subTask.setDetail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onTogglePause) {
                Icon(
                    if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (state.isPaused) "继续" else "暂停")
            }
            OutlinedButton(onClick = onRestart) {
                Icon(Icons.Filled.Replay, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("重来")
            }
            OutlinedButton(onClick = onCompleteSet) {
                Text("跳过本组")
            }
        }
    }
}

@Composable
private fun RestingContent(
    state: TimerState,
    onSkipRest: () -> Unit,
    onTogglePause: () -> Unit,
    onRestart: () -> Unit,
) {
    val subTask = state.subTask ?: return
    val progress = if (state.totalRestSeconds > 0) {
        state.remainingSeconds.toFloat() / state.totalRestSeconds.toFloat()
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "restProgress",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (state.isPaused) "休息已暂停" else "休息中",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        CircularCountdown(
            progress = animatedProgress,
            remainingSeconds = state.remainingSeconds,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "下一组: 第 ${state.currentSet + 1} / ${subTask.totalSets} 组",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onTogglePause) {
                Icon(
                    if (state.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                )
                Spacer(Modifier.width(4.dp))
                Text(if (state.isPaused) "继续" else "暂停")
            }
            OutlinedButton(onClick = onRestart) {
                Icon(Icons.Filled.Replay, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("重来")
            }
            OutlinedButton(onClick = onSkipRest) {
                Icon(Icons.Filled.SkipNext, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("跳过")
            }
        }
    }
}

@Composable
private fun CircularCountdown(
    progress: Float,
    remainingSeconds: Int,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant
    val strokeWidth = 12.dp
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { strokeWidth.toPx() }

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val canvasSize = size.minDimension
            val radius = (canvasSize - strokeWidthPx * 2) / 2
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset(
                x = (canvasSize - radius * 2) / 2,
                y = (canvasSize - radius * 2) / 2,
            )

            drawArc(
                color = backgroundColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )

            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatSeconds(remainingSeconds),
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun CompletedContent(
    state: TimerState,
    onBack: () -> Unit,
) {
    val subTask = state.subTask ?: return

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "\uD83C\uDF89",
            fontSize = 64.sp,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = "${subTask.title} 完成！",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "共完成 ${subTask.totalSets} 组",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        Button(onClick = onBack) {
            Text("返回任务详情")
        }
    }
}

private fun formatSeconds(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
}
