package fr.hardel.leafs.world;

import fr.hardel.leafs.region.CoordinateKey;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.Predicate;

/** Vanilla {@code Level.tickBlockEntities} semantics, one instance per region. */
public final class RegionBlockEntityTickers {
    private final List<TickingBlockEntity> tickers = new ArrayList<>();
    private final List<TickingBlockEntity> pending = new ArrayList<>();
    private boolean ticking;

    public void add(TickingBlockEntity ticker) {
        (ticking ? pending : tickers).add(ticker);
    }

    public void tickAll(boolean runsNormally, Predicate<BlockPos> tickable) {
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
            } else if (runsNormally && tickable.test(ticker.getPos())) {
                ticker.tick();
            }
        }

        ticking = false;
    }

    public int size() {
        return tickers.size() + pending.size();
    }

    void mergeInto(RegionBlockEntityTickers target) {
        target.tickers.addAll(tickers);
        target.tickers.addAll(pending);
        tickers.clear();
        pending.clear();
    }

    /** Tickers whose section died with the split are dropped, like their chunk's other transient state. */
    void splitInto(int sectionShift, LongFunction<RegionBlockEntityTickers> childBySection) {
        rebucket(tickers, sectionShift, childBySection);
        rebucket(pending, sectionShift, childBySection);
    }

    /** Activation variant of {@link #splitInto}: an unmatched ticker stays here instead of being dropped. */
    void migrateInto(int sectionShift, LongFunction<RegionBlockEntityTickers> childBySection) {
        List<TickingBlockEntity> kept = new ArrayList<>();
        for (TickingBlockEntity ticker : tickers) {
            RegionBlockEntityTickers child = childBySection.apply(CoordinateKey.pack(ticker.getPos().getX() >> (4 + sectionShift), ticker.getPos().getZ() >> (4 + sectionShift)));
            if (child != null) {
                child.tickers.add(ticker);
            } else {
                kept.add(ticker);
            }
        }

        tickers.clear();
        tickers.addAll(kept);
    }

    private static void rebucket(List<TickingBlockEntity> source, int sectionShift, LongFunction<RegionBlockEntityTickers> childBySection) {
        for (TickingBlockEntity ticker : source) {
            RegionBlockEntityTickers child = childBySection.apply(CoordinateKey.pack(ticker.getPos().getX() >> (4 + sectionShift), ticker.getPos().getZ() >> (4 + sectionShift)));
            if (child != null) {
                child.tickers.add(ticker);
            }
        }

        source.clear();
    }
}
