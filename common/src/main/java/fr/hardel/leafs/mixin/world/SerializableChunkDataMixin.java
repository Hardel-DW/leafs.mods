package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import fr.hardel.leafs.ticking.LevelRegions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.storage.SerializableChunkData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Save half of the two clocks: scheduled ticks pack delay-relative to the owning region's clock, not game time. */
@Mixin(SerializableChunkData.class)
public abstract class SerializableChunkDataMixin {

    @WrapOperation(method = "copyOf", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/ChunkAccess;getTicksForSerialization(J)Lnet/minecraft/world/level/chunk/ChunkAccess$PackedTicks;"))
    private static ChunkAccess.PackedTicks leafs$packAtOwnerClock(ChunkAccess chunk, long time, Operation<ChunkAccess.PackedTicks> original, @Local(argsOnly = true) ServerLevel level) {
        return original.call(chunk, LevelRegions.of(level).timeAt(chunk.getPos().x(), chunk.getPos().z(), time));
    }
}
