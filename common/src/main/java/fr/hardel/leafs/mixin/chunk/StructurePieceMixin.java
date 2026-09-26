package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(StructurePiece.class)
public abstract class StructurePieceMixin {

    @WrapMethod(method = "generateBox(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/levelgen/structure/BoundingBox;IIIIIIZLnet/minecraft/util/RandomSource;"
        + "Lnet/minecraft/world/level/levelgen/structure/StructurePiece$BlockSelector;)V")
    private void leafs$oneBoxPerSelector(WorldGenLevel level, BoundingBox chunkBB, int x0, int y0, int z0, int x1, int y1, int z1, boolean skipAir, RandomSource random,
        StructurePiece.BlockSelector selector, Operation<Void> original) {
        SharedStateMonitor.run(selector, () -> original.call(level, chunkBB, x0, y0, z0, x1, y1, z1, skipAir, random, selector));
    }
}
