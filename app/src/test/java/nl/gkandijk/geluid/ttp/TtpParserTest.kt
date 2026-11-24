package nl.gkandijk.geluid.ttp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class TtpParserTest {
    @Test
    fun okWithValue() {
        val line = "+OK \"value\":-12.500000"
        assertTrue(TtpParser.isResponse(line))
        assertTrue(TtpParser.isOk(line))
        assertEquals(-12.5f, TtpParser.toFloat(TtpParser.value(line))!!, 0.001f)
    }

    @Test
    fun errorIsResponse() {
        val line = "-ERR address not found: {\"deviceId\":0 \"classCode\":0 \"instanceNum\":0}"
        assertTrue(TtpParser.isResponse(line))
        assertFalse(TtpParser.isOk(line))
    }

    @Test
    fun echoIsNotResponse() {
        assertFalse(TtpParser.isResponse("Level1 set level 1 -10.0"))
        assertFalse(TtpParser.isResponse("-10"))
    }

    @Test
    fun publishBool() {
        val p = TtpParser.publish("! \"publishToken\":\"s3\" \"value\":true")
        assertEquals("s3" to "true", p)
        assertEquals(true, TtpParser.toBool(p!!.second))
    }

    @Test
    fun publishNumber() {
        val p = TtpParser.publish("! \"publishToken\":\"s0\" \"value\":-100.000000")
        assertEquals(-100f, TtpParser.toFloat(p!!.second)!!, 0.001f)
    }

    @Test
    fun notPublish() {
        assertNull(TtpParser.publish("+OK"))
    }

    @Test
    fun telnetNegotiationIsStripped() {
        val iac = byteArrayOf(
            0xFF.toByte(), 0xFD.toByte(), 0x18, // IAC DO TERMINAL-TYPE
            0xFF.toByte(), 0xFB.toByte(), 0x01, // IAC WILL ECHO
        )
        val data = iac + "Welcome\r\n+OK\r\n".toByteArray()
        val replies = mutableListOf<ByteArray>()
        val r = LineReader(ByteArrayInputStream(data), telnet = true) { replies.add(it) }
        assertEquals("Welcome", r.readLine())
        assertEquals("+OK", r.readLine())
        assertNull(r.readLine())
        assertEquals(2, replies.size)
        assertEquals(0xFC.toByte(), replies[0][1]) // WONT
        assertEquals(0xFE.toByte(), replies[1][1]) // DONT
    }
}
