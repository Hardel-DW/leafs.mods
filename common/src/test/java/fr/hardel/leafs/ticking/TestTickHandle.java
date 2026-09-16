package fr.hardel.leafs.ticking;

import java.util.function.BooleanSupplier;

final class TestTickHandle extends TickHandle {
    private final BooleanSupplier gate;
    private final Runnable body;
    private long ticks;
    private final boolean crashReportFails;

    TestTickHandle(long id, Runnable body) {
        this(id, () -> true, body, false);
    }

    TestTickHandle(long id, Runnable body, boolean crashReportFails) {
        this(id, () -> true, body, crashReportFails);
    }

    TestTickHandle(long id, BooleanSupplier gate, Runnable body) {
        this(id, gate, body, false);
    }

    private TestTickHandle(long id, BooleanSupplier gate, Runnable body, boolean crashReportFails) {
        super(new RegionContext.Region(id, "test:world"), 1);
        this.gate = gate;
        this.body = body;
        this.crashReportFails = crashReportFails;
    }

    @Override
    public long currentTick() {
        return ticks;
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

    @Override
    protected RegionCrashReport buildCrashReport() {
        if (crashReportFails) {
            throw new IllegalArgumentException("the level is too broken to describe");
        }

        return new RegionCrashReport(id(), dimension(), currentTick(), 0, 0);
    }
}
