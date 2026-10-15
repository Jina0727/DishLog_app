package app.dishlog.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dishlog.data.MealRepository
import app.dishlog.ui.theme.C
import java.time.LocalDate
import java.time.YearMonth

/**
 * 오늘 화면 위에서 아래로 내려오는 월간 달력.
 * 바깥을 누르거나, 패널을 위로 밀거나, 뒤로가기로 닫혀요.
 */
@Composable
fun CalendarPanel(visible: Boolean, repo: MealRepository, onDismiss: () -> Unit, onOpenDay: (Long) -> Unit) {
    if (visible) BackHandler(onBack = onDismiss)

    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f))
                .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInVertically { -it },
        exit = slideOutVertically { -it },
    ) {
        var drag by remember { mutableFloatStateOf(0f) }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
                .background(C.Ground)
                .pointerInput(Unit) { detectTapGestures { } } // 패널 안 탭은 닫히지 않게
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { drag = 0f },
                        onDragEnd = { if (drag < -120f) onDismiss() },
                    ) { _, dy -> drag += dy }
                }
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            MonthCalendar(repo, onOpenDay = { onDismiss(); onOpenDay(it) })
            Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(C.Border))
            }
        }
    }
}

/** 월간 달력. 기록이 있는 날만 점으로 표시. */
@Composable
fun MonthCalendar(repo: MealRepository, onOpenDay: (Long) -> Unit) {
    val today = remember { LocalDate.now() }
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthKey)
    val first = month.atDay(1)
    val logged by repo.daysWithEntries(first.toEpochDay(), month.atEndOfMonth().toEpochDay())
        .collectAsStateWithLifecycle(emptyList())
    val lead = first.dayOfWeek.value % 7 // 일요일 시작
    val rows = (lead + month.lengthOfMonth() + 6) / 7

    Column {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${month.year}년", fontSize = 13.sp, color = C.Muted)
                Text("${month.monthValue}월", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Ink)
            }
            IconButton(onClick = { monthKey = month.minusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "이전 달")
            }
            IconButton(onClick = { monthKey = month.plusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "다음 달")
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 6.dp)) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                Text(it, fontSize = 12.sp, color = C.Muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            }
        }

        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val idx = r * 7 + c - lead
                    if (idx < 0 || idx >= month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(52.dp))
                    } else {
                        val d = first.plusDays(idx.toLong())
                        val has = d.toEpochDay() in logged
                        val isToday = d == today
                        Column(
                            Modifier
                                .weight(1f)
                                .height(52.dp)
                                .clickable(enabled = has && !isToday) { onOpenDay(d.toEpochDay()) }
                                .semantics {
                                    contentDescription = "${d.monthValue}월 ${d.dayOfMonth}일" +
                                        (if (isToday) ", 오늘" else "") + (if (has) ", 기록 있음" else "")
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(if (isToday) C.Ink else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${d.dayOfMonth}",
                                    fontSize = 15.sp,
                                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                                    color = when { isToday -> Color.White; d.isAfter(today) -> C.Faint; else -> C.Ink },
                                )
                            }
                            Box(
                                Modifier.padding(top = 3.dp).size(5.dp).clip(CircleShape)
                                    .background(if (has) C.Accent else Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }
}
