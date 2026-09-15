package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.WorldTickContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The spawn census of a region counts the entities of that region; vanilla's counts the whole level from the server thread. */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {
    @WrapOperation(method = "createState", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;getAllEntities()Ljava/lang/Iterable;"))
    private static Iterable<Entity> leafs$regionEntities(ServerLevel level, Operation<Iterable<Entity>> original) {
        RegionWorldData worldData = WorldTickContext.activeFor(level);
        return worldData == null ? original.call(level) : worldData.entities().accessible();
    }
}
