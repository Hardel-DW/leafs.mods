package fr.hardel.leafs.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.thread.SerialEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerChunkEvents.class)
public abstract class FabricChunkEventsShim {

    @WrapOperation(method = "*", at = @At(value = "INVOKE",
        target = "Lnet/fabricmc/fabric/api/event/lifecycle/v1/ServerChunkEvents$Load;onChunkLoad(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;Z)V"))
    private static void leafs$loadOneAtATime(ServerChunkEvents.Load callback, ServerLevel level, LevelChunk chunk, boolean generated, Operation<Void> original) {
        SerialEvents.deliver(() -> original.call(callback, level, chunk, generated));
    }

    @WrapOperation(method = "*", at = @At(value = "INVOKE",
        target = "Lnet/fabricmc/fabric/api/event/lifecycle/v1/ServerChunkEvents$Unload;onChunkUnload(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/level/chunk/LevelChunk;)V"))
    private static void leafs$unloadOneAtATime(ServerChunkEvents.Unload callback, ServerLevel level, LevelChunk chunk, Operation<Void> original) {
        SerialEvents.deliver(() -> original.call(callback, level, chunk));
    }
}
