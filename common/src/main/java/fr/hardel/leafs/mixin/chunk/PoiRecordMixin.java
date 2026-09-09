package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import org.spongepowered.asm.mixin.Mixin;

/** A villager claims a bed or a job from its own region, the record may sit in another: the ticket count moves under the record's monitor. */
@Mixin(PoiRecord.class)
public abstract class PoiRecordMixin {

    @WrapMethod(method = "acquireTicket")
    private boolean leafs$acquireUnderTheMonitor(Operation<Boolean> original) {
        return SharedStateMonitor.call(this, original::call);
    }

    @WrapMethod(method = "releaseTicket")
    private boolean leafs$releaseUnderTheMonitor(Operation<Boolean> original) {
        return SharedStateMonitor.call(this, original::call);
    }
}
