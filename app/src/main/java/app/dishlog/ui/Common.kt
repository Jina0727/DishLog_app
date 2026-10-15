package app.dishlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dishlog.data.EstimateState
import app.dishlog.data.FULLNESS_LABELS
import app.dishlog.data.MealEntry
import app.dishlog.data.TimeSlot
import app.dishlog.data.TagResolver
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

fun tagOf(e: MealEntry) = e.tag ?: "태그 없음"

/** 지금 찍으면 붙을 태그와 시간. 촬영 화면 상단 칩에 써요. */
fun currentTagLabel(slots: List<TimeSlot>, nearest: Boolean): String {
    val now = System.currentTimeMillis()
    val tag = TagResolver.resolve(minuteOfDay(now), slots, nearest) ?: "태그 없음"
    return "$tag · ${now.hhmm()}"
}

fun kcalLine(e: MealEntry): String = when (e.estimateState) {
    EstimateState.PENDING -> "칼로리 계산 중…"
    EstimateState.FAILED -> "추정 실패"
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
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Photo(
            e.photos.firstOrNull(),
            Modifier.size(76.dp).clip(RoundedCornerShape(14.dp)),
            "${tagOf(e)} 사진",
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tagOf(e), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = if (e.tag == null) C.Muted else C.Ink)
                Text(e.takenAt.hhmm(), fontSize = 12.sp, color = C.Muted)
                if (e.photos.size > 1) Text("${e.photos.size}장", fontSize = 12.sp, color = C.Muted)
            }
            val desc = e.items.joinToString(", ") { it.name }.ifBlank { e.note }
            if (desc.isNotBlank()) Text(desc, fontSize = 14.sp, color = C.Body, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val k = kcalLine(e)
                if (k.isNotBlank()) Text(k, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                e.fullness?.let { Text("포만 ${FULLNESS_LABELS[it]}", fontSize = 12.sp, color = C.Muted) }
            }
        }
    }
}

/** 세 칸짜리 선택 버튼 (먹은 양, 포만도). 같은 걸 다시 누르면 선택 해제할 수 있게 onPick 에 index 를 넘겨요. */
@Composable
fun ChoiceRow(labels: List<String>, selected: Int?, fill: androidx.compose.ui.graphics.Color, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            ChoiceButton(label, selected == i, fill) { onPick(i) }
        }
    }
}

@Composable
private fun RowScope.ChoiceButton(label: String, on: Boolean, fill: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier
            .weight(1f)
            .height(48.dp)
            .clip(shape)
            .then(if (on) Modifier.background(fill) else Modifier.background(C.Surface).border(1.dp, C.Border, shape))
            .semantics { selected = on }
            .clickable(role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            color = if (on) androidx.compose.ui.graphics.Color.White else C.Body,
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
}
