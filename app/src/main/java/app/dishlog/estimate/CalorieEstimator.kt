package app.dishlog.estimate

import android.graphics.Bitmap
import android.util.Base64
import app.dishlog.data.FoodItem
import app.dishlog.data.decodeUpright
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

interface CalorieEstimator {
    /**
     * 한 끼에 찍은 사진들(여러 장 가능)을 받아 음식별 추정 칼로리를 돌려줘요.
     * hint: 사용자가 덧붙인 말 (예: "밥은 반 공기만 먹음"). 사진보다 우선해요.
     */
    suspend fun estimate(photos: List<File>, hint: String = ""): List<FoodItem>
}

/**
 * 비전 LLM(Claude)으로 사진 속 음식을 인식하고 양을 가늠해 칼로리를 추정해요.
 * 여러 장을 한 번에 보내서 겹쳐 찍힌 음식은 한 번만 세게 해요.
 *
 * API 키는 앱 설정에서 입력받아 폰 안에만 저장해요 (APK·저장소엔 들어가지 않아요).
 */
class ClaudeCalorieEstimator(
    private val apiKey: String,
    private val model: String,
    /** 보내는 사진의 긴 변(px). 클수록 글씨를 잘 읽지만 토큰(비용)이 늘어요. */
    private val maxSide: Int = 2000,
) : CalorieEstimator {

    /** 추정 결과 + 실제 사용량 (모델 비교 실험용). */
    data class Run(val items: List<FoodItem>, val inputTokens: Int, val outputTokens: Int)

    override suspend fun estimate(photos: List<File>, hint: String): List<FoodItem> = run(photos, hint).items

    suspend fun run(photos: List<File>, hint: String): Run = withContext(Dispatchers.IO) {
        val content = JSONArray()
        photos.forEach { f ->
            content.put(
                JSONObject()
                    .put("type", "image")
                    .put(
                        "source", JSONObject()
                            .put("type", "base64")
                            .put("media_type", "image/jpeg")
                            .put("data", encodeScaled(f))
                    )
            )
        }
        val prompt = if (hint.isBlank()) PROMPT else PROMPT + """

사용자 메모: "${hint.trim()}"
이 메모는 사진보다 우선해요. 남긴 양, 덜 먹은 양, 사진에 안 보이는 음식·음료가 적혀 있으면
실제로 먹은 만큼으로 칼로리를 고치고, 음식 이름에도 반영하세요 (예: "쌀밥 반 공기").
"""
        content.put(JSONObject().put("type", "text").put("text", prompt))

        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1024)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))

        val conn = (URL("https://api.anthropic.com/v1/messages").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 90_000
            setRequestProperty("content-type", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                throw IOException("칼로리 추정 요청 실패 ($code): $err")
            }
            val resp = JSONObject(conn.inputStream.bufferedReader().readText())
            val blocks = resp.getJSONArray("content")
            val text = buildString {
                for (i in 0 until blocks.length()) {
                    val b = blocks.getJSONObject(i)
                    if (b.optString("type") == "text") append(b.getString("text"))
                }
            }
            val usage = resp.optJSONObject("usage")
            Run(parse(text), usage?.optInt("input_tokens") ?: 0, usage?.optInt("output_tokens") ?: 0)
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String): List<FoodItem> {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw IOException("응답에서 결과를 찾지 못했어요")
        val items = JSONObject(text.substring(start, end + 1)).optJSONArray("items") ?: JSONArray()
        return List(items.length()) {
            val o = items.getJSONObject(it)
            FoodItem(o.getString("name"), o.getInt("kcal"), o.optString("note"))
        }
    }

    /** 긴 변을 maxSide 로 맞춰 보내요. 기본 2000px 은 포장지·영양성분표 글씨도 읽힐 만큼. */
    private fun encodeScaled(file: File): String {
        // 회전 정보(EXIF)를 반영해 바로 선 사진으로 보내요.
        val decoded = decodeUpright(file.absolutePath, maxSide / 2)
            ?: throw IOException("사진을 열 수 없어요: ${file.name}")
        val longSide = maxOf(decoded.width, decoded.height)
        val bmp = if (longSide <= maxSide) decoded else {
            val scale = maxSide.toFloat() / longSide
            Bitmap.createScaledBitmap(decoded, (decoded.width * scale).toInt(), (decoded.height * scale).toInt(), true)
                .also { decoded.recycle() }
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
        bmp.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        const val MODEL = "claude-sonnet-5-5"
        const val MODEL_HAIKU = "claude-haiku-5-5"

        /** 100만 토큰당 달러 (입력, 출력) */
        val PRICES = mapOf(MODEL to (2.0 to 10.0), MODEL_HAIKU to (0.10 to 0.50))

        fun costUsd(model: String, inputTokens: Int, outputTokens: Int): Double {
            val (inP, outP) = PRICES[model] ?: return 0.0
            return inputTokens * inP / 1_000_000 + outputTokens * outP / 1_000_000
        }

        /** 키가 유효한지 가볍게 확인해요 (모델 목록 조회, 사용량이 들지 않아요). */
        suspend fun verify(apiKey: String): Boolean = withContext(Dispatchers.IO) {
            runCatching {
                val conn = (URL("https://api.anthropic.com/v1/models?limit=1").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 10_000
                    readTimeout = 15_000
                    setRequestProperty("x-api-key", apiKey.trim())
                    setRequestProperty("anthropic-version", "2023-06-01")
                }
                try { conn.responseCode in 200..299 } finally { conn.disconnect() }
            }.getOrDefault(false)
        }

        private const val PROMPT = """
이 사진들은 한 사람이 한 번에 먹은 식사(또는 간식·음료)를 찍은 것이에요.
그릇이 여러 개이거나 여러 장에 나눠 찍혔을 수 있어요. 같은 음식이 여러 장에 보이면 한 번만 세세요.

음식·음료마다 한국어 이름과 실제로 먹는 양 기준 칼로리(kcal, 정수)를 구하세요.
- 포장 제품이면 포장지·영양성분표에 적힌 칼로리를 가장 먼저 읽고 그대로 쓰세요. 제품명도 그대로 쓰세요.
- 우유·물에 타 먹는 쉐이크, 끓여 먹는 라면처럼 먹는 방법이 정해진 제품은
  포장지의 조리법(없으면 가장 일반적인 방법)대로 먹었다고 보고, 더해지는 재료(예: 우유 200ml)의 칼로리를
  별도 항목으로 넣으세요.
- 조리된 음식은 그릇·수저 크기를 단서로 양을 가늠하세요.
- 각 항목의 note 에는 근거나 가정을 짧게 적으세요 (예: "포장지 표기", "우유 200ml 기준", "1공기 210g 추정").

설명 없이 아래 JSON 형식으로만 답하세요. 음식이 없으면 items 를 빈 배열로 두세요.
{"items":[{"name":"단백질 쉐이크 파우치","kcal":158,"note":"포장지 표기"},{"name":"우유 200ml","kcal":130,"note":"조리법 기준 가정"}]}
"""
    }
}
