package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.JoinPreload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/** The flip into the game reuses what the waiting room prepared: no second disk read. */
@Mixin(targets = "net.minecraft.server.network.config.PrepareSpawnTask$Ready")
public abstract class PrepareSpawnTaskReadyMixin {

    /** The second read of the playerdata is served from the tag {@code start} kept. */
    @WrapOperation(method = "spawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;loadPlayerData(Lnet/minecraft/server/players/NameAndId;)Ljava/util/Optional;"))
    private Optional<CompoundTag> leafs$serveKeptPlayerData(PlayerList playerList, NameAndId nameAndId, Operation<Optional<CompoundTag>> original) {
        return JoinPreload.servePlayerData(nameAndId, () -> original.call(playerList, nameAndId));
    }
}
