package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Pair;
import fr.hardel.leafs.ownership.OwnershipViolationException;
import fr.hardel.leafs.world.StructureReferences;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureCheckResult;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Set;

// Off the owner a search answers from placement and disk knowledge; the reference write lands on the owner at delivery.
@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @WrapMethod(method = "getStructureGeneratingAt")
    private static Pair<BlockPos, Holder<Structure>> leafs$locateWithoutTheLoad(Set<Holder<Structure>> structures, LevelReader level, StructureManager structureManager, boolean createReference, StructurePlacement config, ChunkPos chunkTarget, Operation<Pair<BlockPos, Holder<Structure>>> original) {
        try {
            return original.call(structures, level, structureManager, createReference, config, chunkTarget);
        } catch (OwnershipViolationException refusal) {
            for (Holder<Structure> structure : structures) {
                if (structureManager.checkStructurePresence(chunkTarget, structure.value(), config, createReference) != StructureCheckResult.START_PRESENT) {
                    continue;
                }

                if (createReference && level instanceof ServerLevel serverLevel) {
                    StructureReferences.writeAtDelivery(serverLevel, structureManager, chunkTarget, structure.value());
                }

                return Pair.of(config.getLocatePos(chunkTarget), structure);
            }

            return null;
        }
    }
}
