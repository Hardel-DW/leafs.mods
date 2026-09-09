package fr.hardel.leafs.chunk.holder;

import com.mojang.logging.LogUtils;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.metrics.MinuteCounter;
import net.minecraft.server.level.GenerationChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.StaticCache2D;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.status.ChunkStatusTasks;
import net.minecraft.world.level.chunk.status.WorldGenContext;
import net.minecraft.world.level.storage.TagValueInput;
import org.slf4j.Logger;

import java.util.concurrent.CompletableFuture;

/** Vanilla's FULL body in two halves: the pool builds the LevelChunk, the owner publishes it into the live world. */
public final class FullStep {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final ChunkOwners owners;
    private final MinuteCounter completed;

    public FullStep(ChunkOwners owners, MinuteCounter completed) {
        this.owners = owners;
        this.completed = completed;
    }

    public CompletableFuture<ChunkAccess> apply(WorldGenContext context, StaticCache2D<GenerationChunkHolder> cache, ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        LevelChunk built = build(context.level(), cache.get(pos.x(), pos.z()), (ProtoChunk) chunk);
        return CompletableFuture.supplyAsync(() -> publish(context, built), owners.executor(pos.x(), pos.z()));
    }

    private static LevelChunk build(ServerLevel level, GenerationChunkHolder holder, ProtoChunk protoChunk) {
        LevelChunk levelChunk;
        if (protoChunk instanceof ImposterProtoChunk imposter) {
            levelChunk = imposter.getWrapped();
        } else {
            levelChunk = new LevelChunk(level, protoChunk, _ -> loadEntities(level, protoChunk));
            holder.replaceProtoChunk(new ImposterProtoChunk(levelChunk, false));
        }

        levelChunk.setFullStatus(holder::getFullStatus);
        return levelChunk;
    }

    private ChunkAccess publish(WorldGenContext context, LevelChunk chunk) {
        chunk.runPostLoad();
        chunk.setLoaded(true);
        chunk.registerAllBlockEntitiesAfterLevelLoad();
        chunk.registerTickContainerInLevel(context.level());
        chunk.setUnsavedListener(context.unsavedListener());
        completed.increment();
        return chunk;
    }

    private static void loadEntities(ServerLevel level, ProtoChunk protoChunk) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(protoChunk.problemPath(), LOGGER)) {
            ChunkStatusTasks.postLoadProtoChunk(level, TagValueInput.create(reporter, level.registryAccess(), protoChunk.getEntities()));
        }
    }
}
