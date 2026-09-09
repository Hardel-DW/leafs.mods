package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.excess.ConcurrentShort2ObjectMap;
import it.unimi.dsi.fastutil.shorts.Short2ObjectMap;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiSection;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Mixin(PoiSection.class)
public abstract class PoiSectionMixin {

    @Mutable
    @Shadow
    @Final
    private Short2ObjectMap<PoiRecord> records;

    @Mutable
    @Shadow
    @Final
    private Map<Holder<PoiType>, Set<PoiRecord>> byType;

    @Inject(method = "<init>(Ljava/lang/Runnable;ZLjava/util/List;)V", at = @At("TAIL"))
    private void leafs$concurrentRecords(CallbackInfo callbackInfo) {
        Short2ObjectMap<PoiRecord> concurrent = new ConcurrentShort2ObjectMap<>();
        concurrent.putAll(this.records);
        this.records = concurrent;
        this.byType = new ConcurrentHashMap<>(this.byType);
    }

    @WrapOperation(method = "add(Lnet/minecraft/world/entity/ai/village/poi/PoiRecord;)Z", at = @At(value = "INVOKE", target = "Ljava/util/Map;computeIfAbsent(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;"))
    private Object leafs$concurrentTypeSet(Map<Holder<PoiType>, Set<PoiRecord>> byType, Object type, Function<Holder<PoiType>, Set<PoiRecord>> vanilla, Operation<Object> original) {
        Function<Holder<PoiType>, Set<PoiRecord>> concurrentSet = _ -> ConcurrentHashMap.newKeySet();
        return original.call(byType, type, concurrentSet);
    }
}
