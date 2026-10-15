package app.dishlog.widget

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dishlog.DishlogApp
import app.dishlog.ui.camera.CameraCapture
import app.dishlog.ui.currentTagLabel
import app.dishlog.ui.theme.C
import app.dishlog.ui.theme.DishlogTheme
import kotlinx.coroutines.launch

/**
 * 위젯·앱 아이콘 바로가기에서 여는 촬영 창.
 * 앱 전체를 띄우지 않고 홈 화면 위에 아래쪽 시트로 떠요.
 * 한 장 찍으면 태그를 붙여 저장하고 바로 닫혀요. 수정은 나중에 앱에서.
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
                            .fillMaxHeight(0.78f)
                            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                            .background(C.Camera)
                            .pointerInput(Unit) { detectTapGestures { } } // 시트 안 탭은 닫히지 않게
                            .navigationBarsPadding(),
                    ) {
                        CameraCapture(
                            newFile = repo::newPhotoFile,
                            singleShot = true,
                            tagLabel = currentTagLabel(slots, repo.prefs.outsideNearest),
                            onClose = { finish() },
                            onDone = { files ->
                                scope.launch {
                                    val e = repo.createEntry(files)
                                    Toast.makeText(this@QuickCaptureActivity, "저장했어요 · ${e.tag ?: "태그 없음"}", Toast.LENGTH_SHORT).show()
                                    finish()
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}
