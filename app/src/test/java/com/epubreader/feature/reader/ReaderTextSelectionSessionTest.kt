package com.epubreader.feature.reader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTextSelectionSessionTest {

    @Test
    fun showMenu_hide_showMenuWithinGraceWindow_keepsSelectionActive() {
        val scheduler = FakeScheduler()
        val session = ReaderTextSelectionSession(
            scheduler = scheduler,
            onActiveChanged = {}
        )
        val firstCopyAction = {}
        val replacementCopyAction = {}

        session.showMenu(firstCopyAction)
        session.hide()
        scheduler.advanceBy(199)
        session.showMenu(replacementCopyAction)
        scheduler.advanceBy(1)

        assertTrue(session.isActive)
        assertNotNull(session.copyAction)
    }

    @Test
    fun showMenu_hideWithoutFollowup_clearsSelectionAfterGraceWindow() {
        val scheduler = FakeScheduler()
        val session = ReaderTextSelectionSession(
            scheduler = scheduler,
            onActiveChanged = {}
        )

        session.showMenu {}
        session.hide()
        scheduler.advanceBy(199)

        assertTrue(session.isActive)
        assertNotNull(session.copyAction)

        scheduler.advanceBy(1)

        assertFalse(session.isActive)
        assertNull(session.copyAction)
    }

    @Test
    fun hideDuringActivePointer_defersClearUntilPointerRelease() {
        val scheduler = FakeScheduler()
        val session = ReaderTextSelectionSession(
            scheduler = scheduler,
            onActiveChanged = {}
        )

        session.showMenu {}
        session.onPointerPressed()
        session.hide()
        scheduler.advanceBy(500)

        assertTrue(session.isActive)

        session.onPointerReleased()
        scheduler.advanceBy(199)
        assertTrue(session.isActive)

        scheduler.advanceBy(1)
        assertFalse(session.isActive)
    }

    @Test
    fun reset_clearsImmediatelyAndCancelsPendingDelayedClear() {
        val scheduler = FakeScheduler()
        val session = ReaderTextSelectionSession(
            scheduler = scheduler,
            onActiveChanged = {}
        )

        session.showMenu {}
        session.hide()
        session.reset()
        scheduler.advanceBy(200)

        assertFalse(session.isActive)
        assertNull(session.copyAction)
    }

    private class FakeScheduler : ReaderTextSelectionScheduler {
        private data class ScheduledTask(
            val runAtMillis: Long,
            val action: () -> Unit,
            var isCancelled: Boolean = false,
        )

        private var nowMillis: Long = 0
        private val tasks = mutableListOf<ScheduledTask>()

        override fun schedule(
            delayMillis: Long,
            action: () -> Unit,
        ): ReaderTextSelectionCancellable {
            val task = ScheduledTask(
                runAtMillis = nowMillis + delayMillis,
                action = action,
            )
            tasks += task
            return ReaderTextSelectionCancellable {
                task.isCancelled = true
            }
        }

        fun advanceBy(delayMillis: Long) {
            val targetTime = nowMillis + delayMillis
            while (true) {
                val nextTask = tasks
                    .filter { !it.isCancelled && it.runAtMillis <= targetTime }
                    .minByOrNull { it.runAtMillis }
                    ?: break
                tasks.remove(nextTask)
                nowMillis = nextTask.runAtMillis
                nextTask.action()
            }
            nowMillis = targetTime
        }
    }
}
