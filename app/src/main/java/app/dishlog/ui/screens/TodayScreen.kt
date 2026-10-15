package app.dishlog.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dishlog.data.MealRepository
import app.dishlog.data.totalKcal
import app.dishlog.ui.EntryCard
import app.dishlog.ui.grouped
import app.dishlog.ui.koreanLong
import app.dishlog.ui.theme.C
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/**
 * 오늘 화면. 맨 위에서 아래로 크게 당기면 월간 달력이 내려와요.
 * (제스처가 어려운 경우를 위해 상단 달력 아이콘으로도 열 수 있어요)
 */
@Composable
fun TodayScreen(
    repo: MealRepository,
    onOpenDay: (Long) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onCapture: () -> Unit,
    onSettings: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val weekStart = remember { today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)) }
    val entries by repo.day(today.toEpochDay()).collectAsStateWithLifecycle(emptyList())
    val logged by repo.daysWithEntries(weekStart.toEpochDay(), weekStart.plusDays(6).toEpochDay())
        .collectAsStateWithLifecycle(emptyList())
    val total = entries.mapNotNull { it.totalKcal() }.sum()

    var calendarOpen by rememberSaveable { mutableStateOf(false) }
    var pull by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 110.dp.toPx() }
    val shown by animateFloatAsState(pull, label = "pull")

    // 목록이 맨 위일 때 더 아래로 당긴 거리를 모아요. 손을 뗐을 때 기준을 넘었으면 달력을 열어요.
    val pullToCalendar = remember(threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (pull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f) {
                    pull = (pull + available.y * 0.5f).coerceAtMost(threshold * 1.6f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pull >= threshold) calendarOpen = true
                val wasPulling = pull > 0f
                pull = 0f
                return if (wasPulling) available else Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().background(C.Ground)) {
        // 당기는 동안 위에 살짝 보이는 안내
        Row(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)
                .alpha((shown / threshold).coerceIn(0f, 1f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = C.Muted, modifier = Modifier.size(18.dp))
            Text(if (shown >= threshold) "놓으면 달력이 열려요" else "당겨서 달력 보기", fontSize = 12.sp, color = C.Muted)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().nestedScroll(pullToCalendar).graphicsLayer { translationY = shown },
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(Modifier.statusBarsPadding().fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(top = 12.dp)) {
                        Text("${today.year}년 ${today.monthValue}월", fontSize = 13.sp, color = C.Muted)
                        Text(today.koreanLong().substringAfter("월 "), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    }
                    IconButton(onClick = { calendarOpen = true }) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "달력 열기") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "설정") }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (i in 0..6) {
                        val d = weekStart.plusDays(i.toLong())
                        val isToday = d == today
                        val past = d.isBefore(today)
                        val has = d.toEpochDay() in logged
                        Column(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isToday) C.Ink else Color.Transparent)
                                .clickable(enabled = past) { onOpenDay(d.toEpochDay()) }
                                .semantics { contentDescription = "${d.monthValue}월 ${d.dayOfMonth}일" + if (has) ", 기록 있음" else "" },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            val fg = when { isToday -> Color.White; past -> C.Muted; else -> C.Faint }
                            Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.KOREAN), fontSize = 11.sp, color = fg)
                            Text("${d.dayOfMonth}", fontSize = 15.sp, fontWeight = FontWeight.Medium, color = fg)
                            Box(
                                Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape)
                                    .background(if (has) (if (isToday) Color.White else C.AccentSoft) else Color.Transparent),
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(20.dp)).background(C.Surface)
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("오늘 추정 섭취", fontSize = 13.sp, color = C.Muted)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(if (total > 0) total.grouped() else "–", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                            Text(" kcal", fontSize = 14.sp, color = C.Muted, modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("기록", fontSize = 13.sp, color = C.Muted)
                        Text("${entries.size}개", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                    }
                }
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "아직 오늘 기록이 없어요.\n찍기를 누르면 바로 남길 수 있어요.",
                        fontSize = 14.sp, color = C.Muted, lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 24.dp, start = 4.dp),
                    )
                }
            }
            items(entries, key = { it.id }) { e -> EntryCard(e) { onOpenEntry(e.id) } }
        }

        ExtendedFloatingActionButton(
            onClick = onCapture,
            containerColor = C.Accent,
            contentColor = Color.White,
            shape = RoundedCornerShape(30.dp),
            icon = { Icon(Icons.Outlined.PhotoCamera, contentDescription = null) },
            text = { Text("찍기", fontWeight = FontWeight.SemiBold, fontSize = 16.sp) },
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(24.dp),
        )

        CalendarPanel(
            visible = calendarOpen,
            repo = repo,
            onDismiss = { calendarOpen = false },
            onOpenDay = onOpenDay,
        )
    }
}
