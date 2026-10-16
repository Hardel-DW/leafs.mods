package fr.hardel.shared.transform.neoforge;

import fr.hardel.shared.transform.SharedTransform;
import net.neoforged.neoforgespi.transformation.BytecodeProvider;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;
import org.objectweb.asm.ClassReader;

import java.util.Set;

public final class SharedClassProcessor implements ClassProcessor {
    private static final ProcessorName NAME = new ProcessorName("leafs", "shared_collections");

    private volatile BytecodeProvider bytecode;

    @Override
    public ProcessorName name() {
        return NAME;
    }

    @Override
    public Set<ProcessorName> runsAfter() {
        return Set.of(ClassProcessorIds.COMPUTING_FRAMES, ClassProcessorIds.MIXIN);
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        return !context.empty() && SharedTransform.covers(context.type().getInternalName());
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        if (!SharedTransform.apply(context.node(), this::superName)) {
            return ComputeFlags.NO_REWRITE;
        }

        context.audit("shared collections");
        return ComputeFlags.SIMPLE_REWRITE;
    }

    @Override
    public void link(LinkContext context) {
        bytecode = context.bytecodeProvider();
    }

    private String superName(String internalName) {
        byte[] classBytes = bytesOf(internalName);
        return classBytes == null ? null : new ClassReader(classBytes).getSuperName();
    }

    private byte[] bytesOf(String internalName) {
        try {
            return bytecode.getByteCode(internalName.replace('/', '.'));
        } catch (ClassNotFoundException absent) {
            return null;
        }
    }
}
