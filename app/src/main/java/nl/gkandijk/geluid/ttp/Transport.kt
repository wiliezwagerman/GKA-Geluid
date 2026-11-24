package nl.gkandijk.geluid.ttp

import com.jcraft.jsch.ChannelShell
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/** Een tekstverbinding met de Tesira: Telnet of SSH. */
interface Transport {
    /** Opent de verbinding (blokkerend). */
    fun open()

    /** Leest één regel (blokkerend). Geeft null als de verbinding dicht is. */
    fun readLine(): String?

    fun write(text: String)

    fun close()
}

/** Leest regels uit een stroom en filtert Telnet-onderhandeling (IAC) eruit. */
internal class LineReader(
    stream: InputStream,
    private val telnet: Boolean,
    private val reply: (ByteArray) -> Unit,
) {
    private val input = BufferedInputStream(stream)
    private val buf = StringBuilder()

    fun readLine(): String? {
        while (true) {
            val b = input.read()
            if (b < 0) {
                if (buf.isEmpty()) return null
                return buf.toString().also { buf.setLength(0) }
            }
            if (telnet && b == IAC) {
                handleIac()
                continue
            }
            when (b) {
                '\n'.code -> return buf.toString().also { buf.setLength(0) }
                '\r'.code, 0 -> {}
                else -> buf.append(b.toChar())
            }
        }
    }

    private fun handleIac() {
        when (val cmd = input.read()) {
            DO, DONT, WILL, WONT -> {
                val opt = input.read()
                if (opt < 0) return
                // Weiger alle opties: de Tesira werkt dan als kale tekstverbinding.
                when (cmd) {
                    DO -> reply(byteArrayOf(IAC.toByte(), WONT.toByte(), opt.toByte()))
                    WILL -> reply(byteArrayOf(IAC.toByte(), DONT.toByte(), opt.toByte()))
                }
            }
            SB -> {
                // Sla subonderhandeling over tot IAC SE.
                var prev = -1
                while (true) {
                    val x = input.read()
                    if (x < 0 || (prev == IAC && x == SE)) break
                    prev = x
                }
            }
            IAC -> buf.append(IAC.toChar())
            else -> if (cmd < 0) return
        }
    }

    private companion object {
        const val IAC = 255
        const val DONT = 254
        const val DO = 253
        const val WONT = 252
        const val WILL = 251
        const val SB = 250
        const val SE = 240
    }
}

class TelnetTransport(private val host: String, private val port: Int) : Transport {
    private var socket: Socket? = null
    private var out: OutputStream? = null
    private var reader: LineReader? = null

    override fun open() {
        val s = Socket()
        s.tcpNoDelay = true
        s.keepAlive = true
        s.connect(InetSocketAddress(host, port), 5000)
        socket = s
        out = s.getOutputStream()
        reader = LineReader(s.getInputStream(), telnet = true) { bytes -> writeBytes(bytes) }
    }

    override fun readLine(): String? = reader?.readLine()

    override fun write(text: String) = writeBytes(text.toByteArray(Charsets.UTF_8))

    private fun writeBytes(bytes: ByteArray) {
        val o = out ?: throw IOException("Niet verbonden")
        synchronized(this) {
            o.write(bytes)
            o.flush()
        }
    }

    override fun close() {
        try {
            socket?.close()
        } catch (_: Exception) {
        }
    }
}

class SshTransport(
    private val host: String,
    private val port: Int,
    private val user: String,
    private val password: String,
) : Transport {
    private var session: Session? = null
    private var channel: ChannelShell? = null
    private var out: OutputStream? = null
    private var reader: LineReader? = null

    override fun open() {
        val jsch = JSch()
        val s = jsch.getSession(user, host, port)
        if (password.isNotEmpty()) s.setPassword(password)
        // Tesira staat in een afgesloten netwerk; host-key niet controleren.
        s.setConfig("StrictHostKeyChecking", "no")
        s.setConfig("PreferredAuthentications", "password,keyboard-interactive,none")
        s.serverAliveInterval = 15000
        s.connect(8000)
        val ch = s.openChannel("shell") as ChannelShell
        val input = ch.inputStream
        out = ch.outputStream
        ch.connect(5000)
        session = s
        channel = ch
        reader = LineReader(input, telnet = false) {}
    }

    override fun readLine(): String? = reader?.readLine()

    override fun write(text: String) {
        val o = out ?: throw IOException("Niet verbonden")
        synchronized(this) {
            o.write(text.toByteArray(Charsets.UTF_8))
            o.flush()
        }
    }

    override fun close() {
        try {
            channel?.disconnect()
        } catch (_: Exception) {
        }
        try {
            session?.disconnect()
        } catch (_: Exception) {
        }
    }
}
