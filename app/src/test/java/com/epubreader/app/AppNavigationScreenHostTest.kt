package com.epubreader.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppNavigationScreenHostTest {

    @Test
    fun transitionBetweenNonReaderRoutes_staysAnimated() {
        assertTrue(
            shouldAnimateAppRouteTransition(
                initialRoute = AppRoute.Library,
                targetRoute = AppRoute.Settings,
            )
        )
    }

    @Test
    fun transitionIntoReader_skipsAnimation() {
        assertFalse(
            shouldAnimateAppRouteTransition(
                initialRoute = AppRoute.Library,
                targetRoute = AppRoute.Reader("book-1"),
            )
        )
    }

    @Test
    fun transitionOutOfReader_skipsAnimation() {
        assertFalse(
            shouldAnimateAppRouteTransition(
                initialRoute = AppRoute.Reader("book-1"),
                targetRoute = AppRoute.Library,
            )
        )
    }
}
