package fr.hardel.shared.transform.neoforge;

import fr.hardel.shared.transform.SharedTransform;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforgespi.transformation.ClassProcessor;
import net.neoforged.neoforgespi.transformation.ClassProcessorIds;
import net.neoforged.neoforgespi.transformation.ProcessorName;

import java.util.Set;

/** Runs after Mixin, so the code other mods merged into a class is rewritten too. It claims only the classes the index found. */
public final class SharedClassProcessor implements ClassProcessor {
    private static final ProcessorName NAME = new ProcessorName("leafs", "shared_collections");

    private volatile SharedIndex index;

    @Override
    public ProcessorName name() {
        return NAME;
    }

    @Override
    public Set<ProcessorName> runsAfter() {
        return Set.of(ClassProcessorIds.COMPUTING_FRAMES, ClassProcessorIds.MIXIN);
    }

    @Override
    public void link(LinkContext context) {
        index = SharedIndex.scan(FMLLoader.getCurrent().getLoadingModList().getAllModFiles());
    }

    @Override
    public boolean handlesClass(SelectionContext context) {
        return !context.empty() && index.rewrites(context.type().getInternalName());
    }

    @Override
    public ComputeFlags processClass(TransformationContext context) {
        if (!SharedTransform.apply(context.node(), index::superName)) {
            return ComputeFlags.NO_REWRITE;
        }

        context.audit("shared collections");
        return ComputeFlags.SIMPLE_REWRITE;
    }
}
