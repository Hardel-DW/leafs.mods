package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.ownership.RegionContext;
import fr.hardel.leafs.ownership.TickGuard;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Every structure query funnels through startsForStructure, and the advancement location predicate
 * runs it every 20 ticks per player. From a region worker the chunk read can refuse, so the query
 * degrades to not-found (Compromise #6) instead of crashing the region.
 */
@Mixin(StructureManager.class)
public abstract class StructureManagerMixin {

    @WrapMethod(method = "startsForStructure(Lnet/minecraft/world/level/ChunkPos;Ljava/util/function/Predicate;)Ljava/util/List;")
    private List<StructureStart> leafs$startsByChunkRefuseOverCrashing(ChunkPos pos, Predicate<Structure> matcher, Operation<List<StructureStart>> original) {
        return leafs$startsOrEmpty(() -> original.call(pos, matcher));
    }

    @WrapMethod(method = "startsForStructure(Lnet/minecraft/core/SectionPos;Lnet/minecraft/world/level/levelgen/structure/Structure;)Ljava/util/List;")
    private List<StructureStart> leafs$startsBySectionRefuseOverCrashing(SectionPos pos, Structure structure, Operation<List<StructureStart>> original) {
        return leafs$startsOrEmpty(() -> original.call(pos, structure));
    }

    private static List<StructureStart> leafs$startsOrEmpty(Supplier<List<StructureStart>> query) {
        if (!(RegionContext.current() instanceof RegionContext.Region)) {
            return query.get();
        }

        List<StructureStart> starts = TickGuard.callOrNull(query::get, "structure starts");
        return starts == null ? List.of() : starts;
    }
}
