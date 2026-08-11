package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.JoinPreload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.network.config.PrepareSpawnTask;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/** The configuration phase prepares the join: the one playerdata read is kept and the player files preload off-thread. */
@Mixin(PrepareSpawnTask.class)
public abstract class PrepareSpawnTaskMixin {

    @Shadow
    @Final
    private NameAndId nameAndId;

    /** The one disk read of the join: its tag is kept and the stats and advancements reads start off-thread. */
    @WrapOperation(method = "start", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;loadPlayerData(Lnet/minecraft/server/players/NameAndId;)Ljava/util/Optional;"))
    private Optional<CompoundTag> leafs$captureAndPreload(PlayerList playerList, NameAndId nameAndId, Operation<Optional<CompoundTag>> original) {
        return JoinPreload.captureAndPreload(playerList, nameAndId, original.call(playerList, nameAndId));
    }

    @Inject(method = "close", at = @At("TAIL"))
    private void leafs$discardPreload(CallbackInfo callbackInfo) {
        JoinPreload.discard(nameAndId);
    }
}
