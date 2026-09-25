package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.world.SavedEpochAccess;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.concurrent.CompletableFuture;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderMixin implements SavedEpochAccess {

    @Unique
    private final Object leafs$barriers = new Object();

    @Unique
    private long leafs$savedEpoch;

    @Override
    public long leafs$savedEpoch() {
        return leafs$savedEpoch;
    }

    @Override
    public void leafs$markSaved(long epoch) {
        leafs$savedEpoch = epoch;
    }

    @WrapMethod(method = {"addSendDependency", "addSaveDependency"})
    private void leafs$composeUnderTheMonitor(CompletableFuture<?> sync, Operation<Void> original) {
        synchronized (leafs$barriers) {
            original.call(sync);
        }
    }

    @WrapMethod(method = {"getSendSyncFuture", "getSaveSyncFuture"})
    private CompletableFuture<?> leafs$readUnderTheMonitor(Operation<CompletableFuture<?>> original) {
        synchronized (leafs$barriers) {
            return original.call();
        }
    }

    @WrapMethod(method = "getChunkToSend")
    private LevelChunk leafs$sendableUnderTheMonitor(Operation<LevelChunk> original) {
        synchronized (leafs$barriers) {
            return original.call();
        }
    }

    @WrapMethod(method = "isReadyForSaving")
    private boolean leafs$savableUnderTheMonitor(Operation<Boolean> original) {
        synchronized (leafs$barriers) {
            return original.call();
        }
    }
}
