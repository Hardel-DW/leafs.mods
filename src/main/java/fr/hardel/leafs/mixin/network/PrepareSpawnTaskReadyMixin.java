package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.hardel.leafs.network.JoinPreload;
import fr.hardel.leafs.network.SpawnEntityWait;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/** The flip into the game reuses what the waiting room prepared: no second disk read, no blocking entity wait. */
@Mixin(targets = "net.minecraft.server.network.config.PrepareSpawnTask$Ready")
public abstract class PrepareSpawnTaskReadyMixin {

    /** The second read of the playerdata is served from the tag {@code start} kept. */
    @WrapOperation(method = "spawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;loadPlayerData(Lnet/minecraft/server/players/NameAndId;)Ljava/util/Optional;"))
    private Optional<CompoundTag> leafs$serveKeptPlayerData(PlayerList playerList, NameAndId nameAndId, Operation<Optional<CompoundTag>> original) {
        return JoinPreload.servePlayerData(nameAndId, () -> original.call(playerList, nameAndId));
    }

    /** The preparation hold already established entity readiness; the vanilla managed block stays only as the net. */
    @WrapOperation(method = "spawn", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;waitForEntities(Lnet/minecraft/world/level/ChunkPos;I)V"))
    private void leafs$skipSatisfiedEntityWait(ServerLevel level, ChunkPos center, int radius, Operation<Void> original) {
        SpawnEntityWait.skipSatisfiedWait(level, center, radius, () -> original.call(level, center, radius));
    }
}
