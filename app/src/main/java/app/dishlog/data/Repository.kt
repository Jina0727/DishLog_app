package app.dishlog.data

import android.content.Context
import app.dishlog.estimate.CalorieEstimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import kotlin.math.abs

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("dishlog", Context.MODE_PRIVATE)

    /** 구간 밖에서 찍었을 때 가장 가까운 구간 태그를 붙일지 (false 면 태그 없음). */
    var outsideNearest: Boolean
        get() = sp.getBoolean("outside_nearest", false)
        set(v) = sp.edit().putBoolean("outside_nearest", v).apply()

    /** 앱을 열면 바로 카메라부터 띄울지 (밀로그 방식). */
    var autoCamera: Boolean
        get() = sp.getBoolean("auto_camera", true)
        set(v) = sp.edit().putBoolean("auto_camera", v).apply()
}

object TagResolver {
    fun resolve(minuteOfDay: Int, slots: List<TimeSlot>, nearest: Boolean): String? {
        slots.firstOrNull { it.contains(minuteOfDay) }?.let { return it.name }
        if (!nearest || slots.isEmpty()) return null
        return slots.minBy { it.distanceTo(minuteOfDay) }.name
    }
}

fun TimeSlot.contains(m: Int): Boolean =
    if (startMin <= endMin) m in startMin until endMin else (m >= startMin || m < endMin)

fun TimeSlot.distanceTo(m: Int): Int {
    fun d(a: Int, b: Int) = abs(a - b).let { minOf(it, 1440 - it) }
    return minOf(d(m, startMin), d(m, endMin))
}

fun minuteOfDay(epochMillis: Long): Int {
    val t = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    return t.hour * 60 + t.minute
}

class MealRepository(
    context: Context,
    private val db: AppDatabase,
    val prefs: Prefs,
    private val estimator: CalorieEstimator?,
    private val scope: CoroutineScope,
) {
    private val photoDir = File(context.filesDir, "photos").apply { mkdirs() }

    val hasEstimator: Boolean get() = estimator != null
    val slots: Flow<List<TimeSlot>> = db.slots().observeAll()

    fun newPhotoFile(): File = File(photoDir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")

    fun day(epochDay: Long): Flow<List<MealEntry>> = db.meals().observeDay(epochDay)
    fun daysWithEntries(from: Long, to: Long): Flow<List<Long>> = db.meals().observeDaysWithEntries(from, to)
    fun entry(id: Long): Flow<MealEntry?> = db.meals().observe(id)

    /** 찍은 사진들로 기록 하나를 만들고, 찍은 시간에 맞는 구간 태그를 자동으로 붙여요. */
    suspend fun createEntry(photos: List<File>, takenAt: Long = System.currentTimeMillis()): MealEntry {
        val zoned = Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault())
        val tag = TagResolver.resolve(minuteOfDay(takenAt), db.slots().getAll(), prefs.outsideNearest)
        val entry = MealEntry(
            takenAt = takenAt,
            epochDay = zoned.toLocalDate().toEpochDay(),
            tag = tag,
            photos = photos.map { it.absolutePath },
            estimateState = if (estimator != null) EstimateState.PENDING else EstimateState.NONE,
        )
        val saved = entry.copy(id = db.meals().insert(entry))
        if (estimator != null) estimate(saved.id)
        return saved
    }

    /** 칼로리 추정은 앱 전역 스코프에서 돌아서, 위젯 촬영 창이 닫혀도 계속 진행돼요. */
    fun estimate(entryId: Long) {
        val est = estimator ?: return
        scope.launch {
            val current = db.meals().get(entryId) ?: return@launch
            db.meals().update(current.copy(estimateState = EstimateState.PENDING))
            val result = runCatching { est.estimate(current.photos.map(::File)) }
            val latest = db.meals().get(entryId) ?: return@launch
            db.meals().update(
                latest.copy(
                    items = result.getOrNull() ?: latest.items,
                    estimateState = if (result.isSuccess) EstimateState.DONE else EstimateState.FAILED,
                )
            )
        }
    }

    suspend fun update(e: MealEntry) = db.meals().update(e)

    suspend fun setTime(e: MealEntry, takenAt: Long) {
        val day = Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault()).toLocalDate().toEpochDay()
        db.meals().update(e.copy(takenAt = takenAt, epochDay = day))
    }

    suspend fun updateNote(id: Long, note: String) {
        val latest = db.meals().get(id) ?: return
        db.meals().update(latest.copy(note = note))
    }

    /** 화면이 닫혀도 끝까지 지워지도록 앱 스코프에서 실행해요. */
    fun delete(e: MealEntry) {
        scope.launch {
            db.meals().delete(e)
            e.photos.forEach { File(it).delete() }
        }
    }

    suspend fun saveSlot(s: TimeSlot) = db.slots().upsert(s)
    suspend fun deleteSlot(s: TimeSlot) = db.slots().delete(s)
}
