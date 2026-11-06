package com.gympad.data

import com.gympad.domain.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.util.Base64

/** The user's own Gemini key, kept on the device and out of backups. */
interface AiSettingsStore {
    fun loadKey(): String?
    fun saveKey(key: String?)

    /** The model that answered last, tried right after the preferred one. */
    fun loadModel(): String?
    fun saveModel(model: String?)
}

/** One food Gemini found on the plate. Values are for the whole portion, not per 100 g. */
data class EstimatedFood(
    val name: String,
    val grams: Double,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
)

data class MealEstimate(val foods: List<EstimatedFood>, val note: String?)

sealed interface AiError {
    data object NoKey : AiError
    data object InvalidKey : AiError
    data object RateLimited : AiError
    data object Offline : AiError
    data object Busy : AiError
    data object Blocked : AiError
    data object Unreadable : AiError
    data class Other(val message: String) : AiError
}

class AiException(val error: AiError) : Exception(error.toString())

data class HttpResult(val status: Int, val body: String)

/** Plain HTTP, so tests can answer for Google without a network. */
fun interface HttpTransport {
    suspend fun send(method: String, url: String, body: String?): HttpResult
}

object UrlConnectionTransport : HttpTransport {
    override suspend fun send(method: String, url: String, body: String?): HttpResult = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            // An overloaded model can hang for minutes; better to give up and ask another one.
            connection.readTimeout = 30_000
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            HttpResult(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

/**
 * Free-tier models to try, in order. The first gives the best estimates when it's free; the
 * "latest" aliases, which Google keeps pointing at current models, back it up. The newest Flash
 * is last because on the free tier it is often overloaded and can hang until the timeout.
 */
val PREFERRED_MODELS = listOf("gemini-3.6-flash", "gemini-flash-lite-latest", "gemini-flash-latest")
private const val API = "https://generativelanguage.googleapis.com/v1beta"

/** Stands in for an HTTP status when Google didn't answer in time, which reads as "busy". */
private const val TIMED_OUT = 504

/**
 * Sends a meal photo to Gemini with the user's key. The instructions and a JSON schema travel
 * with every photo, so the answer always comes back in a shape the diary can read.
 */
class MealAnalyzer(
    private val settings: AiSettingsStore,
    private val transport: HttpTransport = UrlConnectionTransport,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun hasKey(): Boolean = !settings.loadKey().isNullOrBlank()

    fun maskedKey(): String? = settings.loadKey()?.let { key ->
        if (key.length <= 8) "•".repeat(key.length) else "${key.take(4)}…${key.takeLast(4)}"
    }

    fun removeKey() {
        settings.saveKey(null)
        settings.saveModel(null)
    }

    /** Checks the key with Google before saving it, so a typo shows up now rather than at the first meal. */
    suspend fun saveKey(key: String) {
        val trimmed = key.trim()
        val result = request("GET", "$API/models?pageSize=1&key=$trimmed", null)
        if (result.status != 200) throw AiException(errorFor(result))
        settings.saveKey(trimmed)
    }

    suspend fun analyze(jpeg: ByteArray, note: String, language: AppLanguage): MealEstimate {
        val key = settings.loadKey()?.takeIf { it.isNotBlank() } ?: throw AiException(AiError.NoKey)
        // The best model gets a chance every time; when it's busy, the last one that answered goes next.
        val candidates = (listOf(PREFERRED_MODELS.first()) + listOfNotNull(settings.loadModel()) + PREFERRED_MODELS).distinct()
        var lastFailure: HttpResult? = null
        var anyAvailable = false
        for (model in candidates) {
            val result = attempt(key, model, jpeg, note, language)
            when {
                result.status == 200 -> return parseEstimate(result.body).also { settings.saveModel(model) }
                // Retired or unknown to this key: not a reason to stop, just a name to skip.
                result.status == 404 -> continue
                isTemporary(result.status) -> {
                    anyAvailable = true
                    lastFailure = result
                }
                else -> throw AiException(errorFor(result))
            }
        }
        if (!anyAvailable) {
            // Every known name was retired; pick the newest Flash model this key can use.
            val replacement = newestFlashModel(key)
            if (replacement != null) {
                val result = attempt(key, replacement, jpeg, note, language)
                if (result.status == 200) return parseEstimate(result.body).also { settings.saveModel(replacement) }
                lastFailure = result
            }
        }
        throw AiException(lastFailure?.let(::errorFor) ?: AiError.Busy)
    }

    /** Overloaded, rate-limited or timed out: another model may well answer. */
    private fun isTemporary(status: Int) = status == 429 || status >= 500

    private suspend fun attempt(key: String, model: String, jpeg: ByteArray, note: String, language: AppLanguage): HttpResult {
        val result = generate(key, model, jpeg, note, language, withSchema = true)
        if (result.status == 400 && result.body.contains("schema", ignoreCase = true)) {
            // A model that rejects the schema still follows the same format described in the instructions.
            return generate(key, model, jpeg, note, language, withSchema = false)
        }
        return result
    }

    private suspend fun generate(
        key: String,
        model: String,
        jpeg: ByteArray,
        note: String,
        language: AppLanguage,
        withSchema: Boolean,
    ): HttpResult {
        val body = buildJsonObject {
            putJsonObject("systemInstruction") {
                putJsonArray("parts") { add(buildJsonObject { put("text", instructions(language)) }) }
            }
            putJsonArray("contents") {
                add(
                    buildJsonObject {
                        put("role", "user")
                        putJsonArray("parts") {
                            add(buildJsonObject { put("text", userText(note)) })
                            add(
                                buildJsonObject {
                                    putJsonObject("inlineData") {
                                        put("mimeType", "image/jpeg")
                                        put("data", Base64.getEncoder().encodeToString(jpeg))
                                    }
                                },
                            )
                        }
                    },
                )
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                if (withSchema) put("responseJsonSchema", RESPONSE_SCHEMA)
            }
        }
        return request("POST", "$API/models/$model:generateContent?key=$key", body.toString())
    }

    private suspend fun request(method: String, url: String, body: String?): HttpResult = try {
        transport.send(method, url, body)
    } catch (_: SocketTimeoutException) {
        HttpResult(TIMED_OUT, "")
    } catch (_: IOException) {
        throw AiException(AiError.Offline)
    }

    private suspend fun newestFlashModel(key: String): String? {
        val result = request("GET", "$API/models?pageSize=1000&key=$key", null)
        if (result.status != 200) return null
        val pattern = Regex("""models/gemini-(\d+(?:\.\d+)?)-flash""")
        return runCatching {
            json.parseToJsonElement(result.body).jsonObject["models"]?.jsonArray.orEmpty()
                .mapNotNull { model ->
                    val name = model.jsonObject["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val methods = model.jsonObject["supportedGenerationMethods"]?.jsonArray
                        ?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty()
                    val version = pattern.matchEntire(name)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapNotNull null
                    if ("generateContent" in methods) name.removePrefix("models/") to version else null
                }
                .maxByOrNull { it.second }?.first
        }.getOrNull()
    }

    private fun errorFor(result: HttpResult): AiError {
        val error = runCatching { json.parseToJsonElement(result.body).jsonObject["error"]?.jsonObject }.getOrNull()
        val message = error?.get("message")?.jsonPrimitive?.contentOrNull.orEmpty()
        return when {
            result.status == 400 && ("API_KEY_INVALID" in result.body || "API key not valid" in message) -> AiError.InvalidKey
            result.status == 401 || result.status == 403 -> AiError.InvalidKey
            result.status == 429 -> AiError.RateLimited
            result.status >= 500 -> AiError.Busy
            else -> AiError.Other(message.ifBlank { "HTTP ${result.status}" })
        }
    }

    internal fun parseEstimate(body: String): MealEstimate {
        val response = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw AiException(AiError.Unreadable)
        val candidate = response["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
        if (candidate == null) {
            throw AiException(if (response["promptFeedback"] != null) AiError.Blocked else AiError.Unreadable)
        }
        if (candidate["finishReason"]?.jsonPrimitive?.contentOrNull in setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST")) {
            throw AiException(AiError.Blocked)
        }
        val text = candidate["content"]?.jsonObject?.get("parts")?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            .joinToString("")
        // Tolerates a reply wrapped in a code fence or a sentence, should a model add one.
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException(AiError.Unreadable)
        val answer = runCatching { json.parseToJsonElement(text.substring(start, end + 1)).jsonObject }.getOrNull()
            ?: throw AiException(AiError.Unreadable)
        val foods = (answer["items"] as? JsonArray).orEmpty().mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            fun number(field: String) = item[field]?.jsonPrimitive?.doubleOrNull?.takeIf { it.isFinite() && it >= 0 }
            val name = item["name"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val grams = number("grams")?.takeIf { it > 0 } ?: return@mapNotNull null
            EstimatedFood(
                name = name.take(80),
                grams = grams.coerceAtMost(5_000.0),
                kcal = number("kcal") ?: return@mapNotNull null,
                protein = number("protein") ?: 0.0,
                carbs = number("carbs") ?: 0.0,
                fat = number("fat") ?: 0.0,
            )
        }
        val note = answer["note"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        return MealEstimate(foods, note)
    }

    private fun userText(note: String): String =
        if (note.isBlank()) {
            "Estimate this meal."
        } else {
            "Estimate this meal. The person who ate it says: \"${note.trim()}\""
        }

    private fun instructions(language: AppLanguage): String {
        val languageName = if (language == AppLanguage.POLISH) "Polish" else "English"
        return """
            You estimate nutrition for a food diary app from a photo of a meal.
            List each separate food you can see. For each one, estimate the weight of the portion in grams,
            and the calories (kcal), protein, carbohydrates and fat in grams for that whole portion, not per 100 g.
            The person's note about the meal is more reliable than the photo: follow it for what the food is,
            how it was cooked, oil, sauces and portion size.
            Count cooking oil, butter and sauces: inside the food they coat, or as their own item when they are separate.
            If the photo shows a nutrition label or a packaged product, use its values.
            Name each food briefly and specifically in $languageName, for example "Fried chicken breast" or "White rice".
            Put one short sentence in "note" saying the main assumption you made, in $languageName.
            If there is no food in the photo, return an empty "items" list.
            Answer only with JSON in this shape:
            {"items":[{"name":"...","grams":0,"kcal":0,"protein":0,"carbs":0,"fat":0}],"note":"..."}
        """.trimIndent()
    }

    private companion object {
        val RESPONSE_SCHEMA = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("items") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("name") { put("type", "string") }
                            listOf("grams", "kcal", "protein", "carbs", "fat").forEach { field ->
                                putJsonObject(field) { put("type", "number") }
                            }
                        }
                        put(
                            "required",
                            buildJsonArray { listOf("name", "grams", "kcal", "protein", "carbs", "fat").forEach { add(JsonPrimitive(it)) } },
                        )
                    }
                }
                putJsonObject("note") { put("type", "string") }
            }
            put("required", buildJsonArray { add(JsonPrimitive("items")) })
        }
    }
}
