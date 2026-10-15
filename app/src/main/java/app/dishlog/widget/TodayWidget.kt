package app.dishlog.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.dishlog.DishlogApp
import app.dishlog.R
import app.dishlog.data.MealRepository
import app.dishlog.ui.MainActivity
import java.text.NumberFormat
import java.util.Locale

private val Ink = Color(0xFF161616)
private val Muted = Color(0xFF5E5E58)
private val Accent = Color(0xFF2F5D46)
private val Surface = Color(0xFFFFFFFF)

/**
 * 4×1 '오늘' 위젯. 왼쪽: 오늘 추정 칼로리·기록 수 (누르면 앱), 오른쪽: 찍기 버튼 (누르면 촬영 창).
 * 기록이 바뀔 때마다 앱이 갱신해 주고, 날짜가 넘어가는 건 30분 주기 갱신으로 맞춰요.
 */
class TodayWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as DishlogApp).repo
        val summary = repo.todaySummary()
        provideContent { Content(summary) }
    }

    @Composable
    private fun Content(s: MealRepository.DaySummary) {
        Row(
            modifier = GlanceModifier.fillMaxSize().cornerRadius(24.dp).background(Surface).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(start = 12.dp)
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("오늘", style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (s.kcal > 0) NumberFormat.getIntegerInstance(Locale.KOREA).format(s.kcal) else "–",
                        style = TextStyle(color = ColorProvider(Ink), fontSize = 20.sp, fontWeight = FontWeight.Bold),
                    )
                    Text(" kcal", style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp))
                }
                Text(
                    "기록 ${s.count}개" + if (s.pending) " · 계산 중" else "",
                    style = TextStyle(color = ColorProvider(Muted), fontSize = 12.sp),
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Box(
                modifier = GlanceModifier.width(72.dp).fillMaxHeight().cornerRadius(18.dp).background(Accent)
                    .clickable(actionStartActivity<QuickCaptureActivity>()),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(ImageProvider(R.drawable.ic_camera), contentDescription = "식사 바로 찍기", modifier = GlanceModifier.size(24.dp))
                    Spacer(GlanceModifier.height(2.dp))
                    Text("기록", style = TextStyle(color = ColorProvider(Color.White), fontSize = 11.sp, fontWeight = FontWeight.Medium))
                }
            }
        }
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
