package com.techport.tvremote.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.techport.tvremote.domain.model.RemoteKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DPadHitTestTest {
    private val size = Size(300f, 300f)

    @Test
    fun mapsRegions() {
        assertEquals(RemoteKey.DPAD_CENTER, hitTest(Offset(150f, 150f), size))
        assertEquals(RemoteKey.DPAD_UP, hitTest(Offset(150f, 20f), size))
        assertEquals(RemoteKey.DPAD_DOWN, hitTest(Offset(150f, 280f), size))
        assertEquals(RemoteKey.DPAD_LEFT, hitTest(Offset(20f, 150f), size))
        assertEquals(RemoteKey.DPAD_RIGHT, hitTest(Offset(280f, 150f), size))
    }

    @Test
    fun outsideDiscIsIgnored() {
        assertNull(hitTest(Offset(5f, 5f), size))
    }
}
