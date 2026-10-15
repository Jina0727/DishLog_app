package app.dishlog.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dishlog.data.EstimateState
import app.dishlog.data.MealEntry
import app.dishlog.data.MealRepository
import app.dishlog.data.minuteOfDay
import app.dishlog.data.totalKcal
import app.dishlog.ui.EntryChips
import app.dishlog.ui.FullnessChip
import app.dishlog.ui.LocalSlots
import app.dishlog.ui.Photo
import app.dishlog.ui.SectionTitle
import app.dishlog.ui.TagChip
import app.dishlog.ui.TimeWheel
import app.dishlog.ui.grouped
import app.dishlog.ui.hhmm
import app.dishlog.ui.tagOf
import app.dishlog.ui.theme.C
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN)

/** 기록 상세: 보기 전용으로 깔끔하게. 수정 버튼을 누르면 시트에서 고쳐요. */
@Composable
fun EntryScreen(repo: MealRepository, id: Long, onBack: () -> Unit) {
    val entry by repo.entry(id).collectAsStateWithLifecycle(null)
    val e = entry ?: return
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var askHint by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(C.Ground)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                val pager = rememberPagerState { e.photos.size.coerceAtLeast(1) }
                HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                    Photo(e.photos.getOrNull(page), Modifier.fillMaxSize(), "${tagOf(e)} 사진 ${page + 1}")
                }
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.statusBarsPadding().padding(12.dp),
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.92f)),
                ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로", tint = C.Ink) }
                if (e.photos.size > 1) {
                    Text(
                        "${pager.currentPage + 1} / ${e.photos.size}",
                        color = Color.White, fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)
                            .clip(RoundedCornerShape(12.dp)).background(C.Ink.copy(alpha = 0.7f))
                            .padding(horizontal = 9.dp, vertical = 3.dp),
                    )
                }
            }
        Column(
            Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            // 시간 + 칩
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(e.takenAt.hhmm(), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    Text(
                        dateFmt.format(LocalDate.ofEpochDay(e.epochDay)),
                        fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(bottom = 5.dp),
                    )
                }
                EntryChips(e)
            }

            // 추정 칼로리
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionTitle("추정 칼로리", Modifier.weight(1f))
                    if (repo.hasEstimator && e.estimateState != EstimateState.PENDING) {
                        TextButton(onClick = { askHint = true }) {
                            Text(if (e.items.isEmpty()) "추정하기" else "다시 추정", fontSize = 13.sp, color = C.Accent)
                        }
                    }
                }
                val total = e.totalKcal()
                when {
                    e.estimateState == EstimateState.PENDING -> Text("계산 중…", fontSize = 15.sp, color = C.Muted)
                    total != null -> {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("약 ", fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(bottom = 4.dp))
                            Text(total.grouped(), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                            Text(" kcal", fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(bottom = 4.dp))
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            e.items.forEach { item ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.name, fontSize = 14.sp, color = C.Ink)
                                        if (item.note.isNotBlank()) Text(item.note, fontSize = 12.sp, color = C.Muted)
                                    }
                                    Text("${item.kcal.grouped()} kcal", fontSize = 14.sp, color = C.Body)
                                }
                            }
                        }
                    }
                    else -> Text(
                        when {
                            e.estimateState == EstimateState.FAILED -> "추정하지 못했어요"
                            !repo.hasEstimator -> "설정에서 Claude API 키를 넣으면 켜져요"
                            else -> "정보 없음"
                        },
                        fontSize = 14.sp, color = C.Muted,
                    )
                }
                if (e.hint.isNotBlank()) {
                    Text("“${e.hint}” 반영", fontSize = 12.sp, color = C.Muted)
                }
            }

            // 메모
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionTitle("메모")
                Text(
                    e.note.ifBlank { "메모 없음" },
                    fontSize = 15.sp, lineHeight = 23.sp,
                    color = if (e.note.isBlank()) C.Faint else C.Body,
                )
            }
        }

        }

        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.height(52.dp),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, C.Border),
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null, tint = C.Body, modifier = Modifier.size(18.dp))
                Text(" 삭제", color = C.Body)
            }
            Button(
                onClick = { editing = true },
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = C.Ink, contentColor = Color.White),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" 수정", fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (editing) EditSheet(repo, e, onDone = { editing = false })

    if (askHint) {
        HintDialog(
            initial = e.hint,
            onDismiss = { askHint = false },
            onEstimate = { hint -> askHint = false; repo.estimate(e.id, hint) },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("이 기록을 지울까요?") },
            text = { Text("사진도 함께 지워져요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    repo.delete(e)
                    onBack()
                }) { Text("지우기") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } },
        )
    }
}

/** 수정 시트: 태그 · 시간 · 먹은 양 · 포만도 · 메모. 저장을 눌러야 반영돼요. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun EditSheet(repo: MealRepository, e: MealEntry, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val slots = LocalSlots.current
    var tag by remember { mutableStateOf(e.tag) }
    var minute by remember { mutableIntStateOf(minuteOfDay(e.takenAt)) }
    var fullness by remember { mutableStateOf(e.fullness) }
    var note by remember { mutableStateOf(e.note) }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet, containerColor = C.Ground) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text("기록 수정", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("태그")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    slots.forEach { s -> TagChip(s.name, selected = tag == s.name, onClick = { tag = s.name }) }
                    TagChip(null, selected = tag == null, onClick = { tag = null })
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("시간")
                TimeWheel(minute, { minute = it }, label = "먹은 시간")
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("포만도")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0..2).forEach { i ->
                        FullnessChip(i, selected = fullness == i, onClick = { fullness = if (fullness == i) null else i })
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("메모") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )

            HorizontalDivider(color = C.Line)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = { scope.launch { sheet.hide(); onDone() } },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, C.Border),
                ) { Text("취소", color = C.Body) }
                Button(
                    onClick = {
                        val zoned = Instant.ofEpochMilli(e.takenAt).atZone(ZoneId.systemDefault())
                        val takenAt = zoned.withHour(minute / 60).withMinute(minute % 60).toInstant().toEpochMilli()
                        scope.launch {
                            repo.update(e.copy(tag = tag, takenAt = takenAt, fullness = fullness, note = note))
                            sheet.hide()
                            onDone()
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = C.Ink, contentColor = Color.White),
                ) { Text("저장", fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 추정 전에 Claude 에게 덧붙일 말. 비워도 돼요. */
@Composable
private fun HintDialog(initial: String, onDismiss: () -> Unit, onEstimate: (String) -> Unit) {
    var hint by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("칼로리 추정") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "남긴 음식이나 사진에 안 보이는 것을 적어주면 더 정확해져요.",
                    fontSize = 13.sp, color = C.Body, lineHeight = 19.sp,
                )
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    placeholder = { Text("예: 밥은 반 공기만 먹음, 라떼 한 잔 추가") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onEstimate(hint) }) { Text("추정하기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
