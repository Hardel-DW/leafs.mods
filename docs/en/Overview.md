# How vanilla Minecraft works
Minecraft handles the world with a single thread, sequentially. So the more players, entities, redstone machines and players generating terrain there are, the more you strain performance. Since everything is shared, each player impacts the other players.
The server runs loops of 50ms, 20 times per second. The famous 20 TPS. When there are too many players the server can take longer than these 50ms to process a tick, so the TPS drops. This slows down everything without exception, that is the lag you feel.

## Problems
So it means that if you have 6 or 12 or 50 cores the game takes a single one to handle everything. So if you buy more expensive hardware, you gain nothing. Minecraft was developed 15 years ago and at the time having several cores was not common, so the game was designed for its time.
Leafs imposes a simple rule, by studying Amdahl's and Gustafson's laws, the mod must guarantee that the number of players/regions scales with the available RAM/threads. If we want more players we buy more RAM or a better CPU, linearly.

## The solution: regions
Leafs looks at the simulated chunks, those around the player defined by the `simulation distance`. The world is cut into a fixed grid of 2x2 chunks defined by the config (`section_size`), and a section becomes active when one of its chunks is simulated. Active sections that touch each other are grouped into a region.

Regions own a ring of 1 section around the active sections, which they do not tick. Two rings of two different regions never overlap.

Two distant players are therefore each in a different region. Regions move with the players. Two players getting closer see their regions merge into one. A region that stretches until it cuts into two pieces splits. These operations happen between two ticks, never in the middle of a tick.

A region owns its chunks, its entities, its players, its block entities, the network packets of its players and its own random generator. During its tick, nobody else writes into its content. Reading from another region stays free for everyone.

# Thread
**The vanilla server thread still exists.** Regions tick at the same time as it does. Once per tick it does what is global by nature, the world time, the weather, the border, the player list and the autosave trigger. It also runs every command. Its cost is fixed and minimal, without depending on the number of chunks or entities. Only the player list grows with them, and its cost per player is tiny.
> The term pool refers to a group of threads: the region pool, the chunk pool.

## Region workers
A region is not a thread! A region is a task. Regions wait in a single list, sorted by the time of their next tick. A free worker takes the first one and ticks it. A worker busy with a big region blocks nobody, the others take over.

TPS in vanilla is global, in Leafs it is per region. Each region has its own TPS. If a region is heavier its TPS drops, this does not affect the other regions which keep their TPS at max.
- The time of day stays global. Handled by the shared global thread. So the weather and the sunset go at the same speed for everyone whatever your TPS.
- Everything that measures a relative duration, a furnace cooking, entities, redstone, is handled by the region clock. A furnace will not cook at the same speed in two regions. It all depends on the TPS.
- A region never waits for another thread. It only waits for the pool for a chunk, because the pool never waits for anything in return. A player, or an entity, is held by a single thread at a time. A region that finds a player held by another thread skips them and picks them up on the next tick.

Connection and disconnection go through the server thread, which borrows the player's region. The respawn goes from the player's region to the region of their respawn point, or to the server thread if no region covers that point.

## Chunk workers
Chunk workers are completely independent from region workers. They generate, light, load and unload chunks, and prepare the bytes to write to disk. The vanilla disk thread now only reads and writes those bytes.

These workers run at the lowest system priority on the operating system. When the machine no longer has enough resources for everyone, region ticks go first, because they have a 50 ms deadline to meet. Chunks take the rest. To keep it simple:
- A player who explores no longer lags the other players, even those of their own region.
- A very dense area, with a low TPS, does not affect the world generation speed, so they can keep moving smoothly.
- When a thread needs a chunk that is not there yet, it asks the pool, which puts it ahead of everything else, and it waits. The received chunk stays loaded until the end of the tick or of the command, like in vanilla.

# Reading/Writing
Minecraft is made of `chunks` of 16x16 blocks. A region is a group of chunks that tick together, each chunk has an owner, the only one allowed to write.
- If a region simulates the chunk, the region is the owner.
- Otherwise nobody is, and the first thread that wants to write there takes it for the duration of its write, then gives it back.
- Any thread reads any chunk, at any time. A mod that looks at a block on the other side of the world reads it directly.

**Writing a block** - Three cases.
- The block is at your place, in a chunk of your region. You write it right away, like in vanilla.
- The block is in a chunk without a region, a dimension not yet generated, an area without a player. You take the chunk, you write, you read your block back, like in vanilla.
- The block is in a chunk another region is currently handling. You cannot touch it during its tick, you send it mail, and it places the block on its next tick. If you read the block back right away, you still see the old one. `Tradeoff 4`

# Saving
- The `/save-all flush` command and the server shutdown, the server thread freezes every region for the duration of the save, like vanilla freezes the server.
- The periodic autosave is done by the regions.

# Borrows and Mail
Two simple multithreading concepts of Leafs.
First of all a rule to understand, the server thread never touches a region without borrowing it, `commands`, `Fabric events`, `arrival`, `departure`.

**Mail**: each region has a mailbox. What the other regions want to do at its place waits in there, it does it at the end of its tick, in order of arrival.
The mailbox has two queues:
- Chunk work. Publishing a generated chunk, tearing it down, saving it.
- Game work. Placing a block, teleporting, respawning. It may need a chunk not yet loaded, so it may wait.

**Borrowing**: It is useful in particular to `commands`. The server thread can create a borrow by targeting entities/chunks, which borrows their regions. The server thread then does the work itself, in the same order as vanilla, and gives everything back at the end.

# Commands
Every command runs on the server thread, whoever launches it.
It borrows a region the moment the command touches one of its chunks or one of its entities, keeps it until the end of the command, then gives it back.

What the command touches decides what it borrows:
- A `/say` borrows nothing.
- A `/give @a` borrows the regions where there are players.
- A `/setblock` borrows the region of the targeted chunk, and loads the chunk first if needed. This load blocks the server thread, like in vanilla.
- A `/kill @e` borrows every region, because that is what the command means.
A datapack therefore costs exactly what it costs in vanilla.

# Connection and disconnection
The server thread handles the arrival and departure of a player. It borrows the player's region for the duration of the operation, that is less than a millisecond. The other regions see nothing, no player feels any difference, even on a wave of 100 connections.

# Mod compatibility.
Primitives are the methods in the Minecraft code that are the lowest and the most used, where the most traffic goes through them.
Leafs takes a fairly simple path, modifying all the lowest primitives of Minecraft, the teleportation, network, chunk read/write functions. Portals, structures, entities...
Mods use these functions without knowing it and are therefore automatically compatible.
Lithium/Ferrite/Mapple are compatible. C2ME, VMP, Moonrise are incompatible.

During the development of Leafs, everything is designed so that the slightest change to an internal Mojang function used by modders, like reading/writing chunks, blocks, or teleportation, is perfectly identical in practice. So that modders get no bad surprises. Mods do not adapt to Leafs. Leafs adapts to mods. Leafs must in no case create bugs or problems. Otherwise open a ticket.

# Mapple
Leafs adds no optimization, whether `CPU`, `RAM`, `Garbage Collector` or `load-time allocations`. Any form of optimization is done in an independent mod named Mapple. This mod works with or without Leafs as a mod without config/tradeoffs, pure gain. But designed for the best possible gain for Leafs multithreading.
On the Leafs benchmarks, an isolated idle player costs 33 MiB of RAM with Mapple instead of 52. The up-to-date numbers are in the Mapple docs.

# Debugging & Metrics
Creating the metrics and collecting the values is done in Leafs. It still provides simplified commands to access this data.
`Leafs Debug and Metrics` is an independent, additional mod that provides the client-side display F3, F8, F9 and RAM analysis.

# Server thread
The server does, in order:
1. Runs the `tick.json` for the commands.
2. Then updates the world time.
3. The dimension handles the time, the weather, the border, the tickets and the view of the players, unloads, spawns (phantoms, trader...), then asks the chunk workers in one line for their pass over the chunks without a region. Raids and the dragon fight tick on the region that owns their center.
4. Everything redirected to the global thread. Such as `command blocks`, `respawn`, `chat commands`.
5. The network requests of each player connection. Transport only, the player tick runs on their region.
6. The player list.
7. The clock and the autosave trigger, the regions and the chunk workers then do the saves.
8. Debug, Monitor, sending chunks to players.

### The costs of the server thread.
- Points `2. World time, 7. Autosave and 8. Debug` are purely fixed costs, always identical whatever the server and the number of players.
- Points `5. Connection network, 6. Player list` are costs that vary with the number of players, so tiny that from one server to another the cost is practically identical.
- Points `1. Tick.json, 4. Command blocks` are tied to commands, so avoidable costs.
- Point `3. Dimensions` has about fifteen stages, a good part at zero because moved to the regions, the rest at a fixed cost.

# Philosophy
The mod focuses a lot on Amdahl's and Gustafson's laws, the goal of Leafs is to scale players linearly with the threads/RAM available on the infrastructure. Of course this requires players to be spread across the world to benefit from the gains. And it is also recommended not to use commands, even though the support exists and its cost is the same as vanilla.

For that, the server thread has a relatively fixed, deterministic cost. Which moreover runs in parallel with the region/chunk threads.
During development we try to put nothing on this global thread, to take advantage of these two computing laws.
