package com.macedonga.visor.language

import com.macedonga.visor.core.assistant.AssistantConfigStore
import com.macedonga.visor.core.connection.GlassesConnection
import com.macedonga.visor.core.media.GlassesConfig
import com.macedonga.visor.core.session.LinkState
import com.macedonga.visor.core.settings.GlassesSettings
import com.macedonga.visor.log.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Meta's switch for its session ("SCI") assistant path, on for English. There the glasses never speak Visor's text. */
const val SESSION_PATH_FLAG = "wearables_ai_phone_call.session_centric_air_infra"

/** How long the glasses wait for an answer before they speak their own error; 20 s from the factory. */
const val PROCESSING_TIMEOUT = "assistant_on_sn.default_processing_timeout_s"

private const val CAPTION_VOICE = "live_caption_voice_id"

/**
 * What Visor's assistant needs in the glasses' configuration, kept each time they connect or it changes: with the
 * assistant on, the processing timeout chosen in the app; with answers in their own voice, Meta's session flag off,
 * since only the classic path speaks Visor's text in every language. The configuration is read once if it never was.
 * The flag takes a restart of their assistant; the timeout applies at once.
 */
fun keepAssistantConfig(
    scope: CoroutineScope,
    connection: GlassesConnection,
    config: GlassesConfig,
    media: com.macedonga.visor.core.media.MediaImport,
    settings: GlassesSettings,
    store: AssistantConfigStore,
    log: EventLog,
) {
    scope.launch {
        // Whether the frames' assistant has been restarted since this link came up: once per connection is
        // enough, and a restart on every settings change would cut the assistant for no reason.
        var restartedThisLink = false
        combine(
            connection.state.map { it.link == LinkState.READY }.distinctUntilChanged(),
            store.config.map { Triple(it.enabled, it.glassesVoice, it.answerWaitSeconds.toLong()) }.distinctUntilChanged(),
        ) { ready, wanted -> wanted.takeIf { ready } }.collectLatest { wanted ->
            if (wanted == null) {
                restartedThisLink = false
                return@collectLatest
            }
            val (enabled, own, wait) = wanted
            if (!(enabled || own)) return@collectLatest
            // Let the link settle; the configuration comes in a bug report, a minute over Bluetooth.
            delay(SETTLE_MS)
            if (config.state.value.params.isEmpty()) {
                // The configuration comes in a bug report of several megabytes: never on top of photos to bring over.
                combine(media.importing, media.captures) { importing, captures -> !importing && captures.none { it.isNew && it.isMedia } }
                    .first { it }
                log.add("Assistant: reading the glasses' configuration once")
                config.read()
                if (withTimeoutOrNull(READ_MS) { config.state.first { it.params.isNotEmpty() } } == null) {
                    log.add("Assistant: the glasses' configuration did not arrive in ${READ_MS / 60_000} min; the session path stays as it is")
                    return@collectLatest
                }
            }
            val state = config.state.first { it.busy.isEmpty() }
            val changes = buildMap<String, Any> {
                if (enabled && PROCESSING_TIMEOUT in state.params && (state.values[PROCESSING_TIMEOUT] as? Number)?.toLong() != wait) put(PROCESSING_TIMEOUT, wait)
                // The flag's value comes from the glasses' own configuration, where a boolean can arrive as
                // true, as 1 or as "true". Only an exact Boolean true was matched before, so a numeric or
                // string value left Meta's session path on and the glasses never spoke the answer.
                if (own && SESSION_PATH_FLAG in state.params && state.values[SESSION_PATH_FLAG].asBoolean()) put(SESSION_PATH_FLAG, false)
            }
            // The classic path is what speaks Visor's text in every language. It is taken when the flag is
            // off — but only once the frames' assistant has read that, and it reads the configuration when it
            // starts, not before.
            val classic = own && SESSION_PATH_FLAG in state.params && !state.values[SESSION_PATH_FLAG].asBoolean()
            if (own) {
                log.add(
                    "Own voice: Meta's session path is " +
                        if (SESSION_PATH_FLAG !in state.params) "not in the glasses' configuration"
                        else if (state.values[SESSION_PATH_FLAG].asBoolean()) "on, so the glasses speak answers themselves"
                        else "off, so the glasses speak answers in every language",
                )
            }
            if (changes.isNotEmpty()) {
                if (PROCESSING_TIMEOUT in changes) log.add("Assistant: letting the glasses wait $wait s for an answer, not ${state.values[PROCESSING_TIMEOUT]}")
                if (SESSION_PATH_FLAG in changes) log.add("Own voice: turning Meta's session path off, so the glasses speak answers in every language")
                config.apply(changes)
                val written = withTimeoutOrNull(WRITE_MS) {
                    config.state.first { s -> s.busy.isEmpty() && changes.all { (key, value) -> s.values[key] == value } }
                }
                if (written == null) return@collectLatest log.add("Assistant: the glasses did not take the change: ${config.state.value.note}")
            }
            // The flag can read as off while the frames' assistant still runs the session path it read when it
            // started: a turn still comes in as use case 7 and the glasses say nothing. Only a restart makes it
            // read the configuration again. So restart once per connection whenever answers are to be in the
            // glasses' own voice and the session path is already off, not just when the flag has just changed.
            log.add("Own voice: classic path=$classic, restarted=$restartedThisLink, changes=${changes.keys.joinToString().ifEmpty { "none" }}")
            if (SESSION_PATH_FLAG !in changes && !(classic && !restartedThisLink)) return@collectLatest
            restartedThisLink = true
            log.add("Own voice: restarting the glasses' assistant to read the configuration again")
            log.add(if (restartGlassesAssistant(settings, log)) "Own voice: the glasses' assistant restarted" else "Own voice: the glasses would not restart their assistant; restart the glasses")
        }
    }
}

/**
 * Restart only the glasses' assistant, which reads MobileConfig afresh when it starts. It exits on any change of
 * live_caption_voice_id, so that voice goes to another value and straight back.
 */
suspend fun restartGlassesAssistant(settings: GlassesSettings, log: EventLog? = null): Boolean {
    // The value to put back must be the glasses' own, so read them first if this run has not.
    if (CAPTION_VOICE !in settings.state.value.stored) {
        log?.add("Own voice: reading the glasses' settings to find $CAPTION_VOICE")
        settings.read()
        if (withTimeoutOrNull(SETTINGS_MS) { settings.state.first { CAPTION_VOICE in it.stored } } == null) {
            log?.add("Own voice: $CAPTION_VOICE did not arrive in ${SETTINGS_MS / 1000} s; cannot restart the assistant")
            return false
        }
    }
    val was = settings.state.value.stored[CAPTION_VOICE].orEmpty()
    val other = if (was == "en_US.mai_03") "en_US.mai_01" else "en_US.mai_03"
    log?.add("Own voice: flipping $CAPTION_VOICE ($was -> $other -> $was) to restart the assistant")
    if (!settings.write(CAPTION_VOICE, other)) {
        log?.add("Own voice: the glasses would not take $CAPTION_VOICE = $other")
        return false
    }
    delay(1_500)
    val back = settings.write(CAPTION_VOICE, was)
    log?.add("Own voice: $CAPTION_VOICE is back to ${if (back) was else "nothing: the glasses would not take it"}")
    return back
}

private const val SETTLE_MS = 20_000L
private const val READ_MS = 5 * 60_000L
private const val WRITE_MS = 2 * 60_000L
private const val SETTINGS_MS = 20_000L

/** A configuration value that means true: a boolean, a number other than zero, or the word. */
private fun Any?.asBoolean(): Boolean = when (this) {
    true -> true
    is Boolean -> this
    is Number -> toLong() != 0L
    is String -> equals("true", ignoreCase = true) || toLongOrNull()?.let { it != 0L } == true
    else -> false
}
