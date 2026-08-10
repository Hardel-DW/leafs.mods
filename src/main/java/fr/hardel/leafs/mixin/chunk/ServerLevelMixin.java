package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.DegradedChunkReads;
import fr.hardel.leafs.chunk.PoiWriteReroute;
import fr.hardel.leafs.ownership.TickGuard;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProgressListener;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Save takes the level's exclusion; POI writes hop to the level's single mutator. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @WrapMethod(method = "save")
    private void leafs$saveUnderExclusion(@Nullable ProgressListener progressListener, boolean flush, boolean noSave, Operation<Void> original) {
        ((ServerLevelRegionAccess) this).leafs$regions().ownership().runExclusive(() -> original.call(progressListener, flush, noSave));
    }

    @Inject(method = "updatePOIOnBlockStateChange", at = @At("HEAD"), cancellable = true)
    private void leafs$poiWriteToLevelSerial(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo callbackInfo) {
        ServerLevel self = (ServerLevel) (Object) this;
        PoiWriteReroute.onBlockStateChange(self, pos, oldState, newState, task -> ((LeafsServerAccess) self.getServer()).leafs$ticking().submitToLevel(self, task));
        callbackInfo.cancel();
    }

    /** A custom spawner probes terrain near a random player; on the serial thread that must refuse, never sync-load. */
    @WrapOperation(method = "tickCustomSpawners", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/CustomSpawner;tick(Lnet/minecraft/server/level/ServerLevel;Z)V"))
    private void leafs$refusableSpawner(CustomSpawner spawner, ServerLevel level, boolean spawnEnemies, Operation<Void> original) {
        DegradedChunkReads.run(() -> TickGuard.tickOrSkip(target -> original.call(target, level, spawnEnemies), spawner));
    }
}
