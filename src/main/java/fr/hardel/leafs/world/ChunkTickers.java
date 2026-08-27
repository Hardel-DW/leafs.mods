package fr.hardel.leafs.world;

import fr.hardel.leafs.ownership.TickGuard;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

/** Vanilla's ticker list, per chunk: registration order, adds during the pass wait for the next one. */
public final class ChunkTickers {
    private static final Consumer<TickingBlockEntity> TICK = TickingBlockEntity::tick;
    private final List<TickingBlockEntity> tickers = new ArrayList<>();
    private final List<TickingBlockEntity> pending = new ArrayList<>();
    private boolean ticking;

    public void add(TickingBlockEntity ticker) {
        (ticking ? pending : tickers).add(ticker);
    }

    public void tickAll(boolean runsNormally) {
        ticking = true;
        if (!pending.isEmpty()) {
            tickers.addAll(pending);
            pending.clear();
        }

        Iterator<TickingBlockEntity> iterator = tickers.iterator();
        while (iterator.hasNext()) {
            TickingBlockEntity ticker = iterator.next();
            if (ticker.isRemoved()) {
                iterator.remove();
            } else if (runsNormally) {
                TickGuard.tickOrSkip(TICK, ticker);
            }
        }

        ticking = false;
    }

    public int size() {
        return tickers.size() + pending.size();
    }
}
