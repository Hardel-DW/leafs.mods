package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerChunkCache;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The server thread stops pumping chunk tasks between ticks — the chunk threads own that now. */
@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {

    @WrapOperation(method = "pollTaskInternal", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerChunkCache;pollTask()Z"))
    private boolean leafs$chunkThreadsPumpThemselves(ServerChunkCache cache, Operation<Boolean> original) {
        return false;
    }
}
