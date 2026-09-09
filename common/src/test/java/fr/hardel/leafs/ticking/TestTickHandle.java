package fr.hardel.leafs.ticking;



final class TestTickHandle extends TickHandle {
    private final Runnable body;
    private long ticks;
    private final boolean crashReportFails;

    TestTickHandle(long id, Runnable body) {
        this(id, body, false);
    }

    TestTickHandle(long id, Runnable body, boolean crashReportFails) {
        super(new RegionContext.Region(id, "test:world"), 1);
        this.body = body;
        this.crashReportFails = crashReportFails;
    }

    @Override
    public long currentTick() {
        return ticks;
    }

    @Override
    protected void tick() {
        ticks++;
        body.run();
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        if (crashReportFails) {
            throw new IllegalArgumentException("the level is too broken to describe");
        }

        return new RegionCrashReport(id(), dimension(), currentTick(), 0, 0);
    }
}
