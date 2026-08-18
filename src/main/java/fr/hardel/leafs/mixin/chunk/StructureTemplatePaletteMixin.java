package fr.hardel.leafs.mixin.chunk;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(StructureTemplate.Palette.class)
public abstract class StructureTemplatePaletteMixin {

    @Mutable
    @Shadow
    @Final
    private Map<Block, List<StructureTemplate.StructureBlockInfo>> cache;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void leafs$concurrentBlockCache(CallbackInfo callbackInfo) {
        this.cache = new ConcurrentHashMap<>();
    }

    @WrapMethod(method = "jigsaws")
    private synchronized List<StructureTemplate.JigsawBlockInfo> leafs$lockedJigsawCache(Operation<List<StructureTemplate.JigsawBlockInfo>> original) {
        return original.call();
    }
}
