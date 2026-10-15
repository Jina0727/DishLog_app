package app.dishlog.estimate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import app.dishlog.data.FoodItem
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
    /** 한 끼에 찍은 사진들(여러 장 가능)을 받아 음식별 추정 칼로리를 돌려줘요. */
    suspend fun estimate(photos: List<File>): List<FoodItem>
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
) : CalorieEstimator {

    override suspend fun estimate(photos: List<File>): List<FoodItem> = withContext(Dispatchers.IO) {
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
        content.put(JSONObject().put("type", "text").put("text", PROMPT))

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
            parse(text)
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
            FoodItem(o.getString("name"), o.getInt("kcal"))
        }
    }

    /** 긴 변을 1280px 정도로 줄여서 보내요 (속도·비용 절약). */
    private fun encodeScaled(file: File): String {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1280) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("사진을 열 수 없어요: ${file.name}")
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
        bmp.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    companion object {
        const val MODEL = "claude-sonnet-5-5"

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
이 사진들은 한 끼 식사를 찍은 것이에요. 그릇이 여러 개이거나 여러 장에 나눠 찍혔을 수 있어요.
같은 음식이 여러 장에 보이면 한 번만 세세요.
보이는 음식마다 한국어 이름과, 사진에 보이는 양 기준 추정 칼로리(kcal, 정수)를 구하세요.
그릇·수저 크기를 단서로 양을 가늠하세요.
설명 없이 아래 JSON 형식으로만 답하세요. 음식이 없으면 items 를 빈 배열로 두세요.
{"items":[{"name":"김치찌개","kcal":280},{"name":"쌀밥 1공기","kcal":300}]}
"""
    }
}
