package app.dishlog.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dishlog.data.EstimateState
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

@Composable
fun EntryCard(e: MealEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(C.Surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Photo(e.photos.firstOrNull(), Modifier.size(84.dp).clip(RoundedCornerShape(14.dp)), "${tagOf(e)} 사진")
        Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(e.takenAt.hhmm(), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                val k = kcalLine(e)
                if (k.isNotBlank()) Text(k, fontSize = 13.sp, color = C.Muted)
                if (e.photos.size > 1) Text("${e.photos.size}장", fontSize = 12.sp, color = C.Muted)
            }
            EntryChips(e)
            val desc = e.note.ifBlank { e.items.joinToString(", ") { it.name } }
            if (desc.isNotBlank()) {
                Text(desc, fontSize = 13.sp, color = C.Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
}
