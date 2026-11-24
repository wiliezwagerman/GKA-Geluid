package nl.gkandijk.geluid.ttp

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class Protocol { TELNET, SSH }

data class ConnConfig(
    val host: String = "",
    val protocol: Protocol = Protocol.TELNET,
    val port: Int = 23,
    val user: String = "default",
    val password: String = "",
)

enum class ConnState { DISCONNECTED, CONNECTING, CONNECTED }

/**
 * Een waarde waarop de app zich abonneert: bijvoorbeeld Level1 / level / kanaal 3.
 * attr "meter" is het level van een AudioMeter-blok (in TTP heet dat ook "level").
 */
data class Sub(val tag: String, val attr: String, val ch: Int) {
    val key: String get() = key(tag, attr, ch)
    val isMeter: Boolean get() = attr == METER

    companion object {
        const val METER = "meter"
        fun key(tag: String, attr: String, ch: Int) = "$tag|$attr|$ch"
    }
}

/**
 * Houdt één verbinding met de Tesira open, abonneert zich op waarden en
 * verstuurt bedieningscommando's. Verbindt automatisch opnieuw bij storingen.
 */
class TesiraClient(private val scope: CoroutineScope) {

    private class Cmd(val text: String, val result: CompletableDeferred<String>?)

    private val _state = MutableStateFlow(ConnState.DISCONNECTED)
    val state: StateFlow<ConnState> = _state.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    /** Actuele waarden per sleutel (tag|attr|ch), als tekst zoals de Tesira ze stuurt. */
    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    /** Bereik (min, max) van level-kanalen zoals de Tesira ze opgeeft. */
    private val _ranges = MutableStateFlow<Map<String, Pair<Float, Float>>>(emptyMap())
    val ranges: StateFlow<Map<String, Pair<Float, Float>>> = _ranges.asStateFlow()

    /** Meterstanden in dB, apart gehouden omdat ze vaak veranderen. */
    private val _meters = MutableStateFlow<Map<String, Float>>(emptyMap())
    val meters: StateFlow<Map<String, Float>> = _meters.asStateFlow()

    private val meterKeys = ConcurrentHashMap.newKeySet<String>()

    private var job: Job? = null

    @Volatile
    private var transport: Transport? = null

    @Volatile
    private var outbox: Channel<Cmd>? = null

    private val tokens = ConcurrentHashMap<String, String>()
    private val pendingLevels = ConcurrentHashMap<String, Float>()
    private val levelSignal = Channel<Unit>(Channel.CONFLATED)

    fun start(config: ConnConfig, subs: List<Sub>) {
        stop()
        if (config.host.isBlank()) return
        job = scope.launch(Dispatchers.IO) { runLoop(config, subs.distinct()) }
    }

    fun stop() {
        job?.cancel()
        job = null
        transport?.close()
        transport = null
        outbox = null
        _state.value = ConnState.DISCONNECTED
    }

    private suspend fun runLoop(config: ConnConfig, subs: List<Sub>) {
        while (scope.isActive) {
            _state.value = ConnState.CONNECTING
            val t: Transport = when (config.protocol) {
                Protocol.TELNET -> TelnetTransport(config.host, config.port)
                Protocol.SSH -> SshTransport(config.host, config.port, config.user, config.password)
            }
            transport = t
            val responses = Channel<String>(Channel.UNLIMITED)
            val box = Channel<Cmd>(Channel.UNLIMITED)
            try {
                t.open()
                outbox = box
                coroutineScope {
                    // Sluit de verbinding zodra deze scope stopt, zodat de lezer loskomt.
                    launch {
                        try {
                            awaitCancellation()
                        } finally {
                            t.close()
                        }
                    }
                    // Lezer
                    launch {
                        while (true) {
                            val line = t.readLine() ?: throw IOException("Verbinding verbroken")
                            handleLine(line, responses)
                        }
                    }
                    // Schrijver: één commando tegelijk, wacht op het antwoord.
                    launch {
                        for (cmd in box) {
                            while (responses.tryReceive().isSuccess) { /* oude antwoorden weg */ }
                            t.write(cmd.text + "\n")
                            val resp = withTimeoutOrNull(4000) { responses.receive() }
                                ?: throw IOException("Geen antwoord op: ${cmd.text}")
                            if (!TtpParser.isOk(resp)) Log.w(TAG, "${cmd.text} -> $resp")
                            cmd.result?.complete(resp)
                        }
                    }
                    // Faderstanden: alleen de laatste waarde per fader versturen.
                    launch {
                        for (signal in levelSignal) {
                            for (key in pendingLevels.keys.toList()) {
                                val v = pendingLevels.remove(key) ?: continue
                                val parts = key.split('|')
                                box.trySend(Cmd("${parts[0]} set level ${parts[2]} ${fmt(v)}", null))
                            }
                            delay(60)
                        }
                    }
                    // Initialisatie
                    launch {
                        delay(700) // banner en Telnet-onderhandeling afwachten
                        subscribeAll(box, subs)
                        _state.value = ConnState.CONNECTED
                        _lastError.value = null
                        while (true) {
                            delay(25_000)
                            request(box, "SESSION get aliases")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.w(TAG, "Verbinding mislukt: ${e.message}")
                _lastError.value = e.message ?: e.javaClass.simpleName
            } finally {
                outbox = null
                box.close()
                t.close()
            }
            _state.value = ConnState.DISCONNECTED
            delay(3000)
        }
    }

    private suspend fun subscribeAll(box: Channel<Cmd>, subs: List<Sub>) {
        tokens.clear()
        meterKeys.clear()
        subs.forEachIndexed { i, sub ->
            val token = "s$i"
            tokens[token] = sub.key
            if (sub.isMeter) {
                meterKeys.add(sub.key)
                request(box, "${sub.tag} subscribe level ${sub.ch} $token 150")
            } else {
                request(box, "${sub.tag} subscribe ${sub.attr} ${sub.ch} $token 100")
            }
            if (sub.attr == "level") {
                val min = TtpParser.toFloat(TtpParser.value(request(box, "${sub.tag} get minLevel ${sub.ch}") ?: ""))
                val max = TtpParser.toFloat(TtpParser.value(request(box, "${sub.tag} get maxLevel ${sub.ch}") ?: ""))
                if (min != null && max != null && max > min) {
                    _ranges.update { it + (sub.key to (min to max)) }
                }
            }
        }
    }

    private fun handleLine(line: String, responses: Channel<String>) {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.startsWith("!")) {
            val (token, value) = TtpParser.publish(trimmed) ?: return
            val key = tokens[token] ?: return
            if (key in meterKeys) {
                val db = value.toFloatOrNull() ?: return
                _meters.update { it + (key to db) }
            } else {
                _values.update { it + (key to value) }
            }
        } else if (TtpParser.isResponse(trimmed)) {
            responses.trySend(trimmed)
        }
        // Overige regels (welkomsttekst, echo van commando's) negeren.
    }

    private suspend fun request(box: Channel<Cmd>, text: String): String? {
        val d = CompletableDeferred<String>()
        if (box.trySend(Cmd(text, d)).isFailure) return null
        return withTimeoutOrNull(6000) { d.await() }
    }

    /** Stuurt een commando en wacht op het antwoord. Null als er geen verbinding is. */
    suspend fun request(text: String): String? {
        val box = outbox ?: return null
        return request(box, text)
    }

    fun setLevel(tag: String, ch: Int, value: Float) {
        val key = Sub.key(tag, "level", ch)
        _values.update { it + (key to fmt(value)) }
        pendingLevels[key] = value
        levelSignal.trySend(Unit)
    }

    fun setMute(tag: String, ch: Int, muted: Boolean) {
        val key = Sub.key(tag, "mute", ch)
        _values.update { it + (key to muted.toString()) }
        outbox?.trySend(Cmd("$tag set mute $ch $muted", null))
    }

    suspend fun recallPreset(id: Int): Boolean =
        request("DEVICE recallPreset $id")?.let { TtpParser.isOk(it) } ?: false

    private fun fmt(v: Float) = String.format(Locale.US, "%.1f", v)

    private companion object {
        const val TAG = "TesiraClient"
    }
}
