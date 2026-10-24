package com.techport.tvremote.ui.touchpad

import com.techport.tvremote.domain.model.RemoteKey
import kotlin.math.abs
import kotlin.math.sign

/**
 * Turns finger travel on the touchpad into discrete D-pad steps.
 *
 * The protocol has no pointer events, so "moving the cursor" means moving the TV's focus one
 * item per step. Rules:
 * - **Axis lock:** the first [lockPx] of travel decides horizontal or vertical. Only that axis
 *   produces steps, so a slightly diagonal swipe never fires both LEFT and DOWN.
 * - **Re-lock:** sustained travel across the locked axis (one full step) switches axes, so the
 *   user can go right, then down, without lifting the finger.
 * - **Step size:** one step every [stepPx] of travel. Reversing direction drops the residual so
 *   the first step back needs no extra travel.
 * - **Acceleration:** fast flicks above [accelThresholdPxPerMs] count up to [maxAcceleration]×
 *   their travel. Velocity is smoothed so one noisy event cannot trigger a burst.
 * - **Cap:** at most [maxStepsPerEvent] steps per pointer event; excess travel is discarded
 *   rather than queued, so a stalled frame never turns into a backlog of key presses.
 *
 * Pure and allocation-free: [feed] runs on every pointer event.
 */
class GestureStepper(
    private val stepPx: Float,
    private val accelThresholdPxPerMs: Float,
    private val maxStepsPerEvent: Int = DEFAULT_MAX_STEPS_PER_EVENT,
    private val maxAcceleration: Float = DEFAULT_MAX_ACCELERATION,
    private val lockPx: Float = stepPx * LOCK_FRACTION,
) {
    enum class Axis { NONE, HORIZONTAL, VERTICAL }

    init {
        require(stepPx > 0f) { "stepPx must be positive" }
        require(maxStepsPerEvent >= 1) { "maxStepsPerEvent must be >= 1" }
    }

    var axis: Axis = Axis.NONE
        private set

    /** Travel along the locked axis not yet turned into a step (signed). Before locking: x travel. */
    private var main = 0f

    /** Travel across the locked axis, used for re-locking. Before locking: y travel. */
    private var cross = 0f

    /** Smoothed speed along the locked axis, px/ms. */
    private var velocity = 0f

    fun reset() {
        axis = Axis.NONE
        main = 0f
        cross = 0f
        velocity = 0f
    }

    /**
     * Feeds one pointer delta. Returns the signed number of steps along [axis]
     * (positive = right/down); 0 when no step is due. Use [keyFor] to map it to a key.
     */
    fun feed(dx: Float, dy: Float, dtMs: Long): Int {
        if (axis == Axis.NONE) {
            main += dx
            cross += dy
            val ax = abs(main)
            val ay = abs(cross)
            if (ax < lockPx && ay < lockPx) return 0
            if (ax >= ay) {
                axis = Axis.HORIZONTAL
            } else {
                axis = Axis.VERTICAL
                main = cross
            }
            cross = 0f
            return takeSteps()
        }

        val dMain = if (axis == Axis.HORIZONTAL) dx else dy
        val dCross = if (axis == Axis.HORIZONTAL) dy else dx

        if (abs(dCross) > abs(dMain)) cross += dCross else cross *= CROSS_DECAY
        if (abs(cross) >= stepPx) {
            axis = if (axis == Axis.HORIZONTAL) Axis.VERTICAL else Axis.HORIZONTAL
            main = cross
            cross = 0f
            velocity = 0f
            return takeSteps()
        }

        if (dtMs > 0) {
            val instant = abs(dMain) / dtMs
            velocity = velocity * (1f - VELOCITY_SMOOTHING) + instant * VELOCITY_SMOOTHING
        }
        if (dMain != 0f && main != 0f && sign(dMain) != sign(main)) main = 0f
        main += dMain * acceleration()
        return takeSteps()
    }

    /** The key for a non-zero [feed] result, or null when no axis is locked. */
    fun keyFor(steps: Int): RemoteKey? = when {
        steps == 0 -> null
        axis == Axis.HORIZONTAL -> if (steps > 0) RemoteKey.DPAD_RIGHT else RemoteKey.DPAD_LEFT
        axis == Axis.VERTICAL -> if (steps > 0) RemoteKey.DPAD_DOWN else RemoteKey.DPAD_UP
        else -> null
    }

    private fun acceleration(): Float {
        if (velocity <= accelThresholdPxPerMs) return 1f
        val extra = (velocity / accelThresholdPxPerMs - 1f) * ACCELERATION_GAIN
        return (1f + extra).coerceAtMost(maxAcceleration)
    }

    private fun takeSteps(): Int {
        val count = (abs(main) / stepPx).toInt()
        if (count == 0) return 0
        val direction = sign(main)
        // Keep only the sub-step remainder: travel beyond the cap is dropped, not queued.
        main -= direction * count * stepPx
        cross = 0f
        return (direction * count.coerceAtMost(maxStepsPerEvent)).toInt()
    }

    companion object {
        /** Finger travel per D-pad step at sensitivity 1.0. */
        const val BASE_STEP_DP = 40f

        /** Speed (dp/ms) above which acceleration starts; 1 dp/ms = 1000 dp/s. */
        const val ACCEL_THRESHOLD_DP_PER_MS = 1.0f

        const val MIN_SENSITIVITY = 0.5f
        const val MAX_SENSITIVITY = 2.0f
        const val DEFAULT_MAX_STEPS_PER_EVENT = 3
        const val DEFAULT_MAX_ACCELERATION = 2.5f
        private const val LOCK_FRACTION = 0.3f
        private const val CROSS_DECAY = 0.5f
        private const val VELOCITY_SMOOTHING = 0.4f
        private const val ACCELERATION_GAIN = 0.6f

        /** Higher [sensitivity] means less travel per step. Out-of-range values are clamped. */
        fun forDensity(density: Float, sensitivity: Float): GestureStepper {
            val s = sensitivity.coerceIn(MIN_SENSITIVITY, MAX_SENSITIVITY)
            return GestureStepper(
                stepPx = BASE_STEP_DP * density / s,
                accelThresholdPxPerMs = ACCEL_THRESHOLD_DP_PER_MS * density,
            )
        }
    }
}

/**
 * Limits haptic ticks during a fast drag: every step still sends a key, but the phone only
 * vibrates if [minIntervalMs] passed since the last tick, so a flick doesn't buzz continuously.
 */
class TickThrottle(private val minIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS) {
    private var lastTickAt = Long.MIN_VALUE

    fun tryTick(nowMs: Long): Boolean {
        if (lastTickAt != Long.MIN_VALUE && nowMs - lastTickAt < minIntervalMs) return false
        lastTickAt = nowMs
        return true
    }

    fun reset() {
        lastTickAt = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_MIN_INTERVAL_MS = 45L
    }
}

/** Classifies a completed swipe (used by [swipeNavigation]) as a single directional key. */
object SwipeClassifier {
    /** The longer axis must be at least this many times the shorter one. */
    const val DOMINANCE = 1.5f
    const val MAX_DURATION_MS = 800L

    /**
     * Returns the key for a swipe that travelled ([dx], [dy]) in [durationMs], or null if it was
     * too short, too slow (a hesitant drag, not a swipe), or too diagonal to be unambiguous.
     */
    fun classify(dx: Float, dy: Float, durationMs: Long, minDistancePx: Float): RemoteKey? {
        if (durationMs > MAX_DURATION_MS) return null
        val ax = abs(dx)
        val ay = abs(dy)
        return when {
            ax >= minDistancePx && ax >= ay * DOMINANCE -> if (dx > 0) RemoteKey.DPAD_RIGHT else RemoteKey.DPAD_LEFT
            ay >= minDistancePx && ay >= ax * DOMINANCE -> if (dy > 0) RemoteKey.DPAD_DOWN else RemoteKey.DPAD_UP
            else -> null
        }
    }
}
