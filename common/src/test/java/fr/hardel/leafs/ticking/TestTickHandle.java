package fr.hardel.leafs.ticking;

import java.util.function.BooleanSupplier;

final class TestTickHandle extends TickHandle {
    private final BooleanSupplier gate;
    private final Runnable body;
    private long ticks;

    TestTickHandle(long id, Runnable body) {
        this(id, () -> true, body);
    }

    TestTickHandle(long id, BooleanSupplier gate, Runnable body) {
        super(id, "test:world", 1);
        this.gate = gate;
        this.body = body;
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
}
