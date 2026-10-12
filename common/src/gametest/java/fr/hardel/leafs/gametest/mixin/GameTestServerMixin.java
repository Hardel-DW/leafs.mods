package fr.hardel.leafs.gametest.mixin;

import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The regions tick at the tick rate on their own threads: the test server keeps that rate too, like a dedicated server, so a test tick is a world tick. */
@Mixin(GameTestServer.class)
public abstract class GameTestServerMixin {

    @Inject(method = "waitUntilNextTick", at = @At("TAIL"))
    private void leafs$waitForTheNextTick(CallbackInfo callbackInfo) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        server.managedBlock(() -> !((MinecraftServerAccessor) server).leafs$haveTime());
    }
}
