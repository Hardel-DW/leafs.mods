package fr.hardel.leafs.chunk;

import fr.hardel.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** A bed has one place: the second taker of the same record gets nothing, and a full record is skipped for the next candidate. */
@ExtendWith(MinecraftBootstrap.class)
class PoiClaimsTest {
    private final Holder<PoiType> home = BuiltInRegistries.POINT_OF_INTEREST_TYPE.getOrThrow(PoiTypes.HOME);
    private final PoiRecord bed = new PoiRecord(new BlockPos(1, 64, 1), home, () -> { });

    @Test
    void theLastPlaceGoesToOneTakerOnly() {
        assertEquals(Optional.of(bed.getPos()), PoiClaims.firstAcquired(Stream.of(bed)));

        assertEquals(Optional.empty(), PoiClaims.firstAcquired(Stream.of(bed)));
    }

    @Test
    void aRecordTakenMeanwhileIsSkippedForTheNextCandidate() {
        PoiRecord otherBed = new PoiRecord(new BlockPos(2, 64, 1), home, () -> { });
        PoiClaims.firstAcquired(Stream.of(bed));

        assertEquals(Optional.of(otherBed.getPos()), PoiClaims.firstAcquired(Stream.of(bed, otherBed)));
    }
}
