package fr.hardel.leafs.neoforge.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.TickEventBorrow;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** Brackets NeoForge's two server tick events with a borrow: the bus does not say who listens, so every emission borrows. */
@Mixin(MinecraftServer.class)
public abstract class ServerTickEventsShim {

    @Unique
    private TickEventBorrow leafs$tickEvents;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createTickEvents(CallbackInfo callbackInfo) {
        leafs$tickEvents = new TickEventBorrow(TickingManager.of((MinecraftServer) (Object) this).metrics().tickEventBorrows());
    }

    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;fireServerTickPre(Ljava/util/function/BooleanSupplier;Lnet/minecraft/server/MinecraftServer;)V"))
    private void leafs$borrowAroundPre(BooleanSupplier haveTime, MinecraftServer server, Operation<Void> original) {
        leafs$emit(() -> original.call(haveTime, server));
    }

    @WrapOperation(method = "tickServer", at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/event/EventHooks;fireServerTickPost(Ljava/util/function/BooleanSupplier;Lnet/minecraft/server/MinecraftServer;)V"))
    private void leafs$borrowAroundPost(BooleanSupplier haveTime, MinecraftServer server, Operation<Void> original) {
        leafs$emit(() -> original.call(haveTime, server));
    }

    @Unique
    private void leafs$emit(Runnable emission) {
        leafs$tickEvents.open();
        try {
            emission.run();
        } finally {
            leafs$tickEvents.close();
        }
    }
}
