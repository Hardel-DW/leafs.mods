package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.BarrierWindow;
import fr.hardel.leafs.metrics.DeferReason;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.timers.TimerCallback;
import net.minecraft.world.level.timers.TimerQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A due scheduled function reaches arbitrary state, so its execution rides the barrier window.
 * The hook sits on the callback call itself: the queue bookkeeping stays vanilla, and a tick with
 * nothing due never runs any Leafs code, so an idle server never opens the window for it.
 */
@Mixin(TimerQueue.class)
public abstract class TimerQueueMixin {

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/timers/TimerCallback;handle(Ljava/lang/Object;Lnet/minecraft/world/level/timers/TimerQueue;J)V"))
    private void leafs$callbackIntoWindow(TimerCallback<Object> callback, Object context, TimerQueue<Object> queue, long time, Operation<Void> original) {
        if (context instanceof MinecraftServer server) {
            BarrierWindow.of(server).enqueue(DeferReason.SCHEDULED_FUNCTIONS, () -> original.call(callback, context, queue, time));
            return;
        }

        original.call(callback, context, queue, time);
    }
}
