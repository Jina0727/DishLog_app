package app.dishlog.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.DinnerDining
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.Icecream
import androidx.compose.material.icons.outlined.LocalBar
import androidx.compose.material.icons.outlined.LunchDining
import androidx.compose.material.icons.outlined.RamenDining
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RiceBowl
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.dishlog.data.FULLNESS_LABELS
import app.dishlog.data.PORTION_LABELS
import app.dishlog.data.TimeSlot
import app.dishlog.ui.theme.C

/** 태그 칩 색: (배경, 글자·아이콘) */
data class TagColor(val bg: Color, val fg: Color)

val TAG_COLORS = listOf(
    TagColor(Color(0xFFE3EDE7), Color(0xFF2F5D46)), // 초록
    TagColor(Color(0xFFF6E6D8), Color(0xFF8E4419)), // 주황
    TagColor(Color(0xFFE2E8F3), Color(0xFF34548A)), // 파랑
    TagColor(Color(0xFFECE4F2), Color(0xFF654784)), // 보라
    TagColor(Color(0xFFF4ECCB), Color(0xFF6E540A)), // 노랑
    TagColor(Color(0xFFF5E1E4), Color(0xFF93384B)), // 분홍
)
val NO_TAG_COLOR = TagColor(Color(0xFFEEEEEA), Color(0xFF5E5E58))

val TAG_ICONS: Map<String, ImageVector> = linkedMapOf(
    "meal" to Icons.Outlined.Restaurant,
    "rice" to Icons.Outlined.RiceBowl,
    "lunch" to Icons.Outlined.LunchDining,
    "dinner" to Icons.Outlined.DinnerDining,
    "ramen" to Icons.Outlined.RamenDining,
    "coffee" to Icons.Outlined.Coffee,
    "bakery" to Icons.Outlined.BakeryDining,
    "icecream" to Icons.Outlined.Icecream,
    "fastfood" to Icons.Outlined.Fastfood,
    "bar" to Icons.Outlined.LocalBar,
    "sun" to Icons.Outlined.WbSunny,
    "night" to Icons.Outlined.Bedtime,
)

/** 어느 화면에서든 태그 이름으로 색·아이콘을 찾을 수 있게 구간 목록을 내려줘요. */
val LocalSlots = compositionLocalOf<List<TimeSlot>> { emptyList() }

fun TimeSlot.tagColor() = TAG_COLORS[color.mod(TAG_COLORS.size)]
fun TimeSlot.tagIcon() = TAG_ICONS[icon] ?: Icons.Outlined.Restaurant

/** 기록에 붙은 태그 칩. 태그가 구간 목록에 없으면(지운 구간 등) 회색으로. */
@Composable
fun TagChip(tag: String?, modifier: Modifier = Modifier, selected: Boolean? = null, onClick: (() -> Unit)? = null) {
    val slot = LocalSlots.current.firstOrNull { it.name == tag }
    val color = slot?.tagColor() ?: NO_TAG_COLOR
    Chip(
        label = tag ?: "태그 없음",
        bg = color.bg, fg = color.fg,
        leading = { Icon(slot?.tagIcon() ?: Icons.Outlined.Restaurant, null, tint = color.fg, modifier = Modifier.size(15.dp)) },
        selected = selected, onClick = onClick, modifier = modifier,
    )
}

/** 먹은 양 / 포만도 칩. ●●○ 점 세 개로 정도를 보여줘요. */
@Composable
fun LevelChip(kind: String, level: Int, modifier: Modifier = Modifier, selected: Boolean? = null, onClick: (() -> Unit)? = null) {
    val labels = if (kind == "portion") PORTION_LABELS else FULLNESS_LABELS
    val prefix = if (kind == "portion") "양" else "포만"
    Chip(
        label = if (selected == null) "$prefix ${labels[level]}" else labels[level],
        bg = Color(0xFFEEEEEA), fg = C.Body,
        leading = { LevelDots(level, C.Body) },
        selected = selected, onClick = onClick, modifier = modifier,
    )
}

@Composable
fun LevelDots(level: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            Box(
                Modifier.size(5.dp).clip(CircleShape)
                    .background(if (i <= level) color else color.copy(alpha = 0.25f)),
            )
        }
    }
}

/**
 * 공용 칩. selected 가 null 이면 보기 전용 칩,
 * true/false 면 고르는 칩(선택되면 진한 테두리).
 */
@Composable
fun Chip(
    label: String,
    bg: Color,
    fg: Color,
    leading: (@Composable () -> Unit)? = null,
    selected: Boolean? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val dim = selected == false
    Row(
        modifier
            .height(30.dp)
            .clip(shape)
            .background(if (dim) C.Surface else bg)
            .then(
                when (selected) {
                    true -> Modifier.border(1.5.dp, fg, shape)
                    false -> Modifier.border(1.dp, C.Border, shape)
                    null -> Modifier
                },
            )
            .then(if (selected != null) Modifier.semantics { this.selected = selected } else Modifier)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        leading?.invoke()
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = if (dim) C.Body else fg)
    }
}
