package fr.hardel.leafs.mixin.ticking;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.config.LeafsConfig;
import fr.hardel.leafs.ticking.LeafsServerAccess;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/** Hook only — the logic lives in ticking/TickingManager: each level tick runs through its region unit. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements LeafsServerAccess {

    @Unique
    private TickingManager leafs$ticking;

    @Override
    public TickingManager leafs$ticking() {
        if (leafs$ticking == null) {
            leafs$ticking = new TickingManager(LeafsConfig.get());
        }

        return leafs$ticking;
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void leafs$tickLevelThroughRegionUnit(ServerLevel level, BooleanSupplier haveTime, Operation<Void> original) {
        this.leafs$ticking().tickLevel(level, () -> original.call(level, haveTime));
    }

    @Inject(method = "stopServer", at = @At("TAIL"))
    private void leafs$shutdownTicking(CallbackInfo callbackInfo) {
        if (leafs$ticking != null) {
            leafs$ticking.shutdown();
        }
    }
}
