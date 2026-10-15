package app.dishlog.ui.screens

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dishlog.data.EstimateState
import app.dishlog.data.FULLNESS_LABELS
import app.dishlog.data.MealEntry
import app.dishlog.data.MealRepository
import app.dishlog.data.PORTION_LABELS
import app.dishlog.data.adjustedKcal
import app.dishlog.data.totalKcal
import app.dishlog.ui.ChoiceRow
import app.dishlog.ui.Photo
import app.dishlog.ui.SectionTitle
import app.dishlog.ui.grouped
import app.dishlog.ui.hhmm
import app.dishlog.ui.tagOf
import app.dishlog.ui.theme.C
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/** 기록 카드 편집: 태그·시간·칼로리·먹은 양·포만도·메모. 바꾸는 즉시 저장돼요. */
@Composable
fun EntryScreen(repo: MealRepository, id: Long, onBack: () -> Unit) {
    val entry by repo.entry(id).collectAsStateWithLifecycle(null)
    val e = entry ?: return
    EntryContent(repo, e, onBack)
}

@Composable
private fun EntryContent(repo: MealRepository, e: MealEntry, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val slots by repo.slots.collectAsStateWithLifecycle(emptyList())
    var tagMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var note by remember(e.id) { mutableStateOf(e.note) }

    fun save(next: MealEntry) = scope.launch { repo.update(next) }

    LaunchedEffect(note) {
        if (note != e.note) { delay(500); repo.updateNote(e.id, note) }
    }

    Column(Modifier.fillMaxSize().background(C.Ground)) {
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            val pager = rememberPagerState { e.photos.size.coerceAtLeast(1) }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                Photo(e.photos.getOrNull(page), Modifier.fillMaxSize(), "${tagOf(e)} 사진 ${page + 1}")
            }
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(12.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.92f)),
            ) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로", tint = C.Ink) }
            IconButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.92f)),
            ) { Icon(Icons.Outlined.Delete, contentDescription = "기록 삭제", tint = C.Ink) }
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
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            // 태그 · 시간
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    Chip("${tagOf(e)}", trailing = true) { tagMenu = true }
                    DropdownMenu(expanded = tagMenu, onDismissRequest = { tagMenu = false }) {
                        slots.forEach { s ->
                            DropdownMenuItem(text = { Text(s.name) }, onClick = { tagMenu = false; save(e.copy(tag = s.name)) })
                        }
                        DropdownMenuItem(text = { Text("태그 없음", color = C.Muted) }, onClick = { tagMenu = false; save(e.copy(tag = null)) })
                    }
                }
                Chip(e.takenAt.hhmm(), trailing = true) {
                    val t = Instant.ofEpochMilli(e.takenAt).atZone(ZoneId.systemDefault())
                    TimePickerDialog(context, { _, h, m ->
                        val next = t.withHour(h).withMinute(m).toInstant().toEpochMilli()
                        scope.launch { repo.setTime(e, next) }
                    }, t.hour, t.minute, true).show()
                }
            }

            // 칼로리
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    val total = e.totalKcal()
                    when {
                        e.estimateState == EstimateState.PENDING ->
                            Text("칼로리 계산 중…", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.Muted)
                        total != null -> {
                            Text("약 ", fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(bottom = 6.dp))
                            Text(total.grouped(), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                            Text(" kcal", fontSize = 14.sp, color = C.Muted, modifier = Modifier.padding(bottom = 6.dp))
                        }
                        else -> Text(
                            if (e.estimateState == EstimateState.FAILED) "칼로리를 추정하지 못했어요" else "칼로리 정보 없음",
                            fontSize = 16.sp, color = C.Muted,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (repo.hasEstimator && e.estimateState != EstimateState.PENDING) {
                        TextButton(onClick = { repo.estimate(e.id) }) {
                            Text(if (e.items.isEmpty()) "추정하기" else "다시 추정", color = C.Accent)
                        }
                    }
                }
                if (e.items.isNotEmpty()) {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(horizontal = 16.dp)) {
                        e.items.forEachIndexed { i, item ->
                            if (i > 0) HorizontalDivider(color = Color(0xFFEEEEEA))
                            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(item.name, fontSize = 15.sp, color = C.Ink, modifier = Modifier.weight(1f))
                                Text("${item.adjustedKcal(e.portion).grouped()} kcal", fontSize = 14.sp, color = C.Body)
                            }
                        }
                    }
                    Text("사진으로 추정한 값이에요", fontSize = 12.sp, color = C.Muted)
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("먹은 양")
                ChoiceRow(PORTION_LABELS, e.portion, C.Ink) { save(e.copy(portion = it)) }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("포만도")
                ChoiceRow(FULLNESS_LABELS, e.fullness, C.Accent) { save(e.copy(fullness = if (e.fullness == it) null else it)) }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("메모") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
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

@Composable
private fun Chip(text: String, trailing: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.height(36.dp).clip(shape).background(C.Surface).border(1.dp, C.Border, shape)
            .clickable(onClick = onClick).padding(start = 14.dp, end = if (trailing) 8.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = C.Ink)
        if (trailing) Icon(Icons.Outlined.ArrowDropDown, contentDescription = null, tint = C.Body)
    }
}
