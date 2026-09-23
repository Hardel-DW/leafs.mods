package fr.hardel.leafs.mixin.ticking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import fr.hardel.leafs.ticking.ChunkPumpAccess;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(targets = "net.minecraft.server.level.ServerChunkCache$MainThreadExecutor")
public abstract class ChunkMainThreadExecutorMixin implements ChunkPumpAccess {
    @Unique
    private ServerLevel leafs$level;

    @Override
    public void leafs$bindLevel(ServerLevel level) {
        leafs$level = level;
    }

    @WrapMethod(method = "pollTask")
    private boolean leafs$pumpWithTheOwnedInboxes(Operation<Boolean> original) {
        if (original.call()) {
            return true;
        }

        ServerLevel level = leafs$level;
        if (level == null) {
            return false;
        }

        if (TickingManager.of(level.getServer()).halted()) {
            return LevelRegions.of(level).drainInboxes() > 0;
        }

        RegionBorrow borrow = RegionBorrow.current();
        return borrow != null && borrow.drainInboxes() > 0;
    }
}
