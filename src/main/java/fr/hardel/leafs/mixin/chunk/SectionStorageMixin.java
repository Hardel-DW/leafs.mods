package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.chunk.PoiLockAccess;
import fr.hardel.leafs.chunk.PoiVillageLock;
import fr.hardel.leafs.entity.ConcurrentLong2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** Concurrent storage facade. Carries the village lock shared with the POI subclass; held over the dirty-set save. */
@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin<R, P> implements PoiLockAccess {

    @Mutable
    @Shadow
    @Final
    private Long2ObjectMap<Optional<R>> storage;

    @Unique
    private final PoiVillageLock leafs$villageLock = new PoiVillageLock();

    @Override
    public PoiVillageLock leafs$villageLock() {
        return leafs$villageLock;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$swapForConcurrentFacade(CallbackInfo callbackInfo) {
        this.storage = new ConcurrentLong2ObjectMap<>();
    }

    @WrapMethod(method = "flushAll")
    private void leafs$flushUnderVillageLock(Operation<Void> original) {
        leafs$villageLock.runLocked(original::call);
    }
}
