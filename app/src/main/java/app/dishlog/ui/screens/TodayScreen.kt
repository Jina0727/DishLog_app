package app.dishlog.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dishlog.data.MealRepository
import app.dishlog.data.totalKcal
import app.dishlog.ui.Photo
import app.dishlog.ui.TimelineRow
import app.dishlog.ui.grouped
import app.dishlog.ui.hhmm
import app.dishlog.ui.koreanLong
import app.dishlog.ui.tagOf
import app.dishlog.ui.theme.C
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

private const val PAGE_COUNT = 3650 // 약 10년치
private const val LAST = PAGE_COUNT - 1 // 마지막 페이지 = 오늘

/**
 * 홈 화면.
 * - 좌우로 넘기면 전날/다음날 기록
 * - 맨 위에서 아래로 당기면 주간 줄이 월간 달력으로 펼쳐지고 카드들이 그만큼 아래로 밀려나요
 * - 목록을 위로 올리면 다시 주간 줄로 접혀요
 */
@Composable
fun TodayScreen(
    repo: MealRepository,
    onOpenEntry: (Long) -> Unit,
    onCapture: () -> Unit,
    onSettings: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(initialPage = LAST) { PAGE_COUNT }
    fun dateOf(page: Int): LocalDate = today.minusDays((LAST - page).toLong())
    fun pageOf(d: LocalDate): Int = (LAST - ChronoUnit.DAYS.between(d, today)).toInt().coerceIn(0, LAST)
    val selected = dateOf(pager.currentPage)

    var expanded by rememberSaveable { mutableStateOf(false) }
    var photoMode by rememberSaveable { mutableStateOf(false) }
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthKey)
    LaunchedEffect(selected) { monthKey = YearMonth.from(selected).toString() }

    // 기록 있는 날 점: 펼쳤을 땐 보이는 달, 접혔을 땐 고른 주
    val (from, to) = if (expanded) month.atDay(1) to month.atEndOfMonth()
    else selected.weekStart() to selected.weekStart().plusDays(6)
    val logged by remember(from, to) { repo.daysWithEntries(from.toEpochDay(), to.toEpochDay()) }
        .collectAsStateWithLifecycle(emptyList())
    val loggedSet = logged.toSet()

    // 날짜를 골라도 달력은 펼친 그대로 둬요. 접는 건 위로 올리거나 손잡이를 눌렀을 때만.
    fun pick(d: LocalDate) {
        scope.launch { pager.animateScrollToPage(pageOf(d)) }
    }

    // 당김 제스처: 목록 맨 위에서 더 당기면 펼치고, 펼친 상태에서 위로 올리면 접어요.
    var pull by remember { mutableFloatStateOf(0f) }
    val threshold = with(LocalDensity.current) { 80.dp.toPx() }
    val shown by animateFloatAsState(pull, spring(stiffness = 600f), label = "pull")
    val gesture = remember(threshold) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (expanded && available.y < 0f) {
                    expanded = false
                    return Offset(0f, available.y)
                }
                if (pull > 0f && available.y < 0f) {
                    val used = maxOf(available.y, -pull)
                    pull += used
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0f && !expanded) {
                    pull = (pull + available.y * 0.45f).coerceAtMost(threshold * 1.5f)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                val wasPulling = pull > 0f
                if (pull >= threshold) expanded = true
                pull = 0f
                return if (wasPulling) available else Velocity.Zero
            }
        }
    }

    Box(Modifier.fillMaxSize().background(C.Ground)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // 머리글: 고른 날짜 + 보기 전환 + 설정
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${selected.year}년 ${selected.monthValue}월", fontSize = 13.sp, color = C.Muted)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selected.koreanLong().substringAfter("월 "), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                        if (selected != today) {
                            TextButton(onClick = { pick(today) }) { Text("오늘", color = C.Accent, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                IconButton(onClick = { photoMode = !photoMode }) {
                    Icon(
                        if (photoMode) Icons.Outlined.ViewAgenda else Icons.Outlined.GridView,
                        contentDescription = if (photoMode) "카드로 보기" else "사진만 모아보기",
                    )
                }
                IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, contentDescription = "설정") }
            }

            // 달력: 접히면 한 주, 펼치면 한 달. 높이가 바뀌면 아래 카드들이 같이 밀려요.
            Column(
                Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp)
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectVerticalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag > 60f) expanded = true
                                if (drag < -60f) expanded = false
                            },
                        ) { _, dy -> drag += dy }
                    },
            ) {
                if (expanded) {
                    MonthGrid(month, selected, today, loggedSet, onPick = ::pick, onMonth = { monthKey = it.toString() })
                } else {
                    WeekStrip(selected, today, loggedSet, onPick = ::pick)
                }
                // 손잡이: 눌러도 펼치기/접기
                Box(
                    Modifier.fillMaxWidth().height(20.dp)
                        .clickable { expanded = !expanded }
                        .semantics { contentDescription = if (expanded) "달력 접기" else "한 달 달력 펼치기" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(C.Border))
                }
            }

            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .nestedScroll(gesture)
                    .graphicsLayer { translationY = shown },
                key = { it },
            ) { page ->
                DayPage(repo, dateOf(page), today, photoMode, onOpenEntry)
            }
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
    }
}

/** 하루치 페이지: 요약 + 카드 목록(또는 사진 모아보기). */
@Composable
private fun DayPage(repo: MealRepository, date: LocalDate, today: LocalDate, photoMode: Boolean, onOpenEntry: (Long) -> Unit) {
    val entries by remember(date) { repo.day(date.toEpochDay()) }.collectAsStateWithLifecycle(emptyList())
    val total = entries.mapNotNull { it.totalKcal() }.sum()
    val padding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 120.dp)

    val summary: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 4.dp).clip(RoundedCornerShape(20.dp)).background(C.Surface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (date == today) "오늘 추정 섭취" else "이날 추정 섭취", fontSize = 13.sp, color = C.Muted)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (total > 0) total.grouped() else "–", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    Text(" kcal", fontSize = 14.sp, color = C.Muted, modifier = Modifier.padding(bottom = 5.dp))
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("기록", fontSize = 13.sp, color = C.Muted)
                Text("${entries.size}개", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
            }
        }
    }
    val empty: @Composable () -> Unit = {
        Text(
            if (date == today) "아직 오늘 기록이 없어요.\n찍기를 누르면 바로 남길 수 있어요." else "이 날은 기록이 없어요.",
            fontSize = 14.sp, color = C.Muted, lineHeight = 22.sp,
            modifier = Modifier.padding(top = 20.dp, start = 4.dp),
        )
    }

    if (photoMode) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = padding,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(2) }) { summary() }
            if (entries.isEmpty()) item(span = { GridItemSpan(2) }) { empty() }
            items(entries.size, key = { entries[it].id }) { i ->
                val e = entries[i]
                Box(Modifier.aspectRatio(1f).clip(RoundedCornerShape(14.dp)).clickable { onOpenEntry(e.id) }) {
                    Photo(e.photos.firstOrNull(), Modifier.fillMaxSize(), "${tagOf(e)} ${e.takenAt.hhmm()} 사진")
                    Text(
                        "${tagOf(e)} ${e.takenAt.hhmm()}",
                        color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                            .clip(RoundedCornerShape(10.dp)).background(C.Ink.copy(alpha = 0.7f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    } else {
        LazyColumn(
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { summary() }
            if (entries.isEmpty()) item { empty() }
            itemsIndexed(entries, key = { _, e -> e.id }) { i, e ->
                TimelineRow(e.takenAt.hhmm(), last = i == entries.lastIndex) {
                    ActionableEntryCard(repo, e) { onOpenEntry(e.id) }
                }
            }
        }
    }
}
