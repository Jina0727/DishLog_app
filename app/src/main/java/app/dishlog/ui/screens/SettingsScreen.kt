package app.dishlog.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dishlog.data.MealRepository
import app.dishlog.data.TimeSlot
import app.dishlog.ui.SectionTitle
import app.dishlog.ui.theme.C
import kotlinx.coroutines.launch

private val slotColors = listOf(C.Accent, C.AccentSoft, C.AccentDeep, Color(0xFFB4532A), Color(0xFF6B7FA3))

private fun Int.clock() = "%02d:%02d".format(this / 60, this % 60)

/** 식사 구간 설정 + 기본 동작 설정. */
@Composable
fun SettingsScreen(repo: MealRepository) {
    val scope = rememberCoroutineScope()
    val slots by repo.slots.collectAsStateWithLifecycle(emptyList())
    var editing by remember { mutableStateOf<TimeSlot?>(null) }
    var nearest by remember { mutableStateOf(repo.prefs.outsideNearest) }
    var autoCamera by remember { mutableStateOf(repo.prefs.autoCamera) }

    Column(
        Modifier.fillMaxSize().background(C.Ground).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("식사 구간", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.Ink, modifier = Modifier.padding(start = 4.dp))
        Text("이 시간에 찍은 사진엔 해당 태그가 자동으로 붙어요.", fontSize = 14.sp, color = C.Body, modifier = Modifier.padding(start = 4.dp))

        DayBar(slots)

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface)) {
            slots.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = Color(0xFFEEEEEA))
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clickable { editing = s }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(CircleShape).background(slotColors[i % slotColors.size]))
                    Text(s.name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = C.Ink, modifier = Modifier.weight(1f))
                    Text("${s.startMin.clock()} – ${s.endMin.clock()}", fontSize = 14.sp, color = C.Body)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = C.Muted)
                }
            }
            if (slots.isEmpty()) {
                Text("구간이 없어요. 찍은 사진은 태그 없이 저장돼요.", fontSize = 14.sp, color = C.Muted, modifier = Modifier.padding(16.dp))
            }
        }

        OutlinedButton(
            onClick = { editing = TimeSlot(name = "", startMin = 12 * 60, endMin = 13 * 60) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, C.Border),
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null, tint = C.Body)
            Text(" 구간 추가", color = C.Body, fontWeight = FontWeight.SemiBold)
        }

        SectionTitle("구간 밖에서 찍으면", Modifier.padding(start = 4.dp, top = 14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface)) {
            listOf(false to "태그 없이 저장", true to "가까운 구간으로 붙이기").forEachIndexed { i, (value, label) ->
                if (i > 0) HorizontalDivider(color = Color(0xFFEEEEEA))
                Row(
                    Modifier.fillMaxWidth().height(52.dp)
                        .selectable(selected = nearest == value, role = Role.RadioButton) {
                            nearest = value; repo.prefs.outsideNearest = value
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, fontSize = 15.sp, color = C.Ink, modifier = Modifier.weight(1f))
                    RadioButton(selected = nearest == value, onClick = null)
                }
            }
        }

        SectionTitle("앱 열 때", Modifier.padding(start = 4.dp, top = 14.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface)
                .padding(horizontal = 16.dp).height(56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("바로 카메라 켜기", fontSize = 15.sp, color = C.Ink, modifier = Modifier.weight(1f))
            Switch(checked = autoCamera, onCheckedChange = { autoCamera = it; repo.prefs.autoCamera = it })
        }
        if (!repo.hasEstimator) {
            Text(
                "칼로리 추정이 꺼져 있어요. API 키를 설정하면 켜져요 (README 참고).",
                fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(start = 4.dp, top = 8.dp),
            )
        }
    }

    editing?.let { slot ->
        SlotDialog(
            slot = slot,
            onDismiss = { editing = null },
            onSave = { scope.launch { repo.saveSlot(it) }; editing = null },
            onDelete = if (slot.id != 0L) ({ scope.launch { repo.deleteSlot(slot) }; editing = null }) else null,
        )
    }
}

/** 하루 24시간 막대 위에 구간을 칠해서 한눈에 보여줘요. */
@Composable
private fun DayBar(slots: List<TimeSlot>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(28.dp)
                .semantics { contentDescription = slots.joinToString(", ") { "${it.name} ${it.startMin.clock()}부터 ${it.endMin.clock()}" } },
        ) {
            drawRoundRect(Color(0xFFE6E6E1), cornerRadius = CornerRadius(8.dp.toPx()))
            fun seg(from: Int, to: Int, color: Color) {
                val x0 = size.width * from / 1440f
                val x1 = size.width * to / 1440f
                drawRect(color, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height))
            }
            slots.forEachIndexed { i, s ->
                val color = slotColors[i % slotColors.size]
                if (s.startMin <= s.endMin) seg(s.startMin, s.endMin, color)
                else { seg(s.startMin, 1440, color); seg(0, s.endMin, color) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0시", "6시", "12시", "18시", "24시").forEach { Text(it, fontSize = 11.sp, color = C.Muted) }
        }
    }
}

@Composable
private fun SlotDialog(slot: TimeSlot, onDismiss: () -> Unit, onSave: (TimeSlot) -> Unit, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(slot.name) }
    var start by remember { mutableIntStateOf(slot.startMin) }
    var end by remember { mutableIntStateOf(slot.endMin) }

    fun pick(initial: Int, set: (Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> set(h * 60 + m) }, initial / 60, initial % 60, true).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (slot.id == 0L) "구간 추가" else "구간 편집") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("태그 이름 (예: 점심)") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { pick(start) { start = it } }) { Text(start.clock()) }
                    Text("부터", color = C.Muted)
                    OutlinedButton(onClick = { pick(end) { end = it } }) { Text(end.clock()) }
                    Text("까지", color = C.Muted)
                }
                if (end < start) Text("자정을 넘기는 구간으로 저장돼요", fontSize = 12.sp, color = C.Muted)
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && start != end, onClick = {
                onSave(slot.copy(name = name.trim(), startMin = start, endMin = end))
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) TextButton(onClick = onDelete) { Text("삭제", color = Color(0xFFB3261E)) }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}
