package com.techport.tvremote.ui.touchpad

import com.techport.tvremote.domain.model.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeClassifierTest {
    private val min = 100f

    @Test
    fun clearSwipesMapToDirections() {
        assertEquals(RemoteKey.DPAD_RIGHT, SwipeClassifier.classify(150f, 20f, 200, min))
        assertEquals(RemoteKey.DPAD_LEFT, SwipeClassifier.classify(-150f, -20f, 200, min))
        assertEquals(RemoteKey.DPAD_DOWN, SwipeClassifier.classify(10f, 180f, 200, min))
        assertEquals(RemoteKey.DPAD_UP, SwipeClassifier.classify(-10f, -180f, 200, min))
    }

    @Test
    fun shortSwipeIsIgnored() {
        assertNull(SwipeClassifier.classify(60f, 0f, 100, min))
    }

    @Test
    fun ambiguousDiagonalIsIgnored() {
        assertNull(SwipeClassifier.classify(150f, 140f, 200, min))
    }

    @Test
    fun slowDragIsNotASwipe() {
        assertNull(SwipeClassifier.classify(300f, 0f, SwipeClassifier.MAX_DURATION_MS + 1, min))
    }
}
