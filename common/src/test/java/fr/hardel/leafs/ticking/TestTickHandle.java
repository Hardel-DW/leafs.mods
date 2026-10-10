package fr.hardel.leafs.ticking;

import java.util.function.BooleanSupplier;

public final class TestTickHandle extends TickHandle {
    private static final long PERIOD_NANOS = 50_000_000L;
    private final BooleanSupplier gate;
    private final Runnable body;
    private long ticks;
    private volatile long startDelayNanos = PERIOD_NANOS;

    public TestTickHandle(long id, Runnable body) {
        this(id, () -> true, body);
    }

    TestTickHandle(long id, BooleanSupplier gate, Runnable body) {
        super(id, "test:world", 1, () -> PERIOD_NANOS);
        this.gate = gate;
        this.body = body;
    }

    @Override
    public long currentTick() {
        return ticks;
    }

    void startDelayNanos(long startDelayNanos) {
        this.startDelayNanos = startDelayNanos;
    }

    @Override
    protected long nextStartDelayNanos() {
        return startDelayNanos;
    }

    @Override
    protected boolean tick() {
        if (!gate.getAsBoolean()) {
            return false;
        }

        ticks++;
        body.run();
        return true;
    }
}
