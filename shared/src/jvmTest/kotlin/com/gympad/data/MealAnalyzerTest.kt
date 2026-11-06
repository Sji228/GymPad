package com.gympad.data

import com.gympad.domain.AppLanguage
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class MemorySettings(var key: String? = null, var model: String? = null) : AiSettingsStore {
    override fun loadKey() = key
    override fun saveKey(key: String?) {
        this.key = key
    }
    override fun loadModel() = model
    override fun saveModel(model: String?) {
        this.model = model
    }
}

/** Answers like Google would, and remembers what the app sent. */
class FakeGoogle(private val answer: (method: String, url: String, body: String?) -> HttpResult) : HttpTransport {
    val requests = mutableListOf<Triple<String, String, String?>>()
    override suspend fun send(method: String, url: String, body: String?): HttpResult {
        requests += Triple(method, url, body)
        return answer(method, url, body)
    }
}

fun geminiReply(answer: String): String =
    """{"candidates":[{"content":{"parts":[{"text":${JsonPrimitive(answer)}}]},"finishReason":"STOP"}]}"""

const val TWO_FOODS = """{"items":[{"name":"Fried chicken breast","grams":180,"kcal":390,"protein":52,"carbs":2,"fat":19},""" +
    """{"name":"White rice","grams":150,"kcal":195,"protein":4,"carbs":42,"fat":0.5}],"note":"Assumed one tablespoon of olive oil."}"""

class MealAnalyzerTest {
    private val photo = byteArrayOf(1, 2, 3)

    private fun analyzer(settings: AiSettingsStore, google: FakeGoogle) = MealAnalyzer(settings, google)

    private fun errorOf(block: suspend () -> Unit): AiError = runBlocking {
        try {
            block()
            fail("Expected an AiException")
            error("unreachable")
        } catch (e: AiException) {
            e.error
        }
    }

    @Test fun `a key is saved only after Google accepts it`() {
        val settings = MemorySettings()
        val bad = FakeGoogle { _, _, _ ->
            HttpResult(400, """{"error":{"code":400,"message":"API key not valid. Please pass a valid API key.","details":[{"reason":"API_KEY_INVALID"}]}}""")
        }
        assertEquals(AiError.InvalidKey, errorOf { analyzer(settings, bad).saveKey("AIzaWRONG") })
        assertNull(settings.key)

        val good = FakeGoogle { _, _, _ -> HttpResult(200, """{"models":[]}""") }
        runBlocking { analyzer(settings, good).saveKey("  AIzaGOOD  ") }
        assertEquals("AIzaGOOD", settings.key)
        assertTrue(good.requests.single().second.endsWith("key=AIzaGOOD"))
    }

    @Test fun `the photo, the note and the hidden instructions go out together`() {
        val google = FakeGoogle { _, _, _ -> HttpResult(200, geminiReply(TWO_FOODS)) }
        val estimate = runBlocking {
            analyzer(MemorySettings("AIzaKEY"), google).analyze(photo, "fried in olive oil", AppLanguage.POLISH)
        }

        val (method, url, body) = google.requests.single()
        assertEquals("POST", method)
        assertTrue(url.contains("/models/${PREFERRED_MODELS.first()}:generateContent?key=AIzaKEY"))
        val request = Json.parseToJsonElement(body!!).jsonObject
        val instructions = request["systemInstruction"]!!.jsonObject["parts"]!!.jsonArray[0].jsonObject["text"]!!.jsonPrimitive.content
        assertTrue("grams" in instructions && "Polish" in instructions)
        val parts = request["contents"]!!.jsonArray[0].jsonObject["parts"]!!.jsonArray
        assertTrue("fried in olive oil" in parts[0].jsonObject["text"]!!.jsonPrimitive.content)
        assertEquals("AQID", parts[1].jsonObject["inlineData"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        val config = request["generationConfig"]!!.jsonObject
        assertEquals("application/json", config["responseMimeType"]!!.jsonPrimitive.content)
        assertTrue("responseJsonSchema" in config)

        assertEquals(listOf("Fried chicken breast", "White rice"), estimate.foods.map { it.name })
        assertEquals(180.0, estimate.foods[0].grams, 0.0)
        assertEquals(0.5, estimate.foods[1].fat, 0.0)
        assertEquals("Assumed one tablespoon of olive oil.", estimate.note)
    }

    @Test fun `a retired model is replaced by the newest Flash model`() {
        val settings = MemorySettings("AIzaKEY")
        val google = FakeGoogle { method, url, _ ->
            when {
                method == "GET" -> HttpResult(
                    200,
                    """{"models":[
                        {"name":"models/gemini-3.6-flash","supportedGenerationMethods":["generateContent"]},
                        {"name":"models/gemini-3.8-flash","supportedGenerationMethods":["generateContent"]},
                        {"name":"models/gemini-3.8-flash-tts","supportedGenerationMethods":["generateContent"]},
                        {"name":"models/gemini-9-flash","supportedGenerationMethods":["embedContent"]}
                    ]}""",
                )
                PREFERRED_MODELS.any { "/$it:" in url } -> HttpResult(404, """{"error":{"code":404,"message":"not found"}}""")
                else -> HttpResult(200, geminiReply(TWO_FOODS))
            }
        }
        val estimate = runBlocking { analyzer(settings, google).analyze(photo, "", AppLanguage.ENGLISH) }
        assertEquals("gemini-3.8-flash", settings.model)
        assertEquals(2, estimate.foods.size)
        assertTrue(google.requests.last().second.contains("gemini-3.8-flash:generateContent"))
    }

    @Test fun `an overloaded or silent model hands over to the next, which is remembered`() {
        val settings = MemorySettings("AQ.TestKey")
        val google = FakeGoogle { _, url, _ ->
            when {
                "/${PREFERRED_MODELS[0]}:" in url -> HttpResult(503, """{"error":{"code":503,"message":"The model is overloaded."}}""")
                "/${PREFERRED_MODELS[1]}:" in url -> throw SocketTimeoutException("read timed out")
                else -> HttpResult(200, geminiReply(TWO_FOODS))
            }
        }
        val estimate = runBlocking { analyzer(settings, google).analyze(photo, "", AppLanguage.ENGLISH) }
        assertEquals(2, estimate.foods.size)
        assertEquals(PREFERRED_MODELS[2], settings.model)

        // Next time the best model still gets the first try, then the one that answered, skipping the silent one.
        google.requests.clear()
        runBlocking { analyzer(settings, google).analyze(photo, "", AppLanguage.ENGLISH) }
        assertEquals(listOf(PREFERRED_MODELS[0], PREFERRED_MODELS[2]), google.requests.map { it.second.substringAfter("/models/").substringBefore(":") })
    }

    @Test fun `a model that refuses the schema gets the plain JSON request`() {
        val google = FakeGoogle { _, _, body ->
            if ("responseJsonSchema" in body!!) {
                HttpResult(400, """{"error":{"code":400,"message":"Unknown name \"responseJsonSchema\": schema not supported"}}""")
            } else {
                HttpResult(200, geminiReply(TWO_FOODS))
            }
        }
        val estimate = runBlocking { analyzer(MemorySettings("AIzaKEY"), google).analyze(photo, "", AppLanguage.ENGLISH) }
        assertEquals(2, google.requests.size)
        assertEquals(2, estimate.foods.size)
    }

    @Test fun `answers in a code fence still read, nonsense items are dropped`() {
        val fenced = "```json\n{\"items\":[{\"name\":\"Apple\",\"grams\":180,\"kcal\":94,\"protein\":0.5,\"carbs\":25,\"fat\":0.3}," +
            "{\"name\":\"Ghost\",\"grams\":0,\"kcal\":10},{\"name\":\"\",\"grams\":50,\"kcal\":10}]}\n```"
        val google = FakeGoogle { _, _, _ -> HttpResult(200, geminiReply(fenced)) }
        val estimate = runBlocking { analyzer(MemorySettings("AIzaKEY"), google).analyze(photo, "", AppLanguage.ENGLISH) }
        assertEquals(listOf("Apple"), estimate.foods.map { it.name })
        assertNull(estimate.note)
    }

    @Test fun `failures map to messages the user can act on`() {
        fun failing(status: Int, body: String = "{}") = FakeGoogle { _, _, _ -> HttpResult(status, body) }
        val settings = MemorySettings("AIzaKEY")
        assertEquals(AiError.RateLimited, errorOf { analyzer(settings, failing(429)).analyze(photo, "", AppLanguage.ENGLISH) })
        assertEquals(AiError.Busy, errorOf { analyzer(settings, failing(503)).analyze(photo, "", AppLanguage.ENGLISH) })
        assertEquals(AiError.InvalidKey, errorOf { analyzer(settings, failing(403)).analyze(photo, "", AppLanguage.ENGLISH) })
        assertEquals(
            AiError.Blocked,
            errorOf { analyzer(settings, failing(200, """{"promptFeedback":{"blockReason":"SAFETY"}}""")).analyze(photo, "", AppLanguage.ENGLISH) },
        )
        assertEquals(AiError.Unreadable, errorOf { analyzer(settings, failing(200, geminiReply("no idea"))).analyze(photo, "", AppLanguage.ENGLISH) })
        val offline = FakeGoogle { _, _, _ -> throw IOException("no route") }
        assertEquals(AiError.Offline, errorOf { analyzer(settings, offline).analyze(photo, "", AppLanguage.ENGLISH) })
        assertEquals(AiError.NoKey, errorOf { analyzer(MemorySettings(), failing(200)).analyze(photo, "", AppLanguage.ENGLISH) })
    }

    @Test fun `the key is shown masked and can be removed`() {
        val settings = MemorySettings("AIzaSyD1234567890xyz9Q", "gemini-3.8-flash")
        val analyzer = analyzer(settings, FakeGoogle { _, _, _ -> HttpResult(200, "{}") })
        assertEquals("AIza…yz9Q", analyzer.maskedKey())
        analyzer.removeKey()
        assertFalse(analyzer.hasKey())
        assertNull(settings.model)
    }
}
