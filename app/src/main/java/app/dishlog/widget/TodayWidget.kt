package app.dishlog.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.compose.ui.unit.DpSize
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.dishlog.DishlogApp
import app.dishlog.R
import app.dishlog.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF161616)
private val Muted = Color(0xFF5E5E58)
private val Accent = Color(0xFF2F5D46)
private val Surface = Color(0xFFFFFFFF)
private val Slot = Color(0xFFEEEEEA)

private const val MAX_THUMBS = 4
private const val THUMB_PX = 144

/** 위젯 폭별 사진 칸 수: 3×1 → 2장, 4×1 → 3장, 5×1 → 4장 */
private val NARROW = DpSize(180.dp, 56.dp)
private val MEDIUM = DpSize(250.dp, 56.dp)
private val WIDE = DpSize(320.dp, 56.dp)

/**
 * Dishlog 위젯 (3×1 ~ 5×1, 크기 조절 가능).
 * [오늘 사진 썸네일 · 빈 칸] ····· [칼로리] [둥근 찍기 버튼]
 * 왼쪽을 누르면 앱, 오른쪽 버튼을 누르면 촬영 창이 열려요.
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(NARROW, MEDIUM, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as DishlogApp).repo
        val s = repo.todaySummary()
        // 최근 사진만, 위젯 용량을 아끼려고 작은 정사각형으로 잘라서 넣어요.
        val recent = withContext(Dispatchers.IO) { s.photos.takeLast(MAX_THUMBS).mapNotNull(::thumbnail) }
        provideContent {
            val slots = when {
                LocalSize.current.width >= WIDE.width -> 4
                LocalSize.current.width >= MEDIUM.width -> 3
                else -> 2
            }
            Content(recent.takeLast(slots), slots, (s.photos.size - slots).coerceAtLeast(0), s.kcal, s.pending)
        }
    }

    @Composable
    private fun Content(thumbs: List<Bitmap>, slots: Int, extra: Int, kcal: Int, pending: Boolean) {
        Row(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(26.dp).background(Surface).padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight().clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until slots) {
                    val bmp = thumbs.getOrNull(i)
                    if (bmp != null) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Image(
                                provider = ImageProvider(bmp),
                                contentDescription = "오늘 기록 사진 ${i + 1}",
                                contentScale = ContentScale.Crop,
                                modifier = GlanceModifier.size(48.dp).cornerRadius(12.dp),
                            )
                            if (i == 0 && extra > 0) { // 칸보다 많으면 첫 칸에 "+N"
                                Box(
                                    modifier = GlanceModifier.size(48.dp).cornerRadius(12.dp).background(Color(0x99161616)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text("+$extra", style = TextStyle(color = ColorProvider(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                                }
                            }
                        }
                    } else {
                        // 빈 칸: 아직 안 찍은 자리
                        Box(modifier = GlanceModifier.size(48.dp).cornerRadius(12.dp).background(Slot)) {}
                    }
                    if (i < slots - 1) Spacer(GlanceModifier.width(6.dp))
                }
                Spacer(GlanceModifier.defaultWeight())
                Column(horizontalAlignment = Alignment.End, modifier = GlanceModifier.padding(end = 10.dp)) {
                    Text(
                        when {
                            pending -> "계산 중"
                            kcal > 0 -> NumberFormat.getIntegerInstance(Locale.KOREA).format(kcal)
                            else -> "–"
                        },
                        style = TextStyle(
                            color = ColorProvider(if (pending) Muted else Ink),
                            fontSize = if (pending) 13.sp else 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                        ),
                    )
                    Text("kcal", style = TextStyle(color = ColorProvider(Muted), fontSize = 11.sp, textAlign = TextAlign.End))
                }
            }
            Box(
                modifier = GlanceModifier.size(52.dp).cornerRadius(26.dp).background(Accent)
                    .clickable(actionStartActivity<QuickCaptureActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Image(ImageProvider(R.drawable.ic_camera), contentDescription = "식사 바로 찍기", modifier = GlanceModifier.size(24.dp))
            }
        }
    }
}

/** 사진 파일을 가운데 기준 정사각형 썸네일로. */
private fun thumbnail(path: String): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= THUMB_PX) sample *= 2
    val src = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
    val side = minOf(src.width, src.height)
    val square = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
    Bitmap.createScaledBitmap(square, THUMB_PX, THUMB_PX, true)
}.getOrNull()

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
