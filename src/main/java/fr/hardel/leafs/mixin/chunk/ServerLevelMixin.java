package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.PoiWriteReroute;
import fr.hardel.leafs.chunk.owner.Work;
import fr.hardel.leafs.ticking.RegionBorrow;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @Inject(method = "updatePOIOnBlockStateChange", at = @At("HEAD"), cancellable = true)
    private void leafs$poiWriteOnTheOwner(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo callbackInfo) {
        ServerLevel self = (ServerLevel) (Object) this;
        int chunkX = SectionPos.blockToSectionCoord(pos.getX());
        int chunkZ = SectionPos.blockToSectionCoord(pos.getZ());
        PoiWriteReroute.onBlockStateChange(self, pos, oldState, newState, write -> LevelChunks.of(self).owners().submit(chunkX, chunkZ, Work.GAME, write));
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
