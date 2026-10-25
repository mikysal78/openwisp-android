package it.ninuxnnxx.openwisp

import it.ninuxnnxx.openwisp.ui.adaptiveScale
import it.ninuxnnxx.openwisp.ui.splitLogLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartsTest {
    @Test
    fun adaptivePrefixLikeOpenWisp() {
        assertEquals(1.0 to " ms", adaptiveScale(5.0, " ms"))
        assertEquals(1.0 to "B", adaptiveScale(0.0, "adaptive_prefix+B"))
        assertEquals(1_000_000.0 to "KB", adaptiveScale(0.0002, "adaptive_prefix+B"))
        assertEquals(1000.0 to "MB", adaptiveScale(0.03, "adaptive_prefix+B"))
        assertEquals(1.0 to "Gbps", adaptiveScale(2.5, "adaptive_prefix+bps"))
        assertEquals(0.001 to "TB", adaptiveScale(4000.0, "adaptive_prefix+B"))
    }

    @Test
    fun netconsoleLines() {
        val (time, message) = splitLogLine("2026-10-23T04:25:29.027770+02:00 [ 1900.66] mt7915e: reset")
        assertTrue(time!!.matches(Regex("""\d\d/\d\d \d\d:\d\d:\d\d""")))
        assertEquals("[ 1900.66] mt7915e: reset", message)
        val plain = splitLogLine("niente data")
        assertNull(plain.first)
        assertEquals("niente data", plain.second)
    }
}
