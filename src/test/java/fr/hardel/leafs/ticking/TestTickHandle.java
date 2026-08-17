package fr.hardel.leafs.ticking;

import fr.hardel.leafs.metrics.RegionStage;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.ownership.RegionCrashReport;

import java.util.function.LongConsumer;

final class TestTickHandle extends TickHandle {
    private final LongConsumer body;
    private final boolean crashReportFails;

    TestTickHandle(long id, LongConsumer body) {
        this(id, body, false);
    }

    TestTickHandle(long id, LongConsumer body, boolean crashReportFails) {
        super(new RegionContext.Region(id, "test:world"), RegionStage.values().length);
        this.body = body;
        this.crashReportFails = crashReportFails;
    }

    @Override
    protected void tick(long tickCount) {
        body.accept(tickCount);
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        if (crashReportFails) {
            throw new IllegalArgumentException("the level is too broken to describe");
        }

        return new RegionCrashReport(id(), dimension(), currentTick(), 0, 0);
    }
}
