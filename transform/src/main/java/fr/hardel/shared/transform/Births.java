package fr.hardel.shared.transform;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.Frame;
import org.objectweb.asm.tree.analysis.SourceInterpreter;
import org.objectweb.asm.tree.analysis.SourceValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class Births {
    private static final String SHARED = "fr/hardel/shared/Shared";
    private static final String WRAP_DESCRIPTOR = "(Ljava/lang/Object;)Ljava/lang/Object;";

    private final MethodNode method;
    private final Frame<SourceValue>[] frames;

    private Births(MethodNode method, Frame<SourceValue>[] frames) {
        this.method = method;
        this.frames = frames;
    }

    static boolean rewrite(ClassNode owner, Set<String> shared) {
        boolean changed = false;
        for (MethodNode method : owner.methods) {
            List<FieldInsnNode> stores = storesOf(owner, method, shared);
            changed |= !stores.isEmpty() && new Births(method, analyze(owner, method)).share(stores);
        }

        return changed;
    }

    private static List<FieldInsnNode> storesOf(ClassNode owner, MethodNode method, Set<String> shared) {
        List<FieldInsnNode> stores = new ArrayList<>();
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof FieldInsnNode field && isStore(field) && field.owner.equals(owner.name) && shared.contains(SharedFields.key(field.name, field.desc))) {
                stores.add(field);
            }
        }

        return stores;
    }

    private static boolean isStore(FieldInsnNode field) {
        return field.getOpcode() == Opcodes.PUTSTATIC || field.getOpcode() == Opcodes.PUTFIELD;
    }

    private static Frame<SourceValue>[] analyze(ClassNode owner, MethodNode method) {
        try {
            return new Analyzer<>(new SourceInterpreter()).analyze(owner.name, method);
        } catch (AnalyzerException failure) {
            throw new IllegalStateException("Leafs could not read %s.%s%s to share its collections".formatted(owner.name, method.name, method.desc), failure);
        }
    }

    private boolean share(List<FieldInsnNode> stores) {
        Set<TypeInsnNode> births = new LinkedHashSet<>();
        Set<AbstractInsnNode> visited = new HashSet<>();
        stores.stream().filter(store -> frameAt(store) != null).forEach(store -> trace(top(store), births, visited));
        births.removeIf(birth -> !CollectionTypes.isCollection(birth.desc));
        List<MethodInsnNode> constructors = constructorsOf(births);
        constructors.forEach(this::wrapAfter);
        return !constructors.isEmpty();
    }

    private void trace(SourceValue value, Set<TypeInsnNode> births, Set<AbstractInsnNode> visited) {
        for (AbstractInsnNode source : value.insns) {
            if (!visited.add(source)) {
                continue;
            }

            switch (source.getOpcode()) {
                case Opcodes.NEW -> births.add((TypeInsnNode) source);
                case Opcodes.DUP, Opcodes.CHECKCAST, Opcodes.ASTORE -> trace(top(source), births, visited);
                case Opcodes.ALOAD -> trace(frameAt(source).getLocal(((VarInsnNode) source).var), births, visited);
                default -> {
                }
            }
        }
    }

    private List<MethodInsnNode> constructorsOf(Set<TypeInsnNode> births) {
        List<MethodInsnNode> constructors = new ArrayList<>();
        if (births.isEmpty()) {
            return constructors;
        }

        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.name.equals("<init>") && builds(call, births)) {
                constructors.add(call);
            }
        }

        return constructors;
    }

    private boolean builds(MethodInsnNode constructor, Set<TypeInsnNode> births) {
        Frame<SourceValue> frame = frameAt(constructor);
        if (frame == null) {
            return false;
        }

        int receiver = frame.getStackSize() - 1 - Type.getArgumentTypes(constructor.desc).length;
        Set<TypeInsnNode> built = new HashSet<>();
        trace(frame.getStack(receiver), built, new HashSet<>());
        return built.stream().anyMatch(birth -> births.contains(birth) && birth.desc.equals(constructor.owner));
    }

    private void wrapAfter(MethodInsnNode constructor) {
        InsnList wrap = new InsnList();
        wrap.add(new MethodInsnNode(Opcodes.INVOKESTATIC, SHARED, "wrap", WRAP_DESCRIPTOR, false));
        wrap.add(new TypeInsnNode(Opcodes.CHECKCAST, constructor.owner));
        method.instructions.insert(constructor, wrap);
    }

    private SourceValue top(AbstractInsnNode instruction) {
        Frame<SourceValue> frame = frameAt(instruction);
        return frame.getStack(frame.getStackSize() - 1);
    }

    private Frame<SourceValue> frameAt(AbstractInsnNode instruction) {
        return frames[method.instructions.indexOf(instruction)];
    }
}
