package fr.hardel.leafs.world;

import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Vanilla's ticker list, per chunk: registration order, adds during the pass wait for the next one. */
public final class ChunkTickers {
    private final List<TickingBlockEntity> tickers = new ArrayList<>();
    private final List<TickingBlockEntity> pending = new ArrayList<>();
    private final List<Runnable> openers = new ArrayList<>();
    private boolean ticking;

    public void add(TickingBlockEntity ticker) {
        (ticking ? pending : tickers).add(ticker);
    }

    /** Runs at the head of the chunk's next pass, before any ticker, frozen or not: a loader's load callback lands here. */
    public void beforePass(Runnable work) {
        openers.add(work);
    }

    public void tickAll(boolean runsNormally) {
        ticking = true;
        for (int i = 0; i < openers.size(); i++) {
            openers.get(i).run();
        }

        openers.clear();
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
                ticker.tick();
            }
        }

        ticking = false;
    }

    public int size() {
        return tickers.size() + pending.size();
    }
}
