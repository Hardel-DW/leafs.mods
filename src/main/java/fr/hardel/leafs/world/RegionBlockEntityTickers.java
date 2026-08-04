package fr.hardel.leafs.world;

import fr.hardel.leafs.region.CoordinateKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

/**
 * Vanilla {@code Level.tickBlockEntities} semantics, one instance per region. The chunk key is
 * captured at registration and never re-read from the ticker: a live ticker's position is not
 * stable - Lithium's sleeping system rebinds it to a placeholder answering null - while the block
 * entity itself never moves.
 */
public final class RegionBlockEntityTickers {
    private record Entry(TickingBlockEntity ticker, long chunkKey) {
    }

    private final List<Entry> tickers = new ArrayList<>();
    private final List<Entry> pending = new ArrayList<>();
    private boolean ticking;

    public void add(TickingBlockEntity ticker, long chunkKey) {
        (ticking ? pending : tickers).add(new Entry(ticker, chunkKey));
    }

    public void tickAll(boolean runsNormally, LongPredicate tickingChunk) {
        ticking = true;
        if (!pending.isEmpty()) {
            tickers.addAll(pending);
            pending.clear();
        }

        Iterator<Entry> iterator = tickers.iterator();
        while (iterator.hasNext()) {
            Entry entry = iterator.next();
            if (entry.ticker().isRemoved()) {
                iterator.remove();
            } else if (runsNormally && tickingChunk.test(entry.chunkKey())) {
                entry.ticker().tick();
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
        List<Entry> kept = new ArrayList<>();
        for (Entry entry : tickers) {
            RegionBlockEntityTickers child = childBySection.apply(sectionKeyOf(entry, sectionShift));
            if (child != null) {
                child.tickers.add(entry);
            } else {
                kept.add(entry);
            }
        }

        tickers.clear();
        tickers.addAll(kept);
    }

    private static void rebucket(List<Entry> source, int sectionShift, LongFunction<RegionBlockEntityTickers> childBySection) {
        for (Entry entry : source) {
            RegionBlockEntityTickers child = childBySection.apply(sectionKeyOf(entry, sectionShift));
            if (child != null) {
                child.tickers.add(entry);
            }
        }

        source.clear();
    }

    private static long sectionKeyOf(Entry entry, int sectionShift) {
        return CoordinateKey.pack(ChunkPos.getX(entry.chunkKey()) >> sectionShift, ChunkPos.getZ(entry.chunkKey()) >> sectionShift);
    }
}
