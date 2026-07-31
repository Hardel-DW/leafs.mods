package fr.hardel.leafs.ticking;

import fr.hardel.leafs.ownership.RegionCrashReport;

import java.util.function.LongConsumer;

final class TestTickHandle extends TickHandle {
    private final LongConsumer body;

    TestTickHandle(long id, LongConsumer body) {
        super(id, "test:world");
        this.body = body;
    }

    @Override
    protected void tick(long tickCount) {
        body.accept(tickCount);
    }

    @Override
    protected RegionCrashReport buildCrashReport() {
        return new RegionCrashReport(id(), dimension(), currentTick(), 0, 0);
    }
}
