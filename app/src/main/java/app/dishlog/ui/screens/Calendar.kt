package app.dishlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dishlog.ui.theme.C
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

private val WEEKDAYS = listOf("일", "월", "화", "수", "목", "금", "토")

fun LocalDate.weekStart(): LocalDate = with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))

@Composable
private fun WeekdayHeader() {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        WEEKDAYS.forEach {
            Text(it, fontSize = 11.sp, color = C.Muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        }
    }
}

/** 날짜 칸 하나: 고른 날은 검은 원, 오늘은 테두리, 기록 있으면 아래 점. 미래는 흐리게·누를 수 없음. */
@Composable
private fun RowScope.DayCell(d: LocalDate, selected: LocalDate, today: LocalDate, has: Boolean, onPick: (LocalDate) -> Unit) {
    val isSel = d == selected
    val isToday = d == today
    val future = d.isAfter(today)
    Column(
        Modifier
            .weight(1f)
            .height(50.dp)
            .clickable(enabled = !future) { onPick(d) }
            .semantics {
                this.selected = isSel
                contentDescription = "${d.monthValue}월 ${d.dayOfMonth}일" + (if (isToday) ", 오늘" else "") + (if (has) ", 기록 있음" else "")
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape)
                .background(if (isSel) C.Ink else Color.Transparent)
                .then(if (isToday && !isSel) Modifier.border(1.dp, C.Ink, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${d.dayOfMonth}",
                fontSize = 15.sp,
                fontWeight = if (isSel || isToday) FontWeight.SemiBold else FontWeight.Medium,
                color = when { isSel -> Color.White; future -> C.Faint; else -> C.Ink },
            )
        }
        Box(
            Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape)
                .background(if (has) C.Accent else Color.Transparent),
        )
    }
}

/** 접혔을 때: 고른 날짜가 있는 한 주. */
@Composable
fun WeekStrip(selected: LocalDate, today: LocalDate, logged: Set<Long>, onPick: (LocalDate) -> Unit) {
    val start = selected.weekStart()
    Column {
        WeekdayHeader()
        Row(Modifier.fillMaxWidth()) {
            for (i in 0..6) {
                val d = start.plusDays(i.toLong())
                DayCell(d, selected, today, d.toEpochDay() in logged, onPick)
            }
        }
    }
}

/** 펼쳤을 때: 한 달 전체. 위쪽 화살표로 달을 넘겨요. */
@Composable
fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    logged: Set<Long>,
    onPick: (LocalDate) -> Unit,
    onMonth: (YearMonth) -> Unit,
) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value % 7 // 일요일 시작
    val rows = (lead + month.lengthOfMonth() + 6) / 7
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${month.year}년 ${month.monthValue}월", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink,
                modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "이전 달")
            }
            IconButton(onClick = { onMonth(month.plusMonths(1)) }, enabled = month.isBefore(YearMonth.from(today))) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "다음 달")
            }
        }
        WeekdayHeader()
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0..6) {
                    val idx = r * 7 + c - lead
                    if (idx < 0 || idx >= month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(50.dp))
                    } else {
                        val d = first.plusDays(idx.toLong())
                        DayCell(d, selected, today, d.toEpochDay() in logged, onPick)
                    }
                }
            }
        }
    }
}
