package fr.hardel.leafs.mixin.ticking;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.ticking.ChunkPumpAccess;
import fr.hardel.leafs.ticking.LevelOwnership;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Pump gate: chunk bookkeeping polls run under the level's serial side, so promotions never race a region tick. */
@Mixin(targets = "net.minecraft.server.level.ServerChunkCache$MainThreadExecutor")
public abstract class ChunkMainThreadExecutorMixin implements ChunkPumpAccess {

    @Unique
    private LevelOwnership leafs$ownership;

    @Override
    public void leafs$bindOwnership(LevelOwnership ownership) {
        leafs$ownership = ownership;
    }

    @WrapMethod(method = "pollTask")
    private boolean leafs$pumpUnderLevelSerial(Operation<Boolean> original) {
        LevelOwnership ownership = leafs$ownership;
        if (ownership == null || ownership.isLevelSerialHeldByCurrentThread()) {
            return original.call();
        }

        if (ownership.isRegionTickHeldByCurrentThread()) {
            throw new IllegalStateException("A region worker pumped its own level's chunk bookkeeping");
        }

        ownership.enterLevelSerial();
        try {
            return original.call();
        } finally {
            ownership.exitLevelSerial();
        }
    }
}
