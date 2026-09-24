package fr.hardel.leafs.world;

import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class ChunkTickers {
    private final List<TickingBlockEntity> tickers = new ArrayList<>();
    private final List<TickingBlockEntity> pending = new ArrayList<>();
    private final List<Runnable> openers = new ArrayList<>();
    private boolean ticking;

    public void add(TickingBlockEntity ticker) {
        (ticking ? pending : tickers).add(ticker);
    }

    public boolean beforePass(Runnable work) {
        openers.add(work);
        return openers.size() == 1;
    }

    public void open() {
        for (int i = 0; i < openers.size(); i++) {
            openers.get(i).run();
        }

        openers.clear();
    }

    public void tickAll(boolean runsNormally) {
        ticking = true;
        open();
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
