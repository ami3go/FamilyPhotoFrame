package com.example.familyphotoframe.ui.slideshow

import com.example.familyphotoframe.domain.engine.CollageLayout
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreparedSlideRendererPolicyTest {
    @Test fun onlyGenuineThreePanelLayoutUsesPanelMotionRenderer() {
        assertTrue(usesPanelMotionRenderer(CollageLayout.THREE_COLUMNS, 3))
        assertFalse(usesPanelMotionRenderer(CollageLayout.TWO_COLUMNS, 2))
        assertFalse(usesPanelMotionRenderer(CollageLayout.THREE_COLUMNS, 2))
        assertFalse(usesPanelMotionRenderer(CollageLayout.THREE_COLUMNS, 4))
    }
}
