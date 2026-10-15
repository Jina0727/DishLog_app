package app.dishlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dishlog.ui.theme.C

private val ItemH = 40.dp

/** 위아래로 굴려서 고르는 휠. 가운데 줄에 걸린 값이 선택돼요. */
@Composable
fun Wheel(values: List<String>, initial: Int, onChange: (Int) -> Unit, label: String, modifier: Modifier = Modifier) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial.coerceIn(values.indices))
    val fling = rememberSnapFlingBehavior(state)
    val half = with(LocalDensity.current) { ItemH.toPx() / 2 }
    val center by remember {
        derivedStateOf {
            (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > half) 1 else 0)
                .coerceIn(values.indices)
        }
    }
    LaunchedEffect(center) { onChange(center) }

    Box(modifier.height(ItemH * 3).semantics { contentDescription = "$label ${values[center]}" }) {
        Box(
            Modifier.align(Alignment.Center).fillMaxWidth().height(ItemH)
                .clip(RoundedCornerShape(10.dp)).background(C.Line),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = ItemH),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(values.size) { i ->
                Box(Modifier.height(ItemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        values[i],
                        fontSize = if (i == center) 20.sp else 16.sp,
                        fontWeight = if (i == center) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (i == center) C.Ink else C.Faint,
                    )
                }
            }
        }
    }
}

private val HOURS = List(24) { "%02d".format(it) }
private val MIN_1 = List(60) { "%02d".format(it) }
private val MIN_5 = List(12) { "%02d".format(it * 5) }

/** 시 : 분 휠 한 쌍. 분 단위 값(0~1439)을 주고받아요. step=5 면 5분 단위. */
@Composable
fun TimeWheel(minuteOfDay: Int, onChange: (Int) -> Unit, step: Int = 1, label: String = "시간", modifier: Modifier = Modifier) {
    val mins = if (step == 5) MIN_5 else MIN_1
    var h = minuteOfDay / 60
    var m = (minuteOfDay % 60) / step
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Wheel(HOURS, h, { h = it; onChange(h * 60 + m * step) }, "$label 시", Modifier.width(56.dp))
        Text(":", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = C.Ink)
        Wheel(mins, m, { m = it; onChange(h * 60 + m * step) }, "$label 분", Modifier.width(56.dp))
    }
}
