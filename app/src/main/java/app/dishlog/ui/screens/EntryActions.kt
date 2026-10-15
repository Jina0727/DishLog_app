package app.dishlog.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.dishlog.data.MealEntry
import app.dishlog.data.MealRepository
import app.dishlog.ui.EntryCard

/** 목록 카드 + 길게 누르면 뜨는 수정/삭제 메뉴. */
@Composable
fun ActionableEntryCard(repo: MealRepository, e: MealEntry, onOpen: () -> Unit) {
    val haptic = LocalHapticFeedback.current
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box {
        EntryCard(
            e,
            onClick = onOpen,
            onLongClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menu = true
            },
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, offset = DpOffset(120.dp, (-40).dp)) {
            DropdownMenuItem(
                text = { Text("수정") },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = { menu = false; editing = true },
            )
            DropdownMenuItem(
                text = { Text("삭제", color = Color(0xFFB3261E)) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = Color(0xFFB3261E)) },
                onClick = { menu = false; confirmDelete = true },
            )
        }
    }

    if (editing) EditSheet(repo, e, onDone = { editing = false })
    if (confirmDelete) {
        DeleteEntryDialog(
            onConfirm = { confirmDelete = false; repo.delete(e) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
fun DeleteEntryDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 기록을 지울까요?") },
        text = { Text("사진도 함께 지워져요.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("지우기", color = Color(0xFFB3261E)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
