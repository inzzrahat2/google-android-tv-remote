package com.techport.tvremote.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Test

class TextDiffTest {

    private fun insert(text: String) = TextOp.Insert(text)
    private fun delete(n: Int) = TextOp.Delete(n)

    @Test
    fun identicalTextNeedsNothing() {
        assertEquals(emptyList<TextOp>(), TextDiff.diff("hello", "hello"))
        assertEquals(emptyList<TextOp>(), TextDiff.diff("", ""))
    }

    @Test
    fun appendedTextIsOneInsert() {
        assertEquals(listOf(insert("lo world")), TextDiff.diff("hel", "hello world"))
        assertEquals(listOf(insert("abc")), TextDiff.diff("", "abc"))
    }

    @Test
    fun deletionAtEndIsDelPresses() {
        assertEquals(listOf(delete(3)), TextDiff.diff("hello", "he"))
        assertEquals(listOf(delete(5)), TextDiff.diff("hello", ""))
    }

    @Test
    fun middleEditDeletesBackToCommonPrefixThenInserts() {
        // "hello" -> "helpo": common prefix "hel", remove "lo", type "po".
        assertEquals(listOf(delete(2), insert("po")), TextDiff.diff("hello", "helpo"))
        // Autocorrect replacing a word.
        assertEquals(listOf(delete(2), insert("he ")), TextDiff.diff("teh", "the "))
    }

    @Test
    fun surrogatePairIsOneDelPress() {
        assertEquals(1, BackspaceUnits.count("😀"))
        assertEquals(listOf(delete(1)), TextDiff.diff("a😀", "a"))
        assertEquals(listOf(delete(2)), TextDiff.diff("x😀😀", "x"))
        // CJK Extension B ideograph outside the BMP.
        assertEquals(listOf(delete(1)), TextDiff.diff("𠀀", ""))
    }

    @Test
    fun emojiSequencesAreOneDelPress() {
        assertEquals(1, BackspaceUnits.count("👩‍💻")) // ZWJ sequence
        assertEquals(1, BackspaceUnits.count("👨‍👩‍👧‍👦")) // family, 7 code points
        assertEquals(1, BackspaceUnits.count("👍🏽")) // skin tone
        assertEquals(1, BackspaceUnits.count("❤️")) // variation selector
        assertEquals(1, BackspaceUnits.count("1️⃣")) // keycap
        assertEquals(2, BackspaceUnits.count("🇧🇩🇺🇸")) // two flags
        // England flag: black flag + tag characters.
        assertEquals(1, BackspaceUnits.count("🏴󠁧󠁢󠁥󠁮󠁧󠁿"))
        assertEquals(listOf(delete(1)), TextDiff.diff("hi 👩‍💻", "hi "))
    }

    @Test
    fun combiningMarksAreDeletedPerCodePoint() {
        // Android deletes "e" + U+0301 with two presses.
        assertEquals(2, BackspaceUnits.count("é"))
    }

    @Test
    fun changingAnEmojiModifierReplacesTheWholeEmoji() {
        // The TV deletes 👍🏽 in one press, so "👍" is never treated as a shared prefix.
        assertEquals(listOf(delete(1), insert("👍🏽")), TextDiff.diff("👍", "👍🏽"))
        assertEquals(listOf(delete(1), insert("👍")), TextDiff.diff("👍🏽", "👍"))
    }

    @Test
    fun dropLastRemovesWholeUnits() {
        assertEquals("ab", BackspaceUnits.dropLast("ab👩‍💻", 1))
        assertEquals("", BackspaceUnits.dropLast("ab", 5))
        assertEquals("ab", BackspaceUnits.dropLast("ab", 0))
    }

    @Test
    fun insertNeverPrintsItsText() {
        assertEquals("Insert(<6 chars>)", insert("secret").toString())
    }
}
