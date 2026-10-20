package app.dishlog.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface

/**
 * 사진 파일을 바로 선 방향으로 읽어요.
 * 카메라는 픽셀은 가로로 저장하고 "몇 도 돌려 보라"는 정보(EXIF)만 따로 붙이기 때문에,
 * 그 정보를 반영하지 않으면 사진이 90도 돌아가 보여요.
 *
 * @param minSide 짧은 변이 이 값 이상 남는 선에서 최대한 줄여서 읽어요 (메모리 절약).
 */
fun decodeUpright(path: String, minSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= minSide) sample *= 2
    val raw = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null

    val degrees = runCatching {
        when (ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    }.getOrDefault(0f)
    if (degrees == 0f) return raw
    val rotated = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)
    if (rotated !== raw) raw.recycle()
    return rotated
}

/**
 * 사진첩에서 고른 사진을 앱 사진 폴더로 가져와요.
 * 찍은 사진과 똑같이 바로 선 방향 + 가운데 기준 정사각형으로 맞춰 저장하고,
 * 사진에 적힌 촬영 시각(EXIF)을 함께 돌려줘요. 없으면 null.
 */
fun importSquare(context: android.content.Context, uri: android.net.Uri, dest: java.io.File, maxSide: Int = 2048): Long? {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (minOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val raw = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: throw java.io.IOException("사진을 열 수 없어요")

    val exif = runCatching { cr.openInputStream(uri)?.use { ExifInterface(it) } }.getOrNull()
    val degrees = when (exif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    val upright = if (degrees == 0f) raw else
        Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees) }, true)

    val side = minOf(upright.width, upright.height)
    var square = Bitmap.createBitmap(upright, (upright.width - side) / 2, (upright.height - side) / 2, side, side)
    if (side > maxSide) square = Bitmap.createScaledBitmap(square, maxSide, maxSide, true)
    dest.outputStream().use { square.compress(Bitmap.CompressFormat.JPEG, 92, it) }

    return exif?.let { e ->
        val s = e.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: e.getAttribute(ExifInterface.TAG_DATETIME) ?: return@let null
        runCatching {
            java.time.LocalDateTime.parse(s, java.time.format.DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss"))
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
