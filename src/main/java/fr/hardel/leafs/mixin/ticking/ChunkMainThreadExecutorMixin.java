package fr.hardel.leafs.mixin.ticking;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.ticking.ChunkPumpAccess;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * The pump runs freely now that promotions live under the area locks. What remains here is the
 * universal-owner drain: a server thread that waits on a chunk while it holds the exclusion or the
 * barrier must run the region-queued chunk tasks itself, because no region can.
 */
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

        LevelRegions regions = LevelRegions.of(level);
        if (regions.ownership().isLevelSerialHeldByCurrentThread() || TickingManager.of(level.getServer()).barrier().isHeldByCurrentThread()) {
            return regions.drainTasksInline() > 0;
        }

        return false;
    }
}
