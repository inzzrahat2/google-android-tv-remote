package com.techport.tvremote.ui.touchpad

import com.techport.tvremote.domain.model.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureStepperTest {

    /** 10 px per step, acceleration only above 1 px/ms. */
    private fun stepper(maxSteps: Int = 3) = GestureStepper(stepPx = 10f, accelThresholdPxPerMs = 1f, maxStepsPerEvent = maxSteps)

    /** Feeds [count] slow moves of ([dx], [dy]) 16 ms apart and collects emitted keys. */
    private fun GestureStepper.drag(dx: Float, dy: Float, count: Int, dtMs: Long = 16): List<RemoteKey> {
        val keys = mutableListOf<RemoteKey>()
        repeat(count) {
            val steps = feed(dx, dy, dtMs)
            val key = keyFor(steps)
            if (key != null) repeat(kotlin.math.abs(steps)) { keys += key }
        }
        return keys
    }

    @Test
    fun slowHorizontalDragStepsEveryStepDistance() {
        val s = stepper()
        // 2 px per event at 16 ms = 0.125 px/ms: no acceleration. 50 px → 5 steps.
        assertEquals(List(5) { RemoteKey.DPAD_RIGHT }, s.drag(2f, 0f, 25))
        assertEquals(GestureStepper.Axis.HORIZONTAL, s.axis)
    }

    @Test
    fun movementBelowLockThresholdEmitsNothing() {
        val s = stepper()
        assertEquals(0, s.feed(1f, 1f, 16))
        assertEquals(GestureStepper.Axis.NONE, s.axis)
        assertNull(s.keyFor(0))
    }

    @Test
    fun dominantAxisLocksAndIgnoresMinorAxis() {
        val s = stepper()
        // Mostly upward with some sideways drift: only UP steps.
        val keys = s.drag(dx = 0.8f, dy = -2f, count = 25)
        assertTrue(keys.isNotEmpty())
        assertTrue(keys.all { it == RemoteKey.DPAD_UP })
    }

    @Test
    fun perfectDiagonalNeverEmitsTwoAxesInOneEvent() {
        val s = stepper()
        repeat(50) {
            val steps = s.feed(3f, 3f, 16)
            // Each result is along exactly one axis by construction; check the key matches the axis.
            val key = s.keyFor(steps)
            if (key != null) {
                val horizontal = key == RemoteKey.DPAD_LEFT || key == RemoteKey.DPAD_RIGHT
                assertEquals(s.axis == GestureStepper.Axis.HORIZONTAL, horizontal)
            }
        }
    }

    @Test
    fun sustainedCrossMovementRelocksAxis() {
        val s = stepper()
        val right = s.drag(2f, 0f, 10)
        assertTrue(right.all { it == RemoteKey.DPAD_RIGHT })
        val down = s.drag(0f, 2f, 20)
        assertTrue(down.isNotEmpty())
        assertEquals(GestureStepper.Axis.VERTICAL, s.axis)
        assertTrue(down.all { it == RemoteKey.DPAD_DOWN })
    }

    @Test
    fun reversingDirectionDropsResidual() {
        val s = stepper()
        s.drag(2f, 0f, 9) // 18 px: one step, 8 px residual
        // Going back 10 px must produce one LEFT immediately, not after 18 px.
        assertEquals(listOf(RemoteKey.DPAD_LEFT), s.drag(-2f, 0f, 5))
    }

    @Test
    fun fastFlickAcceleratesButIsCappedPerEvent() {
        val slow = stepper(maxSteps = 100)
        val slowKeys = slow.drag(4f, 0f, 25, dtMs = 16) // 0.25 px/ms
        val fast = stepper(maxSteps = 100)
        val fastKeys = fast.drag(40f, 0f, 10, dtMs = 8) // 5 px/ms, same 100 px total
        assertEquals(10, slowKeys.size)
        assertTrue("fast flick should go further", fastKeys.size > slowKeys.size)

        val capped = stepper(maxSteps = 3)
        val steps = capped.feed(500f, 0f, 1)
        assertEquals(3, steps)
        // Excess travel is discarded, not queued: the next tiny move emits nothing.
        assertEquals(0, capped.feed(1f, 0f, 16))
    }

    @Test
    fun resetClearsAxisAndResidual() {
        val s = stepper()
        s.drag(2f, 0f, 4) // locked horizontal, 8 px residual
        s.reset()
        assertEquals(GestureStepper.Axis.NONE, s.axis)
        assertEquals(emptyList<RemoteKey>(), s.drag(0f, 2f, 4)) // 8 px: no step yet
        assertEquals(GestureStepper.Axis.VERTICAL, s.axis)
    }

    @Test
    fun higherSensitivityMeansShorterSteps() {
        val low = GestureStepper.forDensity(density = 1f, sensitivity = 0.5f)
        val high = GestureStepper.forDensity(density = 1f, sensitivity = 2f)
        val lowKeys = low.drag(2f, 0f, 100)
        val highKeys = high.drag(2f, 0f, 100)
        assertTrue(highKeys.size > lowKeys.size)
        // Out-of-range sensitivity is clamped rather than producing absurd step sizes.
        val clamped = GestureStepper.forDensity(density = 1f, sensitivity = 50f)
        assertEquals(highKeys.size, clamped.drag(2f, 0f, 100).size)
    }

    @Test
    fun tickThrottleLimitsRate() {
        val t = TickThrottle(minIntervalMs = 40)
        assertTrue(t.tryTick(0))
        assertFalse(t.tryTick(20))
        assertTrue(t.tryTick(45))
        t.reset()
        assertTrue(t.tryTick(46))
    }
}
