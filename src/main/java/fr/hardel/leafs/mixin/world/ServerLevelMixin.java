package fr.hardel.leafs.mixin.world;

import fr.hardel.leafs.world.RegionClock;
import fr.hardel.leafs.world.RegionWorldData;
import fr.hardel.leafs.world.ServerLevelWorldAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.ticks.LevelTicks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Builds the region world data at construction and swaps the scheduled-tick indexes for the region
 * ones - registration, scheduling and the drain all flow through the swapped fields untouched.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements ServerLevelWorldAccess {

    @Mutable
    @Shadow
    @Final
    private LevelTicks<Block> blockTicks;

    @Mutable
    @Shadow
    @Final
    private LevelTicks<Fluid> fluidTicks;

    @Unique
    private RegionWorldData leafs$worldData;

    @Override
    public RegionWorldData leafs$worldData() {
        return leafs$worldData;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$createRegionWorldData(CallbackInfo callbackInfo) {
        ServerLevel self = (ServerLevel) (Object) this;
        this.leafs$worldData = new RegionWorldData(new RegionClock(self::getGameTime), self::isPositionTickingWithEntitiesLoaded, self.blockEvents, self.getRandom(), self.neighborUpdater, self.getChunkSource().chunkHoldersToBroadcast);
        this.blockTicks = leafs$worldData.blockTicks();
        this.fluidTicks = leafs$worldData.fluidTicks();
    }
}
