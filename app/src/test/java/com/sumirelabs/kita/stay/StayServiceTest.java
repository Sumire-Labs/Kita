package com.sumirelabs.kita.stay;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class StayServiceTest {
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-10T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return now.get(); }
    };
    private static class Voice implements StayService.Voice {
        int joins;
        int leaves;
        boolean failLeave;
        StaySession.View lastPanel;
        long movedChannel;
        int maintenance;
        @Override public synchronized void operate(long guild, Runnable action) { action.run(); }
        @Override public void connect(long guild, long channel) { joins++; }
        @Override public void disconnect(long guild) {
            if (failLeave) throw new IllegalStateException("temporarily unavailable");
            leaves++;
        }
        @Override public void refresh(long guild, StaySession.View view) { lastPanel = view; }
        @Override public long maintain(long guild, long channel) { maintenance++; return movedChannel == 0 ? channel : movedChannel; }
    }

    @Test void unlimitedStaySurvivesTimeAndSecondCommandLeaves() {
        var voice = new Voice();
        try (var stay = new StayService(voice, clock)) {
            var view = stay.toggle(1, 10);
            assertTrue(view.active()); assertNull(view.until());
            now.updateAndGet(time -> time.plus(Duration.ofDays(365)));
            stay.expire(1);
            assertEquals(0, voice.leaves);
            assertFalse(stay.toggle(1, 10).active());
            assertEquals(1, voice.joins); assertEquals(1, voice.leaves);
        }
    }

    @Test void durationStartsAtSettingAndQueuedExpiryRechecksNewDeadline() {
        var voice = new Voice();
        try (var stay = new StayService(voice, clock)) {
            var view = stay.toggle(1, 10);
            stay.change(1, 10, view.token(), Duration.ofMinutes(30), false);
            now.updateAndGet(time -> time.plus(Duration.ofMinutes(30)));
            stay.change(1, 10, view.token(), Duration.ofHours(2), false);
            stay.expire(1);
            assertEquals(0, voice.leaves);
            now.updateAndGet(time -> time.plus(Duration.ofHours(2)));
            stay.expire(1); stay.expire(1);
            assertEquals(1, voice.leaves); assertFalse(stay.view(1).active());
            assertFalse(voice.lastPanel.active());
        }
    }

    @Test void unlimitedCancelsDeadlineAndGuildsRemainIndependent() {
        var voice = new Voice();
        try (var stay = new StayService(voice, clock)) {
            var first = stay.toggle(1, 10);
            var second = stay.toggle(2, 20);
            stay.change(1, 10, first.token(), Duration.ofMinutes(1), false);
            stay.change(2, 20, second.token(), Duration.ofMinutes(1), false);
            stay.change(1, 10, first.token(), null, false);
            now.updateAndGet(time -> time.plusSeconds(60));
            stay.expire(1); stay.expire(2);
            assertTrue(stay.view(1).active()); assertFalse(stay.view(2).active());
            assertEquals(1, voice.leaves);
        }
    }

    @Test void stalePanelsAndModalsCannotChangeANewStayAndOtherChannelsCannotStopIt() {
        var voice = new Voice();
        try (var stay = new StayService(voice, clock)) {
            var old = stay.toggle(1, 10);
            stay.panel(1, old.token(), 30, 40);
            assertThrows(IllegalArgumentException.class, () -> stay.toggle(1, 20));
            stay.change(1, 10, old.token(), null, true);
            var current = stay.toggle(1, 20);
            assertNotEquals(old.token(), current.token());
            assertThrows(IllegalArgumentException.class, () -> stay.change(1, 20, old.token(), null, true));
            assertThrows(IllegalArgumentException.class, () -> stay.require(1, 10, current.token()));
            assertEquals(20, stay.view(1).voiceChannel());
        }
    }

    @Test void failedExitRetainsDeadlineForRetry() {
        var voice = new Voice();
        try (var stay = new StayService(voice, clock)) {
            var view = stay.toggle(1, 10);
            stay.change(1, 10, view.token(), Duration.ofMinutes(1), false);
            now.updateAndGet(time -> time.plusSeconds(60));
            voice.failLeave = true; stay.expire(1);
            assertTrue(stay.view(1).active());
            voice.failLeave = false; stay.expire(1);
            assertFalse(stay.view(1).active()); assertEquals(1, voice.leaves);
        }
    }

    @Test void durationRejectsZeroNegativeOverflowAndInvalidMinutes() {
        assertEquals(Duration.ofMinutes(90), StaySession.duration("1", "30"));
        for (var value : new String[][]{{"0", "0"}, {"-1", "0"}, {"1", "60"}, {"99999999999", "0"}, {"x", "1"}}) {
            assertThrows(IllegalArgumentException.class, () -> StaySession.duration(value[0], value[1]));
        }
    }
    @Test void reconnectIsCancelledAfterStopOrDeadlineAndMovesKeepTheTimer() {
        var voice = new Voice();
        try (var stay = new StayService(voice, clock)) {
            var view = stay.toggle(1, 10);
            stay.change(1, 10, view.token(), Duration.ofMinutes(1), false);
            var deadline = stay.view(1).until();
            voice.movedChannel = 20; stay.maintain(1);
            assertEquals(20, stay.view(1).voiceChannel());
            assertEquals(deadline, stay.view(1).until());
            assertThrows(IllegalArgumentException.class, () -> stay.require(1, 10, view.token()));
            now.set(deadline); stay.maintain(1);
            assertEquals(1, voice.maintenance);
            stay.expire(1); stay.maintain(1);
            assertEquals(1, voice.maintenance);
        }
    }
    @Test void checkingAnInteractionDoesNotWaitForNetworkIo() throws Exception {
        var entered = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var voice = new Voice() {
            @Override public void disconnect(long guild) {
                entered.countDown();
                try { release.await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        };
        try (var stay = new StayService(voice, clock); var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            var view = stay.toggle(1, 10);
            var stopped = executor.submit(() -> stay.change(1, 10, view.token(), null, true));
            try {
                assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS));
                assertTimeoutPreemptively(Duration.ofSeconds(2), () -> stay.require(1, 10, view.token()));
            } finally { release.countDown(); }
            stopped.get(2, java.util.concurrent.TimeUnit.SECONDS);
        }
    }
}
