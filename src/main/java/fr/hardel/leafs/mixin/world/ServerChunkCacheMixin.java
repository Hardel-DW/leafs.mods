package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.ticking.ChunkPumpAccess;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.world.RegionTickBody;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** Per-chunk tick work moved to region bodies; broadcast marks route to the owning unit; player moves defer to serial. */
@Mixin(ServerChunkCache.class)
public abstract class ServerChunkCacheMixin {

    @Shadow
    @Final
    private ServerLevel level;

    @Shadow
    private boolean spawnEnemies;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$bindPumpOwnership(CallbackInfo callbackInfo) {
        ServerChunkCache self = (ServerChunkCache) (Object) this;
        ((ChunkPumpAccess) (Object) self.mainThreadProcessor).leafs$bindOwnership(((ServerLevelRegionAccess) this.level).leafs$regions().ownership());
    }

    @WrapOperation(method = "tickChunks()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;tickChunks(Lnet/minecraft/util/profiling/ProfilerFiller;J)V"))
    private void leafs$serialChunkTickRemainder(ServerChunkCache instance, ProfilerFiller profiler, long timeDiff, Operation<Void> original) {
        RegionTickBody body = ((ServerLevelRegionAccess) this.level).leafs$regions().body();
        if (body == null) {
            original.call(instance, profiler, timeDiff);

            return;
        }

        body.tickSerialRemainder(this.spawnEnemies);
    }

    @WrapOperation(method = "blockChanged", at = @At(value = "INVOKE", target = "Ljava/util/Set;add(Ljava/lang/Object;)Z"))
    private boolean leafs$markOwnedBroadcastSet(Set<ChunkHolder> instance, Object holder, Operation<Boolean> original) {
        RegionWorldData data = WorldTickContext.activeFor(this.level);
        if (data == null) {
            return original.call(instance, holder);
        }

        return data.broadcastHolders().add((ChunkHolder) holder);
    }

    @Inject(method = "move", at = @At("HEAD"), cancellable = true)
    private void leafs$deferPlayerMoveToLevelSerial(ServerPlayer player, CallbackInfo callbackInfo) {
        LevelRegions regions = ((ServerLevelRegionAccess) this.level).leafs$regions();
        if (regions.body() == null || regions.ownership().isLevelSerialHeldByCurrentThread()) {
            return;
        }

        ServerChunkCache self = (ServerChunkCache) (Object) this;
        ((LeafsServerAccess) this.level.getServer()).leafs$ticking().submitToLevel(this.level, () -> {
            if (!player.isRemoved() && player.level() == this.level) {
                self.move(player);
            }
        });
        callbackInfo.cancel();
    }
}
