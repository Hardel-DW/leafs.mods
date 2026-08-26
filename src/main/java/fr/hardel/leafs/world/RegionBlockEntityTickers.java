package fr.hardel.leafs.world;

import fr.hardel.leafs.ownership.TickGuard;
import fr.hardel.leafs.region.CoordinateKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.TickingBlockEntity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongFunction;
import java.util.function.LongPredicate;

/** Vanilla {@code tickBlockEntities} per region. The chunk key is captured at registration, a Lithium-sleeping ticker answers no position. */
public final class RegionBlockEntityTickers {
    private record Entry(TickingBlockEntity ticker, long chunkKey) {
    }

    /** No capture, one constant for every region: a hopper refused at a border skips its own tick, not the phase. */
    private static final Consumer<TickingBlockEntity> TICK = TickingBlockEntity::tick;

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
                TickGuard.tickOrSkip(TICK, entry.ticker());
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

    void redistribute(int sectionShift, LongFunction<RegionBlockEntityTickers> childBySection, boolean keepOrphans) {
        List<Entry> orphans = new ArrayList<>();
        rebucket(tickers, sectionShift, childBySection, orphans);
        rebucket(pending, sectionShift, childBySection, orphans);
        if (keepOrphans) {
            tickers.addAll(orphans);
        }
    }

    private static void rebucket(List<Entry> source, int sectionShift, LongFunction<RegionBlockEntityTickers> childBySection, List<Entry> orphans) {
        for (Entry entry : source) {
            RegionBlockEntityTickers child = childBySection.apply(sectionKeyOf(entry, sectionShift));
            if (child != null) {
                child.tickers.add(entry);
            } else {
                orphans.add(entry);
            }
        }

        source.clear();
    }

    private static long sectionKeyOf(Entry entry, int sectionShift) {
        return CoordinateKey.pack(ChunkPos.getX(entry.chunkKey()) >> sectionShift, ChunkPos.getZ(entry.chunkKey()) >> sectionShift);
    }
}
