package fr.hardel.leafs.mixin.global;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import fr.hardel.leafs.global.SharedStateMonitor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.spongepowered.asm.mixin.Mixin;

/** Per-instance lock: maps carried by players in different regions serialize their ticks and writes. */
@Mixin(MapItemSavedData.class)
public abstract class MapItemSavedDataMixin {

    @WrapMethod(method = "tickCarriedBy")
    private void leafs$lockedTickCarriedBy(Player player, ItemStack stack, ItemFrame frame, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(player, stack, frame));
    }

    @WrapMethod(method = "getUpdatePacket")
    private Packet<?> leafs$lockedGetUpdatePacket(MapId id, Player player, Operation<Packet<?>> original) {
        return SharedStateMonitor.call(this, () -> original.call(id, player));
    }

    @WrapMethod(method = "getHoldingPlayer")
    private MapItemSavedData.HoldingPlayer leafs$lockedGetHoldingPlayer(Player player, Operation<MapItemSavedData.HoldingPlayer> original) {
        return SharedStateMonitor.call(this, () -> original.call(player));
    }

    @WrapMethod(method = "toggleBanner")
    private boolean leafs$lockedToggleBanner(LevelAccessor level, BlockPos pos, Operation<Boolean> original) {
        return SharedStateMonitor.call(this, () -> original.call(level, pos));
    }

    @WrapMethod(method = "checkBanners")
    private void leafs$lockedCheckBanners(BlockGetter level, int x, int z, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(level, x, z));
    }

    @WrapMethod(method = "removedFromFrame")
    private void leafs$lockedRemovedFromFrame(BlockPos pos, int entityId, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(pos, entityId));
    }

    @WrapMethod(method = "updateColor")
    private boolean leafs$lockedUpdateColor(int x, int y, byte color, Operation<Boolean> original) {
        return SharedStateMonitor.call(this, () -> original.call(x, y, color));
    }

    @WrapMethod(method = "setColor")
    private void leafs$lockedSetColor(int x, int y, byte color, Operation<Void> original) {
        SharedStateMonitor.run(this, () -> original.call(x, y, color));
    }

    @WrapMethod(method = "locked")
    private MapItemSavedData leafs$lockedLockedCopy(Operation<MapItemSavedData> original) {
        return SharedStateMonitor.call(this, original::call);
    }

    @WrapMethod(method = "scaled")
    private MapItemSavedData leafs$lockedScaledCopy(Operation<MapItemSavedData> original) {
        return SharedStateMonitor.call(this, original::call);
    }

    @WrapMethod(method = "isTrackedCountOverLimit")
    private boolean leafs$lockedIsTrackedCountOverLimit(int limit, Operation<Boolean> original) {
        return SharedStateMonitor.call(this, () -> original.call(limit));
    }
}
