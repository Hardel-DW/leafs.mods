package fr.hardel.leafs.gametest.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {

    @Inject(method = "waitUntilNextTick", at = @At("TAIL"))
    private void leafs$waitForTheNextTick(CallbackInfo callbackInfo) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        server.managedBlock(() -> !((MinecraftServerAccessor) server).leafs$haveTime());
    }
}
