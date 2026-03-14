package com.souigat.mobile.ui.screens.trips

import com.souigat.mobile.util.Constants
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TripListUiStateTest {

    @Test
    fun isStale_isTrueWhenTimestampIsOlderThanThreshold() {
        val state = TripListUiState(
            lastRefreshAt = System.currentTimeMillis() - Constants.TRIP_LIST_STALE_MS - 1
        )

        assertTrue(state.isStale)
    }

    @Test
    fun isStale_isFalseWhenTimestampIsWithinThreshold() {
        val state = TripListUiState(
            lastRefreshAt = System.currentTimeMillis() - Constants.TRIP_LIST_STALE_MS + 1_000
        )

        assertFalse(state.isStale)
    }
}
