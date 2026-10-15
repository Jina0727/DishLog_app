package app.dishlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dishlog.data.EstimateState
import app.dishlog.data.MealEntry
import app.dishlog.data.MealRepository
import app.dishlog.estimate.ClaudeCalorieEstimator
import app.dishlog.ui.grouped
import app.dishlog.ui.theme.C
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** 비교할 조합: 모델 × 보내는 사진 크기 */
private data class LabConfig(val label: String, val model: String, val maxSide: Int)

private val CONFIGS = listOf(
    LabConfig("Sonnet 5.5 · 2000px (지금 설정)", ClaudeCalorieEstimator.MODEL, 2000),
    LabConfig("Sonnet 5.5 · 1000px", ClaudeCalorieEstimator.MODEL, 1000),
    LabConfig("Haiku 5.5 · 2000px", ClaudeCalorieEstimator.MODEL_HAIKU, 2000),
    LabConfig("Haiku 5.5 · 1000px", ClaudeCalorieEstimator.MODEL_HAIKU, 1000),
)

/** 원화는 대략적인 감만 보려고 1달러 ≈ 1,400원으로 계산해요. */
private const val KRW_PER_USD = 1400

private sealed interface LabState {
    data object Running : LabState
    data class Done(val run: ClaudeCalorieEstimator.Run, val seconds: Double, val usd: Double) : LabState
    data class Failed(val message: String) : LabState
}

/**
 * 모델 비교 실험: 같은 사진(+메모)을 네 가지 조합으로 동시에 추정해서 나란히 보여줘요.
 * 실제와 가장 가까운 결과를 골라 이 기록에 저장할 수 있어요.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelLabSheet(repo: MealRepository, e: MealEntry, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val results = remember { mutableStateMapOf<Int, LabState>() }

    LaunchedEffect(Unit) {
        val key = repo.prefs.apiKey
        val files = e.photos.map(::File)
        CONFIGS.forEachIndexed { i, c ->
            results[i] = LabState.Running
            launch {
                val t0 = System.nanoTime()
                results[i] = runCatching { ClaudeCalorieEstimator(key, c.model, c.maxSide).run(files, e.hint) }
                    .fold(
                        onSuccess = {
                            LabState.Done(
                                it, (System.nanoTime() - t0) / 1e9,
                                ClaudeCalorieEstimator.costUsd(c.model, it.inputTokens, it.outputTokens),
                            )
                        },
                        onFailure = { LabState.Failed(it.message?.take(120) ?: "실패") },
                    )
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDone, sheetState = sheet, containerColor = C.Ground) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("모델 비교 (실험)", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)
            Text(
                "같은 사진" + (if (e.hint.isNotBlank()) "과 메모" else "") + "를 네 가지로 추정했어요. 실제와 가장 가까운 결과를 저장하세요.",
                fontSize = 13.sp, color = C.Body, lineHeight = 19.sp,
            )

            CONFIGS.forEachIndexed { i, c ->
                LabCard(c, results[i] ?: LabState.Running) { run ->
                    scope.launch {
                        repo.update(e.copy(items = run.items, estimateState = EstimateState.DONE))
                        sheet.hide()
                        onDone()
                    }
                }
            }

            val spent = results.values.filterIsInstance<LabState.Done>().sumOf { it.usd }
            if (spent > 0) {
                Text(
                    "이번 비교에 쓴 비용 약 ${usd(spent)} (${krw(spent)})",
                    fontSize = 12.sp, color = C.Muted, modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun LabCard(c: LabConfig, state: LabState, onPick: (ClaudeCalorieEstimator.Run) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(C.Surface).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.Ink, modifier = Modifier.weight(1f))
            if (state is LabState.Done) Text("%.1f초".format(state.seconds), fontSize = 12.sp, color = C.Muted)
        }
        when (state) {
            LabState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = C.Muted)
                Text("  계산 중", fontSize = 14.sp, color = C.Muted)
            }
            is LabState.Failed -> Text(state.message, fontSize = 13.sp, color = C.Muted)
            is LabState.Done -> {
                val total = state.run.items.sumOf { it.kcal }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(total.grouped(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                    Text(" kcal", fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(bottom = 3.dp))
                }
                state.run.items.forEach { item ->
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, fontSize = 13.sp, color = C.Body)
                            if (item.note.isNotBlank()) Text(item.note, fontSize = 11.sp, color = C.Muted)
                        }
                        Text("${item.kcal.grouped()}", fontSize = 13.sp, color = C.Body)
                    }
                }
                HorizontalDivider(color = C.Line, modifier = Modifier.padding(top = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "토큰 ${state.run.inputTokens.grouped()} + ${state.run.outputTokens.grouped()} · ${usd(state.usd)} (${krw(state.usd)})",
                        fontSize = 11.sp, color = C.Muted, modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onPick(state.run) }) { Text("이 결과로 저장", fontSize = 13.sp, color = C.Accent) }
                }
            }
        }
    }
    Spacer(Modifier.height(0.dp))
}

private fun usd(v: Double) = "$" + String.format(Locale.US, "%.4f", v)
private fun krw(v: Double): String {
    val won = v * KRW_PER_USD
    return if (won < 1) "1원 미만" else "약 ${won.roundToInt()}원"
}
