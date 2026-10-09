package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PlayerLifecycleTest {
    @Test void replacementRejectsOldInteractionsAndKeepsNewPanelWhenOldRefreshFails() {
        var session = new MusicSession();
        session.replacePanel(10, 100);
        var old = session.beginPanelRefresh();
        assertNull(session.beginPanelRefresh(), "Only one update may be pending per panel");
        assertEquals(old, session.replacePanel(20, 200));
        assertFalse(session.isCurrentPanel(100));
        assertTrue(session.isCurrentPanel(200));
        var current = session.beginPanelRefresh();
        session.finishPanelRefresh(old.messageId(), true);
        assertTrue(session.isCurrentPanel(200));
        assertNull(session.beginPanelRefresh(), "An old callback must not clear the new pending update");
        session.finishPanelRefresh(current.messageId(), false);
        assertEquals(current, session.beginPanelRefresh());
    }

    @Test void idlePanelStillRefreshesAndTransientFailuresAreRetried() {
        var session = new MusicSession();
        session.replacePanel(10, 100);
        assertNull(session.queue.current());
        var panel = session.beginPanelRefresh();
        assertNotNull(panel);
        session.finishPanelRefresh(panel.messageId(), false);
        assertEquals(panel, session.beginPanelRefresh());
        session.finishPanelRefresh(panel.messageId(), true);
        assertNull(session.beginPanelRefresh());
        assertFalse(session.isCurrentPanel(100));
    }
}
