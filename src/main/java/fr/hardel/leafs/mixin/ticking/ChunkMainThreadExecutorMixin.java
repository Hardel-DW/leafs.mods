package fr.hardel.leafs.mixin.ticking;

import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.chunk.ChunkMailbox;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.ticking.ChunkPumpAccess;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** A server thread waiting on a chunk runs the mail nobody else may: every chunk's after the pool stopped, its borrowed regions' while borrowing. */
@Mixin(targets = "net.minecraft.server.level.ServerChunkCache$MainThreadExecutor")
public abstract class ChunkMainThreadExecutorMixin implements ChunkPumpAccess {

    @Unique
    private ServerLevel leafs$level;

    @Override
    public void leafs$bindLevel(ServerLevel level) {
        leafs$level = level;
    }

    /** The drain only runs once the pump is empty: a full pump must not pay a sweep of every region per task. */
    @WrapMethod(method = "pollTask")
    private boolean leafs$pumpWithUniversalDrain(Operation<Boolean> original) {
        if (original.call()) {
            return true;
        }

        ServerLevel level = leafs$level;
        if (level == null) {
            return false;
        }

        TickingManager ticking = TickingManager.of(level.getServer());
        ChunkMailbox mailbox = RegionChunkAccess.scheduling(level.getChunkSource().chunkMap).mailbox();
        if (ticking.halted()) {
            return mailbox.drainAll() > 0;
        }

        RegionBorrow borrow = RegionBorrow.current();
        return borrow != null && borrow.drainMail(mailbox, level.getChunkSource().chunkMap) > 0;
    }
}
