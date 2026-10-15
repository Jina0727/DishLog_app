package app.dishlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dishlog.data.MealRepository
import app.dishlog.ui.theme.C
import java.time.LocalDate
import java.time.YearMonth

/** 월간 달력. 기록이 있는 날만 점으로 표시. */
@Composable
fun CalendarScreen(repo: MealRepository, onOpenDay: (Long) -> Unit, onOpenToday: () -> Unit) {
    val today = remember { LocalDate.now() }
    var monthKey by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val month = YearMonth.parse(monthKey)
    val first = month.atDay(1)
    val last = month.atEndOfMonth()
    val logged by repo.daysWithEntries(first.toEpochDay(), last.toEpochDay()).collectAsStateWithLifecycle(emptyList())
    val lead = first.dayOfWeek.value % 7 // 일요일 시작
    val cells = lead + month.lengthOfMonth()
    val rows = (cells + 6) / 7

    Column(Modifier.fillMaxSize().background(C.Ground).padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${month.year}년", fontSize = 13.sp, color = C.Muted)
                Text("${month.monthValue}월", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.Ink)
            }
            IconButton(onClick = { monthKey = month.minusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "이전 달")
            }
            IconButton(onClick = { monthKey = month.plusMonths(1).toString() }) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "다음 달")
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp)) {
            listOf("일", "월", "화", "수", "목", "금", "토").forEach {
                Text(it, fontSize = 12.sp, color = C.Muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }

        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                for (c in 0..6) {
                    val idx = r * 7 + c - lead
                    if (idx < 0 || idx >= month.lengthOfMonth()) {
                        Spacer(Modifier.weight(1f).height(56.dp))
                    } else {
                        val d = first.plusDays(idx.toLong())
                        val has = d.toEpochDay() in logged
                        val isToday = d == today
                        val future = d.isAfter(today)
                        Column(
                            Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clickable(enabled = has) { if (isToday) onOpenToday() else onOpenDay(d.toEpochDay()) }
                                .semantics { contentDescription = "${d.monthValue}월 ${d.dayOfMonth}일" + (if (isToday) ", 오늘" else "") + (if (has) ", 기록 있음" else "") },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                Modifier.size(32.dp).clip(CircleShape).background(if (isToday) C.Ink else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${d.dayOfMonth}",
                                    fontSize = 15.sp,
                                    fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
                                    color = when { isToday -> Color.White; future -> C.Faint; else -> C.Ink },
                                )
                            }
                            Box(
                                Modifier.padding(top = 4.dp).size(5.dp).clip(CircleShape)
                                    .background(if (has) C.Accent else Color.Transparent),
                            )
                        }
                    }
                }
            }
        }
    }
}
