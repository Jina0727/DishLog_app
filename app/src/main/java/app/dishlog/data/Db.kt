package app.dishlog.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

/** 사진으로 인식한 음식 하나. kcal 은 '보통' 양 기준 추정치. */
data class FoodItem(val name: String, val kcal: Int)

enum class EstimateState { NONE, PENDING, DONE, FAILED }

val FULLNESS_LABELS = listOf("덜 참", "적당", "배부름")

@Entity(tableName = "meals", indices = [Index("epochDay")])
data class MealEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAt: Long,
    val epochDay: Long,
    val tag: String?,
    val photos: List<String>,
    val note: String = "",
    val items: List<FoodItem> = emptyList(),
    val estimateState: EstimateState = EstimateState.NONE,
    /** (사용 안 함) 예전 '먹은 양' 값. 칼로리는 hint 로 보정해요. */
    val portion: Int = -1,
    /** null = 선택 안 함, 0..2 = 덜 참/적당/배부름 */
    val fullness: Int? = null,
    /** 칼로리 추정 때 Claude 에게 같이 보내는 말 (예: "밥은 반 공기만 먹음") */
    val hint: String = "",
)

/** 총 추정 칼로리. 추정 결과가 없으면 null. */
fun MealEntry.totalKcal(): Int? = if (items.isEmpty()) null else items.sumOf { it.kcal }

/**
 * 사용자가 정한 식사 구간. 분 단위(0~1439). end < start 이면 자정을 넘는 구간.
 * color: 태그 칩 색 번호, icon: 태그 칩 아이콘 키 (ui/Tags.kt 참고)
 */
@Entity(tableName = "slots")
data class TimeSlot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startMin: Int,
    val endMin: Int,
    val color: Int = 0,
    val icon: String = "meal",
)

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE slots ADD COLUMN color INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE slots ADD COLUMN icon TEXT NOT NULL DEFAULT 'meal'")
        // 기본으로 넣어둔 구간엔 어울리는 색·아이콘을 붙여줘요
        db.execSQL("UPDATE slots SET color = 0, icon = 'rice' WHERE name = '점심'")
        db.execSQL("UPDATE slots SET color = 1, icon = 'coffee' WHERE name = '간식'")
        db.execSQL("UPDATE slots SET color = 2, icon = 'dinner' WHERE name = '저녁'")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE meals ADD COLUMN hint TEXT NOT NULL DEFAULT ''")
    }
}

class Converters {
    @TypeConverter
    fun stringsToJson(v: List<String>): String = JSONArray(v).toString()

    @TypeConverter
    fun jsonToStrings(v: String): List<String> {
        val a = JSONArray(v)
        return List(a.length()) { a.getString(it) }
    }

    @TypeConverter
    fun itemsToJson(v: List<FoodItem>): String = JSONArray().apply {
        v.forEach { put(JSONObject().put("name", it.name).put("kcal", it.kcal)) }
    }.toString()

    @TypeConverter
    fun jsonToItems(v: String): List<FoodItem> {
        val a = JSONArray(v)
        return List(a.length()) {
            val o = a.getJSONObject(it)
            FoodItem(o.getString("name"), o.getInt("kcal"))
        }
    }
}

@Dao
interface MealDao {
    @Query("SELECT * FROM meals WHERE epochDay = :day ORDER BY takenAt")
    fun observeDay(day: Long): Flow<List<MealEntry>>

    @Query("SELECT DISTINCT epochDay FROM meals WHERE epochDay BETWEEN :from AND :to")
    fun observeDaysWithEntries(from: Long, to: Long): Flow<List<Long>>

    @Query("SELECT * FROM meals WHERE id = :id")
    fun observe(id: Long): Flow<MealEntry?>

    @Query("SELECT * FROM meals WHERE id = :id")
    suspend fun get(id: Long): MealEntry?

    @Insert
    suspend fun insert(e: MealEntry): Long

    @Update
    suspend fun update(e: MealEntry)

    @Delete
    suspend fun delete(e: MealEntry)
}

@Dao
interface SlotDao {
    @Query("SELECT * FROM slots ORDER BY startMin")
    fun observeAll(): Flow<List<TimeSlot>>

    @Query("SELECT * FROM slots ORDER BY startMin")
    suspend fun getAll(): List<TimeSlot>

    @Upsert
    suspend fun upsert(s: TimeSlot)

    @Delete
    suspend fun delete(s: TimeSlot)
}

@Database(entities = [MealEntry::class, TimeSlot::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun meals(): MealDao
    abstract fun slots(): SlotDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "dishlog.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // 기본 구간. 설정에서 자유롭게 바꾸거나 지울 수 있어요.
                        db.execSQL(
                            "INSERT INTO slots(name, startMin, endMin, color, icon) VALUES " +
                                "('점심', 780, 900, 0, 'rice'), ('간식', 900, 1080, 1, 'coffee'), ('저녁', 1080, 1260, 2, 'dinner')"
                        )
                    }
                })
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
