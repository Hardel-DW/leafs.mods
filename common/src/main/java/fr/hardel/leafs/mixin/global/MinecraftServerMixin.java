package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.CommandEngine;
import fr.hardel.leafs.metrics.StageTimings;
import fr.hardel.leafs.metrics.TickStages;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/** The global drain, once per global tick after the level ticks, and the reload under every region. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    /** The swap of the reloaded data runs on the server thread with every region taken: they all read recipes, tags and functions each tick. */
    @WrapOperation(method = "reloadResources", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> leafs$applyReloadUnderEveryRegion(CompletableFuture<MinecraftServer.ReloadableResources> loaded, Consumer<MinecraftServer.ReloadableResources> apply, Executor server, Operation<CompletableFuture<Void>> original) {
        MinecraftServer self = (MinecraftServer) (Object) this;
        Consumer<MinecraftServer.ReloadableResources> borrowed = resources -> CommandEngine.runBorrowingAll(self, () -> apply.accept(resources));
        return original.call(loaded, borrowed, server);
    }

    /** Also hooked in {@code tickServer}: its pause-when-empty branch skips {@code tickChildren}, yet diverted tasks must still drain. */
    @Inject(method = {"tickChildren", "tickServer"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickConnection()V"))
    private void leafs$drainGlobalTasks(CallbackInfo callbackInfo) {
        TickingManager ticking = TickingManager.of((MinecraftServer) (Object) this);
        StageTimings globalStages = ticking.metrics().globalStages();
        globalStages.mark(TickStages.globalLevels);
        ticking.globalScheduler().drain();
        globalStages.mark(TickStages.globalDrain);
    }
}
