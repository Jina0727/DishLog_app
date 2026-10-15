package app.dishlog.widget

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dishlog.DishlogApp
import app.dishlog.data.EstimateState
import app.dishlog.data.MealRepository
import app.dishlog.data.totalKcal
import app.dishlog.ui.LocalSlots
import app.dishlog.ui.Photo
import app.dishlog.ui.TagChip
import app.dishlog.ui.camera.CameraCapture
import app.dishlog.ui.currentTagLabel
import app.dishlog.ui.grouped
import app.dishlog.ui.hhmm
import app.dishlog.ui.theme.C
import app.dishlog.ui.theme.DishlogTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 위젯·앱 아이콘 바로가기에서 여는 촬영 창.
 * 앱 전체를 띄우지 않고 홈 화면 위에 아래쪽 시트로 떠요.
 * 한 장 찍으면 저장하고, 칼로리 추정 결과까지 보여준 뒤 닫아요.
 */
class QuickCaptureActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repo = (application as DishlogApp).repo

        setContent {
            DishlogTheme {
                val scope = rememberCoroutineScope()
                val slots by repo.slots.collectAsStateWithLifecycle(emptyList())
                var savedId by remember { mutableStateOf<Long?>(null) }

                CompositionLocalProvider(LocalSlots provides slots) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                            .pointerInput(Unit) { detectTapGestures { finish() } },
                    ) {
                        Box(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .then(if (savedId == null) Modifier.fillMaxHeight(0.78f) else Modifier)
                                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                                .background(if (savedId == null) C.Camera else C.Ground)
                                .pointerInput(Unit) { detectTapGestures { } } // 시트 안 탭은 닫히지 않게
                                .navigationBarsPadding(),
                        ) {
                            val id = savedId
                            if (id == null) {
                                CameraCapture(
                                    newFile = repo::newPhotoFile,
                                    singleShot = true,
                                    tagLabel = currentTagLabel(slots, repo.prefs.outsideNearest),
                                    onClose = { finish() },
                                    onDone = { files -> scope.launch { savedId = repo.createEntry(files).id } },
                                )
                            } else {
                                SavedPanel(repo, id, onClose = { finish() })
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 저장 직후 패널: 사진 · 태그 · 칼로리 결과.
 * 추정이 끝나면 2.5초 뒤 저절로 닫혀요. 키가 없으면 저장 확인만 하고 닫혀요.
 * 계산 중에 닫아도 추정은 앱이 끝까지 해서 기록에 남겨요.
 */
@Composable
private fun SavedPanel(repo: MealRepository, id: Long, onClose: () -> Unit) {
    val entry by remember(id) { repo.entry(id) }.collectAsStateWithLifecycle(null)
    val e = entry
    val done = e != null && e.estimateState != EstimateState.PENDING

    LaunchedEffect(done) {
        if (done) { delay(if (e?.estimateState == EstimateState.NONE) 1200 else 2500); onClose() }
    }

    Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Photo(e?.photos?.firstOrNull(), Modifier.size(88.dp).aspectRatio(1f).clip(RoundedCornerShape(16.dp)), "방금 찍은 사진")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TagChip(e?.tag)
                    e?.let { Text(it.takenAt.hhmm(), fontSize = 13.sp, color = C.Muted) }
                }
                when {
                    e == null -> Unit
                    e.estimateState == EstimateState.PENDING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = C.Muted)
                        Text("  칼로리 계산 중", fontSize = 15.sp, color = C.Muted)
                    }
                    e.totalKcal() != null -> Row(verticalAlignment = Alignment.Bottom) {
                        Text("약 ", fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(bottom = 3.dp))
                        Text(e.totalKcal()!!.grouped(), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                        Text(" kcal", fontSize = 13.sp, color = C.Muted, modifier = Modifier.padding(bottom = 3.dp))
                    }
                    e.estimateState == EstimateState.FAILED -> Text("칼로리를 추정하지 못했어요", fontSize = 14.sp, color = C.Muted)
                    else -> Text("저장했어요", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
                }
            }
        }
        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = C.Ink, contentColor = Color.White),
        ) { Text("닫기", fontWeight = FontWeight.SemiBold) }
    }
}
