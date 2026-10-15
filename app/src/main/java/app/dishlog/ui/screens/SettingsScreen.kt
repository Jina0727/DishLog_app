package app.dishlog.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dishlog.data.MealRepository
import app.dishlog.data.TimeSlot
import app.dishlog.ui.LocalSlots
import app.dishlog.ui.SectionTitle
import app.dishlog.ui.TAG_COLORS
import app.dishlog.ui.TAG_ICONS
import app.dishlog.ui.TagChip
import app.dishlog.ui.TimeWheel
import app.dishlog.ui.clock
import app.dishlog.ui.tagColor
import app.dishlog.ui.theme.C
import kotlinx.coroutines.launch

/** 식사 구간 설정 + 기본 동작 설정. */
@Composable
fun SettingsScreen(repo: MealRepository, onBack: () -> Unit) {
    val slots = LocalSlots.current
    var editing by remember { mutableStateOf<TimeSlot?>(null) }
    var nearest by remember { mutableStateOf(repo.prefs.outsideNearest) }
    var autoCamera by remember { mutableStateOf(repo.prefs.autoCamera) }

    Column(
        Modifier.fillMaxSize().background(C.Ground).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).padding(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
            Text("설정", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = C.Ink)
        }

        SectionTitle("식사 구간", Modifier.padding(start = 4.dp, top = 8.dp))
        Text("이 시간에 찍은 사진엔 해당 태그가 자동으로 붙어요.", fontSize = 13.sp, color = C.Body, modifier = Modifier.padding(start = 4.dp))

        DayBar(slots)

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface)) {
            slots.forEachIndexed { i, s ->
                if (i > 0) HorizontalDivider(color = Color(0xFFEEEEEA))
                Row(
                    Modifier.fillMaxWidth().height(60.dp).clickable { editing = s }.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TagChip(s.name)
                    Spacer(Modifier.weight(1f))
                    Text("${s.startMin.clock()} – ${s.endMin.clock()}", fontSize = 15.sp, color = C.Ink)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = C.Muted)
                }
            }
            if (slots.isEmpty()) {
                Text("구간이 없어요. 찍은 사진은 태그 없이 저장돼요.", fontSize = 14.sp, color = C.Muted, modifier = Modifier.padding(16.dp))
            }
        }

        OutlinedButton(
            onClick = { editing = TimeSlot(name = "", startMin = 12 * 60, endMin = 13 * 60, color = slots.size % TAG_COLORS.size) },
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

    editing?.let { slot -> SlotSheet(repo, slot, onDone = { editing = null }) }
}

/** 하루 24시간 막대 위에 구간을 칠해서 한눈에 보여줘요. */
@Composable
private fun DayBar(slots: List<TimeSlot>) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(
            Modifier.fillMaxWidth().height(24.dp)
                .semantics { contentDescription = slots.joinToString(", ") { "${it.name} ${it.startMin.clock()}부터 ${it.endMin.clock()}" } },
        ) {
            drawRoundRect(Color(0xFFE6E6E1), cornerRadius = CornerRadius(8.dp.toPx()))
            fun seg(from: Int, to: Int, color: Color) {
                val x0 = size.width * from / 1440f
                val x1 = size.width * to / 1440f
                drawRect(color, topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height))
            }
            slots.forEach { s ->
                val color = s.tagColor().fg
                if (s.startMin <= s.endMin) seg(s.startMin, s.endMin, color)
                else { seg(s.startMin, 1440, color); seg(0, s.endMin, color) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("0시", "6시", "12시", "18시", "24시").forEach { Text(it, fontSize = 11.sp, color = C.Muted) }
        }
    }
}

/** 구간 추가·편집 시트: 이름, 색, 아이콘, 시작/끝 시간(휠). */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SlotSheet(repo: MealRepository, slot: TimeSlot, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(slot.name) }
    var color by remember { mutableIntStateOf(slot.color) }
    var icon by remember { mutableStateOf(slot.icon) }
    var start by remember { mutableIntStateOf(slot.startMin / 5 * 5) }
    var end by remember { mutableIntStateOf(slot.endMin / 5 * 5) }

    fun close() = scope.launch { sheet.hide(); onDone() }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet, containerColor = C.Ground) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(if (slot.id == 0L) "구간 추가" else "구간 편집", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)

            OutlinedTextField(name, { name = it }, label = { Text("태그 이름 (예: 점심)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("시간")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("시작", fontSize = 12.sp, color = C.Muted)
                        TimeWheel(start, { start = it }, step = 5, label = "시작")
                    }
                    Text("–", fontSize = 20.sp, color = C.Muted, modifier = Modifier.padding(top = 16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("끝", fontSize = 12.sp, color = C.Muted)
                        TimeWheel(end, { end = it }, step = 5, label = "끝")
                    }
                }
                Text(
                    if (end < start) "자정을 넘기는 구간이에요 (${start.clock()} → 다음 날 ${end.clock()})"
                    else "${start.clock()}부터 ${end.clock()}까지",
                    fontSize = 13.sp, color = C.Body,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("색")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TAG_COLORS.forEachIndexed { i, c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(c.bg)
                                .border(if (color == i) 2.5.dp else 1.dp, if (color == i) c.fg else C.Border, CircleShape)
                                .semantics { selected = color == i; contentDescription = "색 ${i + 1}" }
                                .clickable { color = i },
                            contentAlignment = Alignment.Center,
                        ) { Box(Modifier.size(12.dp).clip(CircleShape).background(c.fg)) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("아이콘")
                val c = TAG_COLORS[color]
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    TAG_ICONS.forEach { (key, vector) ->
                        val on = icon == key
                        Box(
                            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (on) c.bg else C.Surface)
                                .border(if (on) 2.dp else 1.dp, if (on) c.fg else C.Border, RoundedCornerShape(12.dp))
                                .semantics { selected = on }
                                .clickable { icon = key },
                            contentAlignment = Alignment.Center,
                        ) { Icon(vector, contentDescription = key, tint = if (on) c.fg else C.Body) }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (slot.id != 0L) {
                    TextButton(onClick = { scope.launch { repo.deleteSlot(slot); sheet.hide(); onDone() } }) {
                        Text("삭제", color = Color(0xFFB3261E))
                    }
                }
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = { close() }, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, C.Border)) {
                    Text("취소", color = C.Body)
                }
                Button(
                    enabled = name.isNotBlank() && start != end,
                    onClick = {
                        scope.launch {
                            repo.saveSlot(slot.copy(name = name.trim(), startMin = start, endMin = end, color = color, icon = icon))
                            sheet.hide(); onDone()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Ink, contentColor = Color.White),
                ) { Text("저장", fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
