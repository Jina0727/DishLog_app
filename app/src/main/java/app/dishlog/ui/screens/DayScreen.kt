package app.dishlog.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dishlog.data.MealRepository
import app.dishlog.data.totalKcal
import app.dishlog.ui.Photo
import app.dishlog.ui.grouped
import app.dishlog.ui.hhmm
import app.dishlog.ui.koreanLong
import app.dishlog.ui.tagOf
import app.dishlog.ui.theme.C
import java.time.LocalDate

/** 지난 날짜 화면. 기본은 사진만 크게 모아보기, 토글로 카드 목록. */
@Composable
fun DayScreen(repo: MealRepository, epochDay: Long, onBack: () -> Unit, onOpenEntry: (Long) -> Unit) {
    val date = LocalDate.ofEpochDay(epochDay)
    val entries by repo.day(epochDay).collectAsStateWithLifecycle(emptyList())
    var mode by rememberSaveable { mutableIntStateOf(0) }
    val total = entries.mapNotNull { it.totalKcal() }.sum()

    Column(Modifier.fillMaxSize().background(C.Ground)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, end = 20.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "뒤로") }
            Column(Modifier.weight(1f)) {
                Text(date.koreanLong(), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = C.Ink)
                Text(
                    "기록 ${entries.size}개" + if (total > 0) " · 약 ${total.grouped()} kcal" else "",
                    fontSize = 13.sp, color = C.Muted,
                )
            }
            SingleChoiceSegmentedButtonRow {
                listOf("사진", "기록").forEachIndexed { i, label ->
                    SegmentedButton(
                        selected = mode == i,
                        onClick = { mode = i },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        icon = {},
                    ) { Text(label, fontSize = 13.sp) }
                }
            }
        }

        if (entries.isEmpty()) {
            Text("이 날은 기록이 없어요", color = C.Muted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        } else if (mode == 0) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(entries, key = { it.id }) { e ->
                    Box(
                        Modifier.aspectRatio(1f).clip(RoundedCornerShape(12.dp)).clickable { onOpenEntry(e.id) },
                    ) {
                        Photo(e.photos.firstOrNull(), Modifier.fillMaxSize(), "${tagOf(e)} ${e.takenAt.hhmm()} 사진")
                        Text(
                            "${tagOf(e)} ${e.takenAt.hhmm()}",
                            color = Color.White, fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp)
                                .clip(RoundedCornerShape(12.dp)).background(C.Ink.copy(alpha = 0.72f))
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                        if (e.photos.size > 1) {
                            Text(
                                "${e.photos.size}장",
                                color = C.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)
                                    .clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.92f))
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { it.id }) { e -> ActionableEntryCard(repo, e) { onOpenEntry(e.id) } }
            }
        }
    }
}
