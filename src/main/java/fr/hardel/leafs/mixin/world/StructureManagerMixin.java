package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.function.Predicate;

@Mixin(StructureManager.class)
public abstract class StructureManagerMixin {

    @WrapMethod(method = "startsForStructure(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Predicate;)Ljava/util/List;")
    private List<StructureStart> leafs$startsByChunkRefuseOverCrashing(ChunkPos pos, Predicate<Structure> matcher, Operation<List<StructureStart>> original) {
        try {
            return original.call(pos, matcher);
        } catch (OwnershipViolationException refusal) {
            return List.of();
        }
    }

    @WrapMethod(method = "startsForStructure(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Ljava/util/List;")
    private List<StructureStart> leafs$startsBySectionRefuseOverCrashing(SectionPos pos, Structure structure, Operation<List<StructureStart>> original) {
        try {
            return original.call(pos, structure);
        } catch (OwnershipViolationException refusal) {
            return List.of();
        }
    }
}
