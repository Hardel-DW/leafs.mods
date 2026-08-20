package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.List;
import java.util.function.Predicate;

@Mixin(StructureManager.class)
public abstract class StructureManagerMixin {

    @Shadow
    @Final
    private LevelAccessor level;

    @WrapMethod(method = "checkStructurePresence")
    private StructureCheckResult leafs$presenceWithoutTheLoad(ChunkPos pos, Structure structure, StructurePlacement placement, boolean createReference, Operation<StructureCheckResult> original) {
        StructureCheckResult result = original.call(pos, structure, placement, createReference);
        if (result != StructureCheckResult.CHUNK_LOAD_NEEDED) {
            return result;
        }

        if (!(level instanceof ServerLevel serverLevel) || RegionChunkAccess.scheduling(serverLevel.getChunkSource().chunkMap).isUniversalOwner()) {
            return result;
        }

        return StructureCheckResult.START_PRESENT;
    }

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
