package com.sumirelabs.kita.music;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlaybackQueueTest {
    @Test void queueLoopPreservesOrderAndPrevious() {
        var queue = new PlaybackQueue<String>(5);
        queue.add(List.of("a", "b", "c"));
        assertEquals("a", queue.next(false));
        queue.cycleLoop(); queue.cycleLoop();
        assertEquals("b", queue.next(true));
        assertEquals(List.of("c", "a"), queue.upcoming());
        assertEquals("a", queue.previous());
        assertEquals(List.of("b", "c", "a"), queue.upcoming());
    }
    @Test void manualSkipOverridesTrackLoop() {
        var queue = new PlaybackQueue<String>(5);
        queue.add(List.of("a", "b")); queue.next(false); queue.cycleLoop();
        assertEquals("a", queue.next(true));
        assertEquals("b", queue.next(false));
    }
    @Test void overflowDoesNotPartiallyMutateQueue() {
        var queue = new PlaybackQueue<String>(2);
        queue.add(List.of("a"));
        assertThrows(IllegalArgumentException.class, () -> queue.add(List.of("b", "c")));
        assertEquals(List.of("a"), queue.upcoming());
    }
    @Test void queuesRemainIndependent() {
        var first = new PlaybackQueue<String>(5); var second = new PlaybackQueue<String>(5);
        first.add(List.of("first")); second.add(List.of("second"));
        first.clear();
        assertEquals("second", second.next(false));
        assertNull(first.current());
    }
    @Test void selectingLoopModeTogglesOffAndSwitchesModes() {
        var queue = new PlaybackQueue<String>(5);
        queue.toggleLoop(PlaybackQueue.Loop.TRACK);
        assertEquals(PlaybackQueue.Loop.TRACK, queue.loop());
        queue.toggleLoop(PlaybackQueue.Loop.QUEUE);
        assertEquals(PlaybackQueue.Loop.QUEUE, queue.loop());
        queue.toggleLoop(PlaybackQueue.Loop.QUEUE);
        assertEquals(PlaybackQueue.Loop.OFF, queue.loop());
    }
    @Test void failedRemoteOperationCanRestoreQueueAndHistory() {
        var queue = new PlaybackQueue<String>(5);
        queue.add(List.of("a", "b")); queue.next(false);
        var snapshot = queue.snapshot();
        queue.next(false); queue.restore(snapshot);
        assertEquals("a", queue.current());
        assertEquals(List.of("b"), queue.upcoming());
        assertThrows(IllegalArgumentException.class, queue::previous);
    }
}
