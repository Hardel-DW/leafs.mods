package fr.hardel.leafs.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;

import java.util.Optional;
import java.util.stream.Stream;

/** Vanilla takes the first record with space and ignores the answer of the acquisition; two regions can both pass the filter on the last place, so a take is the first candidate whose acquisition succeeds. */
public final class PoiClaims {

    private PoiClaims() {}

    public static Optional<BlockPos> firstAcquired(Stream<PoiRecord> candidates) {
        return candidates.filter(PoiRecord::acquireTicket).findFirst().map(PoiRecord::getPos);
    }
}
