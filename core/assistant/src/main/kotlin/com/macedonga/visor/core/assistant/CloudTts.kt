package com.macedonga.visor.core.assistant

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Audio a synthesis service returned: 16-bit PCM and the rate it was sampled at. */
class SpeechPcm(val samples: ShortArray, val rate: Int)

/** Why a cloud synthesis could not be had, with the service's own words where it gave any. */
class SpeechError(message: String) : Exception(message)

/**
 * Text to speech at an OpenAI-compatible endpoint: `POST {base}/audio/speech` with
 * `{model, input, voice, response_format}`, answered with the audio itself.
 *
 * The format asked for is `wav`, not `pcm`: a WAV carries its own sample rate in the header, so the
 * same decoder works whatever the service synthesises at, and a service that offers `pcm` usually
 * offers `wav` too. Raw PCM would mean guessing the rate from the documentation of each provider.
 *
 * The whole point of one shape is that it is not one service: any endpoint that answers
 * `GET {base}/models` with a `data` array and accepts this body speaks it.
 */
suspend fun cloudSpeech(config: AssistantConfig, text: String): SpeechPcm = withContext(Dispatchers.IO) {
    val base = config.ttsBaseUrl.trimEnd('/')
    check(base.isNotBlank()) { "no speech service is set" }
    check(config.ttsModel.isNotBlank()) { "no speech model is chosen" }
    val body = org.json.JSONObject()
        .put("model", config.ttsModel)
        .put("input", text)
        .put("voice", config.ttsVoice.ifBlank { "alloy" })
        .put("response_format", "wav")
        .toString()
        .toByteArray()
    val http = URL("$base/audio/speech").openConnection() as HttpURLConnection
    try {
        http.requestMethod = "POST"
        http.doOutput = true
        http.connectTimeout = 8_000
        http.readTimeout = 30_000
        http.setRequestProperty("Content-Type", "application/json")
        if (config.ttsApiKey.isNotBlank()) http.setRequestProperty("Authorization", "Bearer ${config.ttsApiKey}")
        http.outputStream.use { it.write(body) }
        val code = http.responseCode
        if (code !in 200..299) {
            val text = http.errorStream?.use { it.readBytes().decodeToString() }.orEmpty()
            val message = runCatching { org.json.JSONObject(text).getJSONObject("error").getString("message") }
                .getOrDefault(text.take(200))
            throw SpeechError(message.ifEmpty { "HTTP $code" })
        }
        val wav = http.inputStream.use { it.readBytes() }
        check(wav.size > 44) { "the speech service returned no audio" }
        wavToPcm(wav)
    } finally {
        http.disconnect()
    }
}

/**
 * 16-bit PCM WAV, mixed down to mono. Some services leave the data size unset, so it is clamped to
 * what is actually there — the same tolerance the phone's own text to speech needs.
 */
internal fun wavToPcm(bytes: ByteArray): SpeechPcm {
    val b = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
    var rate = 48_000
    var channels = 1
    var bits = 16
    var offset = 12
    var dataAt = -1
    var dataSize = -1
    while (offset + 8 <= bytes.size) {
        val id = String(bytes, offset, 4, Charsets.US_ASCII)
        val size = b.getInt(offset + 4)
        val body = offset + 8
        when (id) {
            "fmt " -> {
                channels = b.getShort(body + 2).toInt().coerceAtLeast(1)
                rate = b.getInt(body + 4).coerceAtLeast(1)
                bits = b.getShort(body + 14).toInt().coerceAtLeast(8)
            }
            "data" -> {
                dataAt = body
                dataSize = size
            }
        }
        offset = body + size + (size and 1)
    }
    if (dataAt < 0) throw SpeechError("the speech service's audio has no data chunk")
    val have = (bytes.size - dataAt).coerceAtMost(if (dataSize > 0) dataSize else bytes.size - dataAt)
    val step = (bits / 8).coerceAtLeast(1)
    val frames = have / (step * channels)
    val out = ShortArray(frames)
    for (i in 0 until frames) {
        var sum = 0
        for (c in 0 until channels) {
            val at = dataAt + (i * channels + c) * step
            sum += if (bits == 8) ((bytes[at].toInt() and 0xFF) - 128) shl 8 else b.getShort(at).toInt()
        }
        out[i] = (sum / channels).toShort()
    }
    return SpeechPcm(out, rate)
}
