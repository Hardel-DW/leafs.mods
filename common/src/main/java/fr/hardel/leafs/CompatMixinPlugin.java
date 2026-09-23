package fr.hardel.leafs;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class CompatMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(Leafs.MOD_ID);

    @Override
    public void onLoad(String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        try {
            MixinService.getService().getBytecodeProvider().getClassNode(targetClassName);
            return true;
        } catch (ClassNotFoundException | IOException absent) {
            return false;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        String owner = "L%s;".formatted(targetClass.name);
        Set<String> wrapped = mixinInfo.getClassNode(0).methods.stream()
            .flatMap(method -> Stream.ofNullable(method.visibleAnnotations).flatMap(List::stream))
            .flatMap(injector -> Annotations.<AnnotationNode>getValue(injector, "at", true).stream())
            .map(at -> Annotations.getValue(at, "target", ""))
            .filter(target -> target.startsWith(owner))
            .map(target -> target.substring(owner.length(), target.indexOf(':')))
            .collect(Collectors.toSet());
        // A wrapped field write runs in a MixinExtras bridge, and the JVM refuses a final field written outside its initializer.
        targetClass.fields.stream().filter(field -> wrapped.contains(field.name)).forEach(field -> field.access &= ~Opcodes.ACC_FINAL);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        LOGGER.info("Leafs patched {} for concurrent access", targetClassName);
    }
}
