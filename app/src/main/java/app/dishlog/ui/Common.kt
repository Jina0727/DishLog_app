package app.dishlog.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dishlog.data.EstimateState
import app.dishlog.data.FULLNESS_LABELS
import app.dishlog.data.MealEntry
import app.dishlog.data.TagResolver
import app.dishlog.data.TimeSlot
import app.dishlog.data.minuteOfDay
import app.dishlog.data.totalKcal
import app.dishlog.ui.theme.C
import coil.compose.AsyncImage
import java.io.File
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hhmm = DateTimeFormatter.ofPattern("HH:mm")
private val dateLong = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)

fun Long.hhmm(): String = hhmm.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))
fun LocalDate.koreanLong(): String = dateLong.format(this)
fun Int.grouped(): String = NumberFormat.getIntegerInstance(Locale.KOREA).format(this)
fun Int.clock(): String = "%02d:%02d".format(this / 60, this % 60)

fun tagOf(e: MealEntry) = e.tag ?: "태그 없음"

/** 지금 찍으면 붙을 태그와 시간. 촬영 화면 상단 칩에 써요. */
fun currentTagLabel(slots: List<TimeSlot>, nearest: Boolean): String {
    val now = System.currentTimeMillis()
    val tag = TagResolver.resolve(minuteOfDay(now), slots, nearest) ?: "태그 없음"
    return "$tag · ${now.hhmm()}"
}

fun kcalLine(e: MealEntry): String = when (e.estimateState) {
    EstimateState.PENDING -> "칼로리 계산 중…"
    EstimateState.FAILED -> "칼로리 추정 실패"
    else -> e.totalKcal()?.let { "약 ${it.grouped()} kcal" } ?: ""
}

@Composable
fun Photo(path: String?, modifier: Modifier, contentDescription: String?) {
    Box(modifier.background(C.Placeholder)) {
        if (path != null) {
            AsyncImage(
                model = File(path),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

/** 기록에 붙은 칩들: 태그 · 포만도 (고른 것만). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryChips(e: MealEntry, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TagChip(e.tag)
        e.fullness?.let { FullnessChip(it) }
    }
}

/**
 * 타임라인 카드 (시간은 카드 밖 왼쪽 축에 따로 표시해요).
 * [사진] [태그 칩 ····· 포만도]
 *        [칼로리 크게]
 * 사진이 여러 장이면 사진 모서리에 작은 숫자 배지.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EntryCard(e: MealEntry, onClick: () -> Unit, onLongClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(C.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "수정·삭제 메뉴")
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Photo(e.photos.firstOrNull(), Modifier.size(72.dp).clip(RoundedCornerShape(13.dp)), "${tagOf(e)} ${e.takenAt.hhmm()} 사진")
            if (e.photos.size > 1) {
                Text(
                    "${e.photos.size}",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopEnd).padding(5.dp)
                        .clip(RoundedCornerShape(8.dp)).background(C.Ink.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
        }
        Column(
            Modifier.weight(1f).padding(start = 12.dp, end = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TagChip(e.tag)
                Spacer(Modifier.weight(1f))
                e.fullness?.let { f ->
                    LevelDots(f, C.Muted)
                    Text(" ${FULLNESS_LABELS[f]}", fontSize = 12.sp, color = C.Muted)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val kcal = e.totalKcal()
                when {
                    e.estimateState == EstimateState.PENDING -> {
                        CircularProgressIndicator(Modifier.size(12.dp), strokeWidth = 1.5.dp, color = C.Muted)
                        Text("  계산 중", fontSize = 13.sp, color = C.Muted)
                    }
                    kcal != null -> {
                        Text(kcal.grouped(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                        Text(" kcal", fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(top = 3.dp))
                    }
                    else -> Text("칼로리 –", fontSize = 13.sp, color = C.Faint)
                }
            }
        }
    }
}

/** 타임라인 한 줄: 왼쪽에 시간, 다음 기록까지 세로선으로 이어져요. */
@Composable
fun TimelineRow(time: String, last: Boolean, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().drawBehind {
            if (!last) {
                val x = 24.dp.toPx()
                drawLine(
                    color = C.Border,
                    start = Offset(x, 30.dp.toPx()),
                    end = Offset(x, size.height + 10.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
        },
    ) {
        Box(Modifier.width(48.dp).padding(top = 8.dp), contentAlignment = Alignment.TopCenter) {
            Text(time, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
}
