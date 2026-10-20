package app.dishlog.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Rational
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dishlog.ui.Photo
import app.dishlog.ui.theme.C
import java.io.File

/**
 * 촬영 화면 공용 컴포넌트.
 * - singleShot = true  : 셔터 한 번에 바로 onDone (위젯 촬영 창)
 * - singleShot = false : 여러 장 찍고 '완료'로 한 끼에 묶음 (앱 안 촬영)
 */
@Composable
fun CameraCapture(
    newFile: () -> File,
    singleShot: Boolean,
    tagLabel: String,
    onClose: () -> Unit,
    onDone: (List<File>) -> Unit,
    /** 사진첩에서 고른 사진들 (+ 이미 찍어둔 사진). null 이면 앨범 버튼을 숨겨요. */
    onImport: ((uris: List<Uri>, shots: List<File>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA) }

    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
    }
    val shots = remember { mutableStateListOf<File>() }
    // 시스템 사진 선택기: 권한 없이 사진첩을 열어요. 한 끼 최대 10장.
    val pickMany = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { uris ->
        if (uris.isNotEmpty()) onImport?.invoke(uris, shots.toList())
    }
    val pickOne = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onImport?.invoke(listOf(uri), emptyList())
    }
    fun openAlbum() {
        val req = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        if (singleShot) pickOne.launch(req) else pickMany.launch(req)
    }
    var busy by remember { mutableStateOf(false) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) { onDispose { provider?.unbindAll() } }

    fun close() {
        shots.forEach { it.delete() }   // 완료하지 않고 닫으면 찍은 사진은 버려요
        onClose()
    }

    fun shoot() {
        if (busy || !hasPermission) return
        busy = true
        val file = newFile()
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    busy = false
                    if (singleShot) onDone(listOf(file)) else shots.add(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    Toast.makeText(context, "촬영에 실패했어요", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }

    Column(modifier.fillMaxSize().background(C.Camera)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = ::close,
                colors = IconButtonDefaults.iconButtonColors(containerColor = Color.White.copy(alpha = 0.12f)),
            ) { Icon(Icons.Outlined.Close, contentDescription = "닫기", tint = Color.White) }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 14.dp, vertical = 8.dp),
            ) { Text(tagLabel, color = C.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }

        // 미리보기와 저장되는 사진 모두 정사각형(1:1). 남는 공간 안에서 가장 크게 맞춰요.
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF2A2925)),
            contentAlignment = Alignment.Center,
        ) {
            if (hasPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PreviewView(ctx).also { view ->
                            view.scaleType = PreviewView.ScaleType.FILL_CENTER
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                val p = future.get()
                                val rotation = ContextCompat.getDisplayOrDefault(ctx).rotation
                                val preview = Preview.Builder().build().also { it.setSurfaceProvider(view.surfaceProvider) }
                                imageCapture.targetRotation = rotation
                                // 뷰포트를 1:1로 고정하면 저장되는 JPEG도 같은 정사각형으로 잘려요.
                                val group = UseCaseGroup.Builder()
                                    .setViewPort(ViewPort.Builder(Rational(1, 1), rotation).build())
                                    .addUseCase(preview)
                                    .addUseCase(imageCapture)
                                    .build()
                                p.unbindAll()
                                p.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, group)
                                provider = p
                            }, ContextCompat.getMainExecutor(ctx))
                        }
                    },
                )
                Text(
                    "상 전체가 들어오게 위에서 찍어주세요",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("카메라 권한이 필요해요", color = Color.White, fontSize = 15.sp)
                    TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                        Text("권한 허용하기", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        }

        if (!singleShot) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp).height(44.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                shots.lastOrNull()?.let {
                    Photo(it.absolutePath, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).border(1.5.dp, Color.White, RoundedCornerShape(10.dp)), "방금 찍은 사진")
                }
                Text(
                    if (shots.isEmpty()) "반찬이 따로면 여러 장 찍어도 한 끼로 묶여요" else "${shots.size}장 · 더 찍거나 완료를 눌러요",
                    color = Color(0xFFD4D4CD),
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                if (shots.isNotEmpty()) {
                    TextButton(
                        onClick = { onDone(shots.toList()) },
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(0.dp, Color.Transparent),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(containerColor = Color.White, contentColor = C.Ink),
                    ) { Text("완료", fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
            if (onImport != null) {
                Box(
                    Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 44.dp)
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .semantics { contentDescription = "사진첩에서 고르기" }
                        .clickable(role = Role.Button, onClick = ::openAlbum),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Color.White) }
            }
            Box(
                Modifier
                    .size(80.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(9.dp)
                    .clip(CircleShape)
                    .background(if (busy) Color.White.copy(alpha = 0.5f) else Color.White)
                    .semantics { contentDescription = "촬영" }
                    .clickable(role = Role.Button, onClick = ::shoot),
            )
        }
    }
}
