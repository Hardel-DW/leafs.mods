package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.ticking.RegionBorrow;
import fr.hardel.leafs.chunk.PoiWriteReroute;
import fr.hardel.leafs.chunk.SectionStorageAccess;
import fr.hardel.leafs.ticking.TickingBinding;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A POI write hops to the owner of its block; a custom spawner borrows what it touches. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    /** The POI storage is built level-blind; its contract gate needs the level. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$bindPoiStorageLevel(CallbackInfo callbackInfo) {
        ServerLevel self = (ServerLevel) (Object) this;
        ((SectionStorageAccess) self.getPoiManager()).leafs$bindLevel(self);
    }

    @Inject(method = "updatePOIOnBlockStateChange", at = @At("HEAD"), cancellable = true)
    private void leafs$poiWriteOnTheOwner(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo callbackInfo) {
        ServerLevel self = (ServerLevel) (Object) this;
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        PoiWriteReroute.onBlockStateChange(self, pos, oldState, newState, task -> TickingBinding.of(self).toOwner(chunkX, chunkZ, task));
        callbackInfo.cancel();
    }

    /** A custom spawner probes terrain near a random player and spawns there: the server thread takes the regions it touches, like a command. */
    @WrapOperation(method = "tickCustomSpawners", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/CustomSpawner;tick(Lnet/minecraft/server/level/ServerLevel;Z)V"))
    private void leafs$borrowingSpawner(CustomSpawner spawner, ServerLevel level, boolean spawnEnemies, Operation<Void> original) {
        RegionBorrow.hold(_ -> {
            original.call(spawner, level, spawnEnemies);
            return null;
        });
    }
}
