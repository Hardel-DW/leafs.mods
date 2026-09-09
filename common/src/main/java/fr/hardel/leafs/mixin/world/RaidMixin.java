package fr.hardel.leafs.mixin.world;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.world.AnchoredTicker;
import fr.hardel.leafs.world.RaidTickerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** A raid ticks as a block entity of its centre; raiders join and leave their wave from their own region. */
@Mixin(Raid.class)
public abstract class RaidMixin implements RaidTickerAccess {

    @Mutable
    @Shadow
    @Final
    private Map<Integer, Set<Raider>> groupRaiderMap;

    @Shadow
    public abstract BlockPos getCenter();

    @Shadow
    public abstract boolean isStopped();

    @Shadow
    public abstract void tick(ServerLevel level);

    @Unique
    private boolean leafs$ticker;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentWaves(CallbackInfo callbackInfo) {
        this.groupRaiderMap = new ConcurrentHashMap<>(this.groupRaiderMap);
    }

    @WrapOperation(method = "addWaveMob(Lnet/minecraft/server/level/ServerLevel;ILnet/minecraft/world/entity/raid/Raider;Z)Z", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object leafs$concurrentWave(Map<Integer, Set<Raider>> waves, Object wave, Function<Integer, Set<Raider>> vanilla, Operation<Object> original) {
        Function<Integer, Set<Raider>> concurrentWave = _ -> ConcurrentHashMap.newKeySet();
        return original.call(waves, wave, concurrentWave);
    }

    /** Registered on the owner of the center; the ticker leaves with the raid. */
    @Override
    public void leafs$ensureTicker(ServerLevel level) {
        if (leafs$ticker) {
            return;
        }

        leafs$ticker = true;
        ((ServerLevelRegionAccess) level).leafs$anchors().add(new AnchoredTicker(this::getCenter, () -> tick(level), this::isStopped));
    }
}
