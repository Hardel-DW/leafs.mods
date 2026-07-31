package fr.hardel.leafs.mixin.chunk;

import fr.hardel.leafs.ownership.RegionContext;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.Executor;

/** Player ticket-tracker mutations come from tracking on game threads — they hop to the chunk thread. */
@Mixin(DistanceManager.class)
public abstract class DistanceManagerMixin {

    @Shadow
    @Final
    private Executor mainThreadExecutor;

    @Inject(method = "addPlayer", at = @At("HEAD"), cancellable = true)
    private void leafs$hopAddPlayer(SectionPos pos, ServerPlayer player, CallbackInfo callbackInfo) {
        if (!(RegionContext.current() instanceof RegionContext.Chunk)) {
            mainThreadExecutor.execute(() -> ((DistanceManager) (Object) this).addPlayer(pos, player));
            callbackInfo.cancel();
        }
    }

    @Inject(method = "removePlayer", at = @At("HEAD"), cancellable = true)
    private void leafs$hopRemovePlayer(SectionPos pos, ServerPlayer player, CallbackInfo callbackInfo) {
        if (!(RegionContext.current() instanceof RegionContext.Chunk)) {
            mainThreadExecutor.execute(() -> ((DistanceManager) (Object) this).removePlayer(pos, player));
            callbackInfo.cancel();
        }
    }
}
