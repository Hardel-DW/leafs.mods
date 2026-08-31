package fr.hardel.leafs.chunk;

import fr.hardel.leafs.metrics.DeferReason;
import fr.hardel.leafs.scheduler.DeferredTransports;
import fr.hardel.leafs.scheduler.DeferredWork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jspecify.annotations.NonNull;

import java.util.function.Predicate;

/** The block write runs on the owner of its chunk, its side effects with it, like the POI write the block change causes. */
public final class BlockWriteReroute {

    private BlockWriteReroute() {
    }

    /** Vanilla's answer for a write that happened; the mailed one happens on the owner within a tick. */
    public static boolean write(BlockPos pos, DeferredTransports transports, Predicate<@NonNull BlockPos> write) {
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        if (transports.owns(chunkX, chunkZ)) {
            return write.test(pos);
        }

        // A caller that builds a structure walks one mutable across all its writes.
        BlockPos target = pos.immutable();
        DeferredWork.owner(DeferReason.BLOCK_WRITE, transports.stats(), chunkX, chunkZ, () -> write.test(target)).submit(transports);
        return true;
    }
}
