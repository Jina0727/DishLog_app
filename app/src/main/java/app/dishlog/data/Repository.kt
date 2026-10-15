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

    /** 칼로리 추정용 Claude API 키. 폰 안(앱 전용 저장소)에만 저장돼요. */
    var apiKey: String
        get() = sp.getString("api_key", "").orEmpty()
        set(v) = sp.edit().putString("api_key", v.trim()).apply()
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
    private val makeEstimator: (apiKey: String) -> CalorieEstimator,
    private val scope: CoroutineScope,
) {
    /** 설정에 키가 있을 때만 추정기를 만들어요. 키를 바꾸면 다음 호출부터 바로 반영돼요. */
    private val estimator: CalorieEstimator?
        get() = prefs.apiKey.takeIf { it.isNotBlank() }?.let(makeEstimator)

    private val photoDir = File(context.filesDir, "photos").apply { mkdirs() }

    val hasEstimator: Boolean get() = estimator != null
    val slots: Flow<List<TimeSlot>> = db.slots().observeAll()

    fun newPhotoFile(): File = File(photoDir, "${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")

    fun day(epochDay: Long): Flow<List<MealEntry>> = db.meals().observeDay(epochDay)
    fun daysWithEntries(from: Long, to: Long): Flow<List<Long>> = db.meals().observeDaysWithEntries(from, to)
    fun entry(id: Long): Flow<MealEntry?> = db.meals().observe(id)

    /** 기록이 추가·수정·삭제될 때마다 신호가 와요. 위젯 갱신에 써요. */
    val mealsChanged: Flow<Int> = db.meals().observeCount()

    data class DaySummary(val count: Int, val kcal: Int, val pending: Boolean)

    suspend fun todaySummary(): DaySummary {
        val list = db.meals().dayOnce(java.time.LocalDate.now().toEpochDay())
        return DaySummary(
            count = list.size,
            kcal = list.mapNotNull { it.totalKcal() }.sum(),
            pending = list.any { it.estimateState == EstimateState.PENDING },
        )
    }

    /** 찍은 사진들로 기록 하나를 만들고, 찍은 시간에 맞는 구간 태그를 자동으로 붙여요. */
    suspend fun createEntry(photos: List<File>, takenAt: Long = System.currentTimeMillis()): MealEntry {
        val zoned = Instant.ofEpochMilli(takenAt).atZone(ZoneId.systemDefault())
        val tag = TagResolver.resolve(minuteOfDay(takenAt), db.slots().getAll(), prefs.outsideNearest)
        val entry = MealEntry(
            takenAt = takenAt,
            epochDay = zoned.toLocalDate().toEpochDay(),
            tag = tag,
            photos = photos.map { it.absolutePath },
            estimateState = if (hasEstimator) EstimateState.PENDING else EstimateState.NONE,
        )
        val saved = entry.copy(id = db.meals().insert(entry))
        if (hasEstimator) estimate(saved.id)
        return saved
    }

    /** 칼로리 추정은 앱 전역 스코프에서 돌아서, 위젯 촬영 창이 닫혀도 계속 진행돼요. */
    /** hint 를 주면 기록에 저장하고 그걸 같이 보내요. null 이면 저장된 hint 를 그대로 써요. */
    fun estimate(entryId: Long, hint: String? = null) {
        val est = estimator ?: return
        scope.launch {
            val saved = db.meals().get(entryId) ?: return@launch
            val current = saved.copy(estimateState = EstimateState.PENDING, hint = hint?.trim() ?: saved.hint)
            db.meals().update(current)
            val result = runCatching { est.estimate(current.photos.map(::File), current.hint) }
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
