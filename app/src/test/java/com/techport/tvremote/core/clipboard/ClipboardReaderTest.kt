package com.techport.tvremote.core.clipboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardReaderTest {

    private var reads = 0
    private var clip: String? = "hello"
    private var enabled = true
    private val reader = ClipboardReader({ reads++; clip }, { enabled })

    @Test
    fun disabledNeverTouchesTheClipboard() {
        enabled = false
        assertSame(ClipboardRead.Disabled, reader.read())
        assertEquals(0, reads)
    }

    @Test
    fun enabledReturnsText() {
        val read = reader.read() as ClipboardRead.Text
        assertEquals("hello", read.text)
        assertFalse(read.truncated)
        assertEquals(1, reads)
    }

    @Test
    fun emptyOrBlankOrFailingClipboardIsEmpty() {
        clip = null
        assertSame(ClipboardRead.Empty, reader.read())
        clip = "  \n "
        assertSame(ClipboardRead.Empty, reader.read())
        val failing = ClipboardReader({ throw SecurityException("not focused") }, { true })
        assertSame(ClipboardRead.Empty, failing.read())
    }

    @Test
    fun lineBreaksBecomeSpaces() {
        clip = "  line one\r\nline two\n"
        assertEquals("line one line two", (reader.read() as ClipboardRead.Text).text)
    }

    @Test
    fun longTextIsTruncatedWithoutSplittingASurrogatePair() {
        clip = "a".repeat(ClipboardReader.MAX_CHARS - 1) + "😀" + "tail"
        val read = reader.read() as ClipboardRead.Text
        assertTrue(read.truncated)
        assertEquals(ClipboardReader.MAX_CHARS - 1, read.text.length)
        assertFalse(Character.isHighSurrogate(read.text.last()))
    }

    @Test
    fun toStringNeverContainsTheText() {
        clip = "password123"
        assertFalse(reader.read().toString().contains("password"))
    }
}
