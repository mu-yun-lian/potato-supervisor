package org.potato.supervisor.rules

import org.junit.Test
import org.junit.Assert.*

class WindowPackagesTest {
    @Test fun freshEventCanBridgeTemporaryDetachOfTheSameWindow() {
        val p=WindowPackages(); p.record(1,"game",0); p.visible(setOf(1),0)
        p.record(1,"game",100); p.visible(emptySet(),100)
        assertEquals("game",p.visible(setOf(1),200)[1])
    }
    @Test fun duplicateContentDoesNotExpireAnAlreadyConfirmedWindow() {
        val p=WindowPackages(); p.record(1,"game",0); p.visible(setOf(1),0)
        assertFalse(p.record(1,"game",100,renew=false))
        assertEquals("game",p.visible(setOf(1),2000)[1])
        assertFalse(p.record(-1,"game",2100)); assertFalse(p.record(2,"",2100))
    }
    @Test fun newLandscapeWindowEventBeforeWindowListDoesNotLoseIdentity() {
        val p=WindowPackages()
        p.record(1,"launcher",0)
        assertEquals("launcher",p.visible(setOf(1),0)[1])
        p.record(2,"game",100)
        assertNull(p.visible(setOf(1),100)[2]) // Never infer a window before it is visible.
        assertEquals("game",p.visible(setOf(2),200)[2])
    }
    @Test fun disappearedWindowDoesNotComeBackFromHistory() {
        val p=WindowPackages(); p.record(1,"game",0); p.visible(setOf(1),0)
        p.visible(emptySet(),100)
        assertNull(p.visible(setOf(1),200)[1])
    }
    @Test fun unmatchedEventExpiresAndClearDropsPendingIdentity() {
        val p=WindowPackages(); p.record(1,"game",0)
        assertNull(p.visible(setOf(1),2000)[1])
        p.record(2,"game",2100); p.clear()
        assertNull(p.visible(setOf(2),2200)[2])
    }
    @Test fun stableVisibleWindowKeepsIdentityAndFreshMetadataUpdatesIt() {
        val p=WindowPackages(); p.record(1,"game",0); p.visible(setOf(1),0)
        assertEquals("game",p.visible(setOf(1),2000)[1])
        p.record(1,"other",2100)
        assertEquals("other",p.visible(setOf(1),2100)[1])
        assertNull(p.visible(setOf(9),2200)[9])
    }
}
