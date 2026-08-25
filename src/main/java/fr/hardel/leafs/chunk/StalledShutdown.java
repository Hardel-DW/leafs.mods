package fr.hardel.leafs.chunk;

import fr.hardel.leafs.Leafs;
import net.minecraft.server.level.ServerLevel;

import java.util.concurrent.TimeUnit;

/**
 * A halted server that still reports chunk work names what holds it, once a second. Vanilla's stop
 * loop spins until every source is empty and says nothing when one never drains, which turns any
 * stuck source into a silent freeze.
 */
public final class StalledShutdown {
    private static final long INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final StringBuilder pending = new StringBuilder();
    private long lastReportNanos;

    public boolean due() {
        long now = System.nanoTime();
        if (now - lastReportNanos < INTERVAL_NANOS) {
            return false;
        }

        lastReportNanos = now;
        pending.setLength(0);

        return true;
    }

    public StalledShutdown holding(String source, int count) {
        if (count > 0) {
            append(source + "=" + count);
        }

        return this;
    }

    public StalledShutdown holding(String source, boolean busy) {
        if (busy) {
            append(source);
        }

        return this;
    }

    public void report(ServerLevel level) {
        Leafs.LOGGER.warn("Shutdown of {} still waiting on chunk work: {}", level.dimension().identifier(), pending.isEmpty() ? "nothing named" : pending);
    }

    private void append(String source) {
        if (!pending.isEmpty()) {
            pending.append(", ");
        }

        pending.append(source);
    }
}
