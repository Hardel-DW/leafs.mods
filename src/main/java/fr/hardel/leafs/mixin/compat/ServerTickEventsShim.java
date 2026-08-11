package fr.hardel.leafs.mixin.compat;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.FabricTickEventsBarrier;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.util.profiling.ProfilerFiller;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Brackets the two Fabric API server tick event emissions of {@code tickServer} with the region
 * barrier when subscribers exist. The anchors are the vanilla calls just before each emission point
 * and just after it, so the pause covers exactly the fabric-lifecycle-events injections, whatever
 * their mixin priority.
 */
@Mixin(MinecraftServer.class)
public abstract class ServerTickEventsShim {

    @Unique
    private FabricTickEventsBarrier leafs$tickEvents;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createTickEventsBarrier(CallbackInfo callbackInfo) {
        leafs$tickEvents = new FabricTickEventsBarrier(((LeafsServerAccess) this).leafs$ticking().barrier());
    }

    /** {@code START_SERVER_TICK} fires between the tick-rate manager tick and the {@code tickChildren} call. */
    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/ServerTickRateManager;tick()V"))
    private void leafs$openBeforeStartEvent(ServerTickRateManager manager, Operation<Void> original) {
        original.call(manager);
        leafs$tickEvents.openForTickStart();
    }

    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickChildren(Ljava/util/function/BooleanSupplier;)V"))
    private void leafs$closeAfterStartEvent(MinecraftServer server, BooleanSupplier haveTime, Operation<Void> original) {
        leafs$tickEvents.close();
        original.call(server, haveTime);
    }

    /** {@code END_SERVER_TICK} fires at the tail of {@code tickServer}, after the tallying profiler pop. */
    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiling/ProfilerFiller;pop()V"))
    private void leafs$openBeforeEndEvent(ProfilerFiller profiler, Operation<Void> original) {
        original.call(profiler);
        leafs$tickEvents.openForTickEnd();
    }

    @WrapOperation(method = "processPacketsAndTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/MinecraftServer;tickServer(Ljava/util/function/BooleanSupplier;)V"))
    private void leafs$closeAfterEndEvent(MinecraftServer server, BooleanSupplier haveTime, Operation<Void> original) {
        try {
            original.call(server, haveTime);
        } finally {
            leafs$tickEvents.close();
        }
    }
}
