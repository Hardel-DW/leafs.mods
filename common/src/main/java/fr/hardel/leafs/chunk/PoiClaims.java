package fr.hardel.leafs.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;

import java.util.Optional;
import java.util.stream.Stream;

public final class PoiClaims {

    private PoiClaims() {}

    public static Optional<BlockPos> firstAcquired(Stream<PoiRecord> candidates) {
        return candidates.filter(PoiRecord::acquireTicket).findFirst().map(PoiRecord::getPos);
    }
}
