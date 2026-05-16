package com.jtcamp.plansup

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jtcamp.plansup.data.AppDatabase
import com.jtcamp.plansup.data.PlanTask
import com.jtcamp.plansup.data.RepeatMode
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.HorizontalYearCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.compose.yearcalendar.rememberYearCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.WeekDay
import com.kizitonwose.calendar.core.WeekDayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Year
import java.time.YearMonth
import java.util.Locale

private enum class CalendarViewMode { WEEK, MONTH, YEAR }

private fun taskAppliesToDate(task: PlanTask, date: LocalDate): Boolean {
    val taskDate = LocalDate.parse(task.date)
    if (date.isBefore(taskDate)) return false
    return when (task.repeatMode) {
        RepeatMode.NONE -> date == taskDate
        RepeatMode.DAILY -> true
        RepeatMode.WEEKLY -> {
            val days = task.repeatDaysOfWeek
                .split(",")
                .mapNotNull { it.trim().toIntOrNull() }
                .map { DayOfWeek.of(it) }
                .toSet()
            if (days.isEmpty()) date == taskDate
            else date.dayOfWeek in days
        }
        RepeatMode.MONTHLY -> date.dayOfMonth == taskDate.dayOfMonth
    }
}

private fun tasksForDate(allTasks: List<PlanTask>, date: LocalDate): List<PlanTask> =
    allTasks.filter { taskAppliesToDate(it, date) }

private fun datesWithTasks(allTasks: List<PlanTask>): Set<LocalDate> {
    val result = mutableSetOf<LocalDate>()
    for (task in allTasks) {
        val start = LocalDate.parse(task.date)
        when (task.repeatMode) {
            RepeatMode.NONE -> result.add(start)
            RepeatMode.DAILY -> {
                val end = LocalDate.now().plusMonths(1)
                var d = start
                while (!d.isAfter(end)) {
                    result.add(d)
                    d = d.plusDays(1)
                }
            }
            RepeatMode.WEEKLY -> {
                val days = task.repeatDaysOfWeek
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .map { DayOfWeek.of(it) }
                    .toSet()
                val end = LocalDate.now().plusMonths(1)
                var d = start
                while (!d.isAfter(end)) {
                    if (d.dayOfWeek in days) result.add(d)
                    d = d.plusDays(1)
                }
            }
            RepeatMode.MONTHLY -> {
                var ym = YearMonth.from(start)
                val endYm = YearMonth.now().plusMonths(1)
                while (!ym.isAfter(endYm)) {
                    val dom = start.dayOfMonth.coerceAtMost(ym.lengthOfMonth())
                    result.add(ym.atDay(dom))
                    ym = ym.plusMonths(1)
                }
            }
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(onNavigateToDetail: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.getInstance(context).planTaskDao() }

    var viewMode by remember { mutableStateOf(CalendarViewMode.MONTH) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(LocalDate.now()) }
    val daysOfWeek = remember { daysOfWeek() }
    val today = remember { LocalDate.now() }

    val allTasks by dao.getAllTasks().collectAsState(initial = emptyList())
    val taskDateSet = remember(allTasks) { datesWithTasks(allTasks) }

    val selectedDateValue = selectedDate
    val selectedTaskList = remember(allTasks, selectedDateValue) {
        selectedDateValue?.let { tasksForDate(allTasks, it) } ?: emptyList()
    }

    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth.minusMonths(120) }
    val endMonth = remember { currentMonth.plusMonths(120) }

    val monthState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = daysOfWeek.first(),
    )

    val weekState = rememberWeekCalendarState(
        startDate = startMonth.atDay(1),
        endDate = endMonth.atEndOfMonth(),
        firstVisibleWeekDate = today,
        firstDayOfWeek = daysOfWeek.first(),
    )

    val currentYear = remember { Year.now() }
    val yearState = rememberYearCalendarState(
        startYear = currentYear.minusYears(10),
        endYear = currentYear.plusYears(10),
        firstVisibleYear = currentYear,
        firstDayOfWeek = daysOfWeek.first(),
    )

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    val titleText = when (viewMode) {
        CalendarViewMode.WEEK -> {
            val first = weekState.firstVisibleWeek.days.first().date
            val ym = YearMonth.from(first)
            ym.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINESE) +
                " ${ym.year}"
        }
        CalendarViewMode.MONTH -> {
            val ym = monthState.firstVisibleMonth.yearMonth
            ym.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.CHINESE) +
                " ${ym.year}"
        }
        CalendarViewMode.YEAR -> "${yearState.firstVisibleYear.year}年"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        LargeTopAppBar(
            title = { Text(titleText) },
            scrollBehavior = scrollBehavior,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            ViewModeSelector(
                selected = viewMode,
                onSelect = { viewMode = it },
            )
        }

        DaysOfWeekHeader(daysOfWeek = daysOfWeek)

        AnimatedContent(targetState = viewMode, label = "calendar") { mode ->
            when (mode) {
                CalendarViewMode.WEEK -> {
                    WeekCalendar(
                        state = weekState,
                        dayContent = { weekDay ->
                            WeekDayCell(
                                weekDay = weekDay,
                                isSelected = selectedDate == weekDay.date,
                                isToday = weekDay.date == today,
                                hasTask = weekDay.date in taskDateSet,
                                onClick = { clicked ->
                                    selectedDate = if (selectedDate == clicked.date) null
                                    else clicked.date
                                },
                            )
                        },
                    )
                }
                CalendarViewMode.MONTH -> {
                    HorizontalCalendar(
                        state = monthState,
                        dayContent = { day ->
                            DayCell(
                                day = day,
                                isSelected = selectedDate == day.date &&
                                    day.position == DayPosition.MonthDate,
                                isToday = day.date == today,
                                hasTask = day.position == DayPosition.MonthDate &&
                                    day.date in taskDateSet,
                                onClick = { clicked ->
                                    if (clicked.position == DayPosition.MonthDate) {
                                        selectedDate = if (selectedDate == clicked.date) null
                                        else clicked.date
                                    }
                                },
                            )
                        },
                        monthHeader = { Spacer(Modifier) },
                    )
                }
                CalendarViewMode.YEAR -> {
                    HorizontalYearCalendar(
                        state = yearState,
                        dayContent = { day ->
                            YearDayCell(
                                day = day,
                                isSelected = selectedDate == day.date &&
                                    day.position == DayPosition.MonthDate,
                                isToday = day.date == today,
                                hasTask = day.position == DayPosition.MonthDate &&
                                    day.date in taskDateSet,
                                onClick = { clicked ->
                                    if (clicked.position == DayPosition.MonthDate) {
                                        selectedDate = if (selectedDate == clicked.date) null
                                        else clicked.date
                                    }
                                },
                            )
                        },
                        monthHeader = { monthData ->
                            Text(
                                text = monthData.yearMonth.month.getDisplayName(
                                    java.time.format.TextStyle.SHORT, Locale.CHINESE,
                                ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        },
                    )
                }
            }
        }

        if (selectedDate != null && selectedTaskList.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            TaskListSection(
                tasks = selectedTaskList,
                onTaskClick = { task -> onNavigateToDetail(task.id) },
            )
        }
    }
}

@Composable
private fun TaskListSection(
    tasks: List<PlanTask>,
    onTaskClick: (PlanTask) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(tasks, key = { it.id }) { task ->
            Card(
                onClick = { onTaskClick(task) },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = task.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (task.description.isNotBlank()) {
                            Text(
                                text = task.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (task.isCompleted) {
                        Text(
                            text = "已完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ViewModeSelector(
    selected: CalendarViewMode,
    onSelect: (CalendarViewMode) -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    val options = listOf(
        CalendarViewMode.WEEK to "周",
        CalendarViewMode.MONTH to "月",
        CalendarViewMode.YEAR to "年",
    )
    Row(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outline, shape)
            .background(MaterialTheme.colorScheme.surfaceContainer),
    ) {
        options.forEachIndexed { index, (mode, label) ->
            val isSelected = selected == mode
            val itemShape = when (index) {
                0 -> RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
                2 -> RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
                else -> RoundedCornerShape(0.dp)
            }
            Box(
                modifier = Modifier
                    .clip(itemShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary
                        else Color.Transparent,
                    )
                    .clickable { onSelect(mode) }
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun DaysOfWeekHeader(daysOfWeek: List<DayOfWeek>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        for (dayOfWeek in daysOfWeek) {
            Text(
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                text = dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, Locale.CHINESE),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TaskDot() {
    Box(
        modifier = Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
    )
}

@Composable
private fun DayCell(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    hasTask: Boolean,
    onClick: (CalendarDay) -> Unit,
) {
    val isMonthDate = day.position == DayPosition.MonthDate
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday && isMonthDate -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
            )
            .clickable(enabled = isMonthDate) { onClick(day) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                color = when {
                    !isMonthDate -> MaterialTheme.colorScheme.outlineVariant
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (hasTask && !isSelected) {
                TaskDot()
            }
        }
    }
}

@Composable
private fun WeekDayCell(
    weekDay: WeekDay,
    isSelected: Boolean,
    isToday: Boolean,
    hasTask: Boolean,
    onClick: (WeekDay) -> Unit,
) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
            )
            .clickable { onClick(weekDay) },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = weekDay.date.dayOfMonth.toString(),
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            if (hasTask && !isSelected) {
                TaskDot()
            }
        }
    }
}

@Composable
private fun YearDayCell(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    hasTask: Boolean,
    onClick: (CalendarDay) -> Unit,
) {
    val isMonthDate = day.position == DayPosition.MonthDate
    Box(
        modifier = Modifier
            .height(24.dp)
            .padding(1.dp)
            .clip(CircleShape)
            .background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday && isMonthDate -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                },
            )
            .clickable(enabled = isMonthDate) { onClick(day) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            color = when {
                !isMonthDate -> MaterialTheme.colorScheme.outlineVariant
                isSelected -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}
