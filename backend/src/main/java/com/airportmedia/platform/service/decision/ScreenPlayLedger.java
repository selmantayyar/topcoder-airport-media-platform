package com.airportmedia.platform.service.decision;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * One screen's plays of one campaign inside the rolling frequency-cap window, for simulated days: the
 * pipeline drops a campaign once {@code plays in window ≥ frequencyCapPerScreen}, so a winning sample is
 * granted {@code min(wanted, cap − plays in window)} plays (a cap of 0 means uncapped).
 */
final class ScreenPlayLedger {

    private final Duration window;
    private final Deque<Entry> entries = new ArrayDeque<>();

    ScreenPlayLedger(int windowMinutes) {
        this.window = Duration.ofMinutes(windowMinutes);
    }

    /** Plays recorded in the window ending at {@code at} (exclusive of plays a full window old). */
    int inWindow(Instant at) {
        Instant since = at.minus(window);
        while (!entries.isEmpty() && !entries.peekFirst().at().isAfter(since)) {
            entries.removeFirst();
        }
        return (int) entries.stream().mapToLong(Entry::plays).sum();
    }

    /** Records and returns the plays granted at {@code at}. */
    long play(Instant at, long wanted, int cap) {
        long granted = cap > 0 ? Math.max(0, Math.min(wanted, cap - inWindow(at))) : wanted;
        if (granted > 0) {
            entries.addLast(new Entry(at, granted));
        }
        return granted;
    }

    private record Entry(Instant at, long plays) {}
}
