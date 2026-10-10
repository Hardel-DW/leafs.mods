package fr.hardel.leafs.world;

import fr.hardel.leafs.chunk.LevelChunks;
import fr.hardel.leafs.chunk.RegionChunkAccess;
import fr.hardel.leafs.chunk.holder.HolderTable;
import fr.hardel.leafs.chunk.owner.ChunkOwners;
import fr.hardel.leafs.region.Region;
import fr.hardel.leafs.ticking.RegionTime;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkLevel;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntitySection;
import net.minecraft.world.level.entity.EntitySectionStorage;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RegionChunks {
    private record Read(ChunkHolder holder, @Nullable LevelChunk ticking, boolean simulated, List<EntitySection<Entity>> entities) {}
    private final Long2ObjectOpenHashMap<List<Read>> sections = new Long2ObjectOpenHashMap<>();
    private final ReferenceLinkedOpenHashSet<ChunkHolder> holders = new ReferenceLinkedOpenHashSet<>();
    private final ReferenceLinkedOpenHashSet<LevelChunk> ticking = new ReferenceLinkedOpenHashSet<>();
    private final ReferenceLinkedOpenHashSet<LevelChunk> simulated = new ReferenceLinkedOpenHashSet<>();
    private final ReferenceLinkedOpenHashSet<EntitySection<Entity>> entitySections = new ReferenceLinkedOpenHashSet<>();
    private boolean unbound = true;
    private int minX;
    private int minZ;
    private int maxX;
    private int maxZ;

    public void refresh(Region<?> region, ServerLevel level, RegionTime time) {
        LevelChunks chunks = LevelChunks.of(level);
        region.takeChanged(section -> read(section, chunks.holders().table(), chunks.owners(), level, time));
        if (unbound) {
            bound();
        }
    }

    private void read(long section, HolderTable table, ChunkOwners owners, ServerLevel level, RegionTime time) {
        forget(section);
        List<Read> reads = new ArrayList<>();
        table.forEachHolderIn(section, holder -> {
            ChunkPos pos = holder.getPos();
            LevelChunk full = RegionChunkAccess.fullChunkOrNull(holder);
            if (!owners.heldElsewhere(pos.x(), pos.z()) && full != null) {
                time.adopt(full);
                reads.add(read(holder, level));
            }
        });

        if (reads.isEmpty()) {
            return;
        }

        unbound = true;
        sections.put(section, reads);
        for (Read read : reads) {
            holders.add(read.holder());
            if (read.ticking() != null) {
                ticking.add(read.ticking());
            }

            if (read.simulated()) {
                simulated.add(read.ticking());
            }

            entitySections.addAll(read.entities());
        }
    }

    private static Read read(ChunkHolder holder, ServerLevel level) {
        ChunkPos pos = holder.getPos();
        LevelChunk chunk = ChunkLevel.isBlockTicking(holder.getTicketLevel()) ? holder.getTickingChunk() : null;
        EntitySectionStorage<Entity> storage = level.entityManager.sectionStorage;
        List<EntitySection<Entity>> entities = new ArrayList<>();
        for (long key : storage.getChunkSections(pos.x(), pos.z())) {
            EntitySection<Entity> section = storage.sections.get(key);
            if (section != null) {
                entities.add(section);
            }
        }

        return new Read(holder, chunk, chunk != null && level.shouldTickBlocksAt(pos.pack()), entities);
    }

    /** A section that left the region takes its chunks with it. */
    public void forget(long section) {
        List<Read> reads = sections.remove(section);
        if (reads == null) {
            return;
        }

        unbound = true;
        for (Read read : reads) {
            holders.remove(read.holder());
            ticking.remove(read.ticking());
            simulated.remove(read.ticking());
            entitySections.removeAll(read.entities());
        }
    }

    private void bound() {
        unbound = false;
        minX = Integer.MAX_VALUE;
        minZ = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        maxZ = Integer.MIN_VALUE;
        for (ChunkHolder holder : holders) {
            ChunkPos pos = holder.getPos();
            minX = Math.min(minX, pos.x());
            minZ = Math.min(minZ, pos.z());
            maxX = Math.max(maxX, pos.x());
            maxZ = Math.max(maxZ, pos.z());
        }
    }

    public Collection<ChunkHolder> holders() {
        return holders;
    }

    public Collection<LevelChunk> ticking() {
        return ticking;
    }

    public Collection<LevelChunk> simulated() {
        return simulated;
    }

    public Collection<EntitySection<Entity>> entitySections() {
        return entitySections;
    }

    public boolean within(ChunkPos chunk, int margin) {
        return chunk.x() >= minX - margin && chunk.x() <= maxX + margin && chunk.z() >= minZ - margin && chunk.z() <= maxZ + margin;
    }
}
