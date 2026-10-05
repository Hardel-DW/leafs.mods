# Layout
A folder is a responsibility. An independent building block. The code lives in `src/main/java/fr/hardel/leafs/`. The `excess` folder in hardel lives next to the mod. It provides thread-safe collections unrelated to Minecraft.
The code shared by Fabric and NeoForge lives in `common`.

- `debug/` - The `/leafs` command
- `entity/` - The snapshot of the entities a region ticks, teleportations and entity persistence.
- `global/` - The command engine, the global scheduler, the state monitor, the locator bar, randomness.
- `metrics/` - Telemetry, server measurement, and the list of the stages of a tick.
- `mixin/` - Patches of Minecraft, one subfolder per building block. `compat/` fixes the mods that share a collection between threads.
- `network/` - Handles the per-player packet queues, routing, the per-region network tick, disconnection, connection preparation and the list of the chunks a player waits for.
- `region/` - Splits the world into sections and regions with merging and splitting, without any Minecraft dependency.
- `ticking/` - The region scheduler, the region clock, borrowing, the per-region crash report, the watchdog, the tick epochs.
- `world/` - Region tick, what a chunk ticks by itself, scheduled ticks, block events, block entities, and the little a region keeps, clock, randomness, neighbor updates, snapshots of its chunks and its entities, autosave by epoch.
- `chunk/` - The chunk engine, and the plugging of Firefly into the pool.

# Chunks Folder
- `pool/` - The chunk thread pool.
- `ticket/` - The three ticket graphs, `players`, `simulation`, `loading`.
- `level/` - The chunk levels propagated per graph.
- `holder/` - The holder table, waiting for an absent chunk, the generation stages.
- `owner/` - Who writes at a position of the world, a region's mailbox, chunks without a region.
- `view/` - Tied to the players' position.
- `disk/` - The chunk as bytes to the disk thread.
