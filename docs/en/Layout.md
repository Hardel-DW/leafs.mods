# Layout
A folder is a responsibility. An independent building block. The code lives in `src/main/java/fr/hardel/leafs/`. The `excess` folder in hardel lives next to the mod. It provides utility classes unrelated to Minecraft.

- `debug/` - The `/leafs` command
- `entity/` - The snapshot of the entities a region ticks, teleportations and entity persistence.
- `global/` - The command engine, the state monitor, borrowing during the Fabric API tick events, the locator bar, randomness.
- `metrics/` - Telemetry, server measurement. And the `leafs:tick_stage` registry.
- `mixin/` - Patches of Minecraft, of the Fabric API (`compat/`) and of ScalableLux (`light/`)
- `network/` - Handles the per-player packet queues, routing, the per-region network tick, disconnection and connection preparation.
- `region/` - Splits the world into sections and regions with merging and splitting, without any Minecraft dependency.
- `scheduler/` - Exposes the global scheduler whose destination is the owner of a chunk, region, pool or borrower
- `ticking/` - The region scheduler, the region clock, borrowing, the per-region crash report, the watchdog.
- `world/` - Region tick, what a chunk ticks by itself, scheduled ticks, block events, block entities, and the little a region keeps, clock, randomness, neighbor updates, snapshots of its chunks and its entities, autosave by epoch.
- `chunk/` - The chunk engine.

# Chunks Folder
- `pool/` - The chunk thread pool.
- `ticket/` - The three ticket graphs, `players`, `simulation`, `loading`.
- `level/` - The chunk levels propagated per graph.
- `holder/` - The holder table, waiting for an absent chunk, the generation stages.
- `owner/` - Who writes at a position of the world, a region's mailbox, chunks without a region.
- `view/` - Tied to the players' position.
- `disk/` - The chunk as bytes to the disk thread.
