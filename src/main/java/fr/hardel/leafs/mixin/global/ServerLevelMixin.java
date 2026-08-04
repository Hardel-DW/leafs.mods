package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.global.GlobalServerAccess;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.timers.TimerQueue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The {@code /schedule} TimerQueue fires datapack functions with arbitrary world reach, so its drain
 * moves from the overworld's serial tick into this tick's barrier window (Compromise #4). Fourth
 * mixin of this name (#3b ticking/, #9 world/, #38 entity/): different package, different concern.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    @WrapOperation(method = "tickTime", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/timers/TimerQueue;tick(Ljava/lang/Object;J)V"))
    private void leafs$timerQueueIntoWindow(TimerQueue<MinecraftServer> queue, Object server, long time, Operation<Void> original) {
        MinecraftServer minecraftServer = (MinecraftServer) server;
        ((GlobalServerAccess) minecraftServer).leafs$barrierWindow().enqueue(() -> queue.tick(minecraftServer, time));
    }
}
