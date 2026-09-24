package com.fithealthzone.bandsongbook.ui.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SetlistPlaylistBuildGuardTest {

    @Test
    fun `new build invalidates an older asynchronous build`() {
        val guard = PlaylistBuildGuard()

        val oldBuild = guard.begin()
        val currentBuild = guard.begin()

        assertFalse(guard.isCurrent(oldBuild))
        assertTrue(guard.isCurrent(currentBuild))
    }

    @Test
    fun `selection change invalidates in flight build`() {
        val guard = PlaylistBuildGuard()
        val inFlightBuild = guard.begin()

        guard.invalidate()

        assertFalse(guard.isCurrent(inFlightBuild))
    }
}
