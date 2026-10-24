package com.techport.tvremote.ui.keyboard

import com.techport.tvremote.domain.model.RemoteCommand
import com.techport.tvremote.domain.model.RemoteKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Mirrors the phone's text field onto the TV with as few commands as possible.
 *
 * Throttled with a leading edge: the first change goes out immediately, and further changes
 * inside the next [windowMs] are merged into one diff sent when the window closes. So a single
 * keystroke is never delayed, while a burst (fast typing, autocorrect, gesture typing) becomes
 * one text command.
 *
 * Long runs of DEL (e.g. "Clear") are paced at [maxKeysPerStep] presses every
 * [bulkIntervalMs], because the connection's command queue is bounded and drops the oldest
 * command when full; a dropped DEL would leave the TV out of sync.
 *
 * Not thread-safe: call it from one thread (the main thread, via viewModelScope).
 */
class TextSyncer(
    private val scope: CoroutineScope,
    private val send: (RemoteCommand) -> Unit,
    private val windowMs: Long = 40,
    private val maxKeysPerStep: Int = 4,
    private val bulkIntervalMs: Long = 80,
) {
    /** What the TV has received from us (as far as we know). */
    var sent: String = ""
        private set

    private var target: String = ""
    private var cooldown: Job? = null

    val hasPending: Boolean get() = target != sent

    /** The phone's text changed to [text]. */
    fun update(text: String) {
        target = text
        if (cooldown?.isActive == true) return
        val more = step(maxKeysPerStep)
        startCooldown(if (more) bulkIntervalMs else windowMs)
    }

    /** Sends everything pending right now, e.g. before a raw key that must follow the text. */
    fun flush() {
        cooldown?.cancel()
        cooldown = null
        while (step(Int.MAX_VALUE)) Unit
    }

    /** Forgets pending work and declares that the TV holds [text] (new field, reconnect, submit). */
    fun reset(text: String = "") {
        cooldown?.cancel()
        cooldown = null
        sent = text
        target = text
    }

    private fun startCooldown(delayMs: Long) {
        cooldown = scope.launch {
            delay(delayMs)
            if (target != sent) {
                val more = step(maxKeysPerStep)
                startCooldown(if (more) bulkIntervalMs else windowMs)
            }
        }
    }

    /** Sends at most [budget] commands toward [target]. Returns true if work remains. */
    private fun step(budget: Int): Boolean {
        var left = budget
        for (op in TextDiff.diff(sent, target)) {
            if (left <= 0) return true
            when (op) {
                is TextOp.Delete -> {
                    val presses = minOf(op.count, left)
                    repeat(presses) { send(RemoteCommand.Key(RemoteKey.DEL)) }
                    sent = BackspaceUnits.dropLast(sent, presses)
                    left -= presses
                    if (presses < op.count) return true
                }
                is TextOp.Insert -> {
                    send(RemoteCommand.Text(op.text))
                    sent += op.text
                    left--
                }
            }
        }
        return target != sent
    }
}
