package fr.hardel.leafs.mixin.ticking;

import fr.hardel.leafs.LeafsConfig;
import fr.hardel.leafs.metrics.SerialStage;
import fr.hardel.leafs.ticking.LevelRegions;
import fr.hardel.leafs.ticking.ServerLevelRegionAccess;
import fr.hardel.leafs.ticking.TickingManager;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

/**
 * Region carrier, plus the serial stage boundaries of the shrunk vanilla level tick. Field
 * initialiser runs after {@code super(...)}, so the regionizer exists before the first chunk holder.
 * The marks anchor on the vanilla calls still executed serially; a skipped anchor leaves its stage at zero.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin implements ServerLevelRegionAccess {

    @Unique
    private final LevelRegions leafs$regions = new LevelRegions(LeafsConfig.get());

    @Override
    public LevelRegions leafs$regions() {
        return leafs$regions;
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/border/WorldBorder;tick()V", shift = At.Shift.AFTER))
    private void leafs$markBorderStage(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        leafs$markSerial(SerialStage.BORDER);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;updateSkyBrightness()V", shift = At.Shift.AFTER))
    private void leafs$markWeatherStage(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        leafs$markSerial(SerialStage.WEATHER);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raids;tick(Lnet/minecraft/server/level/ServerLevel;)V"))
    private void leafs$markTimeStage(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        leafs$markSerial(SerialStage.TIME);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/raid/Raids;tick(Lnet/minecraft/server/level/ServerLevel;)V", shift = At.Shift.AFTER))
    private void leafs$markRaidsStage(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        leafs$markSerial(SerialStage.RAIDS);
    }

    @Inject(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/dimension/end/EnderDragonFight;tick()V", shift = At.Shift.AFTER))
    private void leafs$markDragonStage(BooleanSupplier haveTime, CallbackInfo callbackInfo) {
        leafs$markSerial(SerialStage.DRAGON);
    }

    @Unique
    private void leafs$markSerial(SerialStage stage) {
        ServerLevel level = (ServerLevel) (Object) this;
        TickingManager.of(level.getServer()).markSerial(level, stage);
    }
}
