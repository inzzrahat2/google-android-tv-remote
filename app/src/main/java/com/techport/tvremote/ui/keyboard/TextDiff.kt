package com.techport.tvremote.ui.keyboard

/** One step of a text edit, applied at the TV's cursor (assumed to sit at the end of what we sent). */
sealed interface TextOp {
    /** Press DEL [count] times. Each press removes one [BackspaceUnits] unit. */
    data class Delete(val count: Int) : TextOp

    /** Insert [text] at the cursor with a single text command. Never logged. */
    class Insert(val text: String) : TextOp {
        override fun equals(other: Any?) = other is Insert && other.text == text
        override fun hashCode() = text.hashCode()
        override fun toString() = "Insert(<${text.length} chars>)"
    }
}

/**
 * Turns "what the TV has" into "what the user typed" with as few commands as possible.
 *
 * - Appended text → one [TextOp.Insert].
 * - Text removed from the end → one [TextOp.Delete] with N presses.
 * - A change in the middle → delete back to the common prefix, then insert the rest.
 *
 * The common prefix is measured in [BackspaceUnits], never in UTF-16 chars, so a DEL press is
 * never expected to remove half a surrogate pair or half an emoji sequence.
 */
object TextDiff {
    fun diff(previous: String, current: String): List<TextOp> {
        if (previous == current) return emptyList()
        val prev = BackspaceUnits.split(previous)
        val cur = BackspaceUnits.split(current)
        var common = 0
        val max = minOf(prev.size, cur.size)
        while (common < max && prev[common] == cur[common]) common++

        val deletes = prev.size - common
        val insert = cur.subList(common, cur.size).joinToString("")
        return buildList {
            if (deletes > 0) add(TextOp.Delete(deletes))
            if (insert.isNotEmpty()) add(TextOp.Insert(insert))
        }
    }
}

/**
 * Splits text into the pieces one backspace press deletes on an Android TV.
 *
 * The TV handles DEL with Android's own backspace rule (`BaseKeyListener`), which is neither
 * "one UTF-16 char" nor "one grapheme cluster":
 * - a surrogate pair (most emoji, many CJK ideographs) is always one press, never two;
 * - emoji sequences are one press: ZWJ sequences (👩‍💻), skin-tone modifiers (👍🏽),
 *   variation selectors (❤️), keycaps (1️⃣), flag pairs (🇧🇩) and tag sequences;
 * - other combining marks (decomposed "é", Indic vowel signs) are deleted one code point per press.
 *
 * We mirror that rule. Over-counting would delete text the user never sent, which is worse than
 * the rare residue an unusual TV keyboard might leave, so ambiguous cases join the previous unit.
 */
object BackspaceUnits {
    fun split(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val units = ArrayList<StringBuilder>()
        var i = 0
        var joinNext = false
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val len = Character.charCount(cp)
            val piece = text.substring(i, i + len)
            val last = units.lastOrNull()
            val attach = last != null && (
                joinNext ||
                    isAttachingMark(cp) ||
                    cp == ZWJ ||
                    (isRegionalIndicator(cp) && isLoneRegionalIndicator(last))
                )
            if (attach) last!!.append(piece) else units.add(StringBuilder(piece))
            joinNext = cp == ZWJ
            i += len
        }
        return units.map { it.toString() }
    }

    fun count(text: String): Int = split(text).size

    /** [text] with its last [n] units removed. */
    fun dropLast(text: String, n: Int): String {
        if (n <= 0) return text
        val units = split(text)
        return units.subList(0, (units.size - n).coerceAtLeast(0)).joinToString("")
    }

    private const val ZWJ = 0x200D

    private fun isAttachingMark(cp: Int) =
        cp == 0xFE0E || cp == 0xFE0F || // variation selectors (text / emoji presentation)
            cp in 0x1F3FB..0x1F3FF || // skin-tone modifiers
            cp == 0x20E3 || // combining enclosing keycap
            cp in 0xE0020..0xE007F // tag characters (subdivision flags)

    private fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF

    /** A unit that is exactly one regional indicator, waiting for its pair. */
    private fun isLoneRegionalIndicator(unit: StringBuilder): Boolean =
        unit.length == 2 && Character.codePointCount(unit, 0, unit.length) == 1 &&
            isRegionalIndicator(Character.codePointAt(unit, 0))
}
