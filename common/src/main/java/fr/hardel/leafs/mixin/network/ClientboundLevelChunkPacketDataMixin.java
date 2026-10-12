package fr.hardel.leafs.mixin.network;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientboundLevelChunkPacketData.class)
public abstract class ClientboundLevelChunkPacketDataMixin {

    @WrapOperation(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;calculateChunkSize(Lnet/minecraft/world/level/chunk/LevelChunk;)I"))
    private int leafs$writeEachSectionOnce(LevelChunk chunk, Operation<Integer> measure, @Share("sections") LocalRef<ByteBuf> sections) {
        FriendlyByteBuf written = new FriendlyByteBuf(Unpooled.buffer(measure.call(chunk)));
        for (LevelChunkSection section : chunk.getSections()) {
            section.write(written);
        }

        sections.set(written);
        return written.readableBytes();
    }

    @WrapOperation(method = "<init>(Lnet/minecraft/world/level/chunk/LevelChunk;)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;extractChunkData(Lnet/minecraft/network/FriendlyByteBuf;Lnet/minecraft/world/level/chunk/LevelChunk;)V"))
    private void leafs$copyTheWrittenSections(FriendlyByteBuf buffer, LevelChunk chunk, Operation<Void> extract, @Share("sections") LocalRef<ByteBuf> sections) {
        buffer.writeBytes(sections.get());
    }
}
