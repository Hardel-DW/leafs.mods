package fr.hardel.leafs.chunk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/** The POI write belongs to the level's single mutator; the global queue would run it concurrently with this level's regions. */
public final class PoiWriteReroute {

    private PoiWriteReroute() {
    }

    public static void onBlockStateChange(ServerLevel level, BlockPos pos, BlockState oldState, BlockState newState, Consumer<Runnable> levelSerial) {
        Optional<Holder<PoiType>> oldType = PoiTypes.forState(oldState);
        Optional<Holder<PoiType>> newType = PoiTypes.forState(newState);
        if (Objects.equals(oldType, newType)) {
            return;
        }

        BlockPos immutable = pos.immutable();
        oldType.ifPresent(type -> levelSerial.accept(() -> {
            level.getPoiManager().remove(immutable);
            level.debugSynchronizers().dropPoi(immutable);
        }));
        newType.ifPresent(type -> levelSerial.accept(() -> {
            PoiRecord record = level.getPoiManager().add(immutable, type);
            if (record != null) {
                level.debugSynchronizers().registerPoi(record);
            }
        }));
    }
}
