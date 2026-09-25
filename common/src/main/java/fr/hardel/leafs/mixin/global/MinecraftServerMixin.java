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

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @WrapOperation(method = "reloadResources", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;thenAcceptAsync(Ljava/util/function/Consumer;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"))
    private CompletableFuture<Void> leafs$applyReloadUnderEveryRegion(CompletableFuture<MinecraftServer.ReloadableResources> loaded, Consumer<MinecraftServer.ReloadableResources> apply, Executor server, Operation<CompletableFuture<Void>> original) {
        MinecraftServer self = (MinecraftServer) (Object) this;
        Consumer<MinecraftServer.ReloadableResources> borrowed = resources -> CommandEngine.runBorrowingAll(self, () -> apply.accept(resources));
        return original.call(loaded, borrowed, server);
    }

    @Inject(method = {"tickChildren", "tickServer"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickConnection()V"))
    private void leafs$drainGlobalTasks(CallbackInfo callbackInfo) {
        TickingManager ticking = TickingManager.of((MinecraftServer) (Object) this);
        StageTimings globalStages = ticking.metrics().globalStages();
        globalStages.mark(TickStages.globalLevels);
        ticking.globalScheduler().drain();
        globalStages.mark(TickStages.globalDrain);
    }
}
