package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.BarrierWindow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.timers.TimerQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** TimerQueue drain moves into the barrier window because scheduled functions reach arbitrary state. */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @WrapOperation(method = "tickTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/timers/TimerQueue;tick(Ljava/lang/Object;J)V"))
    private void leafs$timerQueueIntoWindow(TimerQueue<MinecraftServer> queue, Object server, long time, Operation<Void> original) {
        MinecraftServer minecraftServer = (MinecraftServer) server;
        BarrierWindow.of(minecraftServer).enqueue(() -> queue.tick(minecraftServer, time));
    }
}
