# Leafs - Multi thread on Fabric
Leafs is a server-side mod for Fabric/NeoForge. It splits the world into independent regions and runs each region on a thread, with its own TPS.

On a vanilla server, a single thread does everything, moreover the whole game works in such a way that any player impacts all the others, and so the player limit is reached quickly.

Leafs adds multithreading and nothing else. No gameplay feature, no API, no hidden optimization. Your mods, datapacks and command blocks work like in vanilla.

More information on the website. The interactive simulations and the full explanation are on [leafs.hardel.io](https://leafs.hardel.io).

## Features:
- One or several regions are on a thread. A heavy farm only slows down its own players.
- A player alone in their region holds 20 TPS, whatever the world generation around them.
- Chunk generation runs on its own thread pool and scales with your cores, a different architecture from C2ME but in practice the same gains as it.
- Works with your favorite content mods, datapacks like AE2.
- Server-side only. Players have no mod to install on their client.
- Available for Fabric and NeoForge, from Minecraft 26.1.

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Why it scales
Leafs was designed so that the gain is infinite and linear, the more cores and RAM you allocate, the more players you have.

It was designed with Amdahl's and Gustafson's laws in mind. Leafs keeps the common thread part deterministic and minimal < 1ms, and puts the rest in the region/chunk threads. Whatever the number of players or regions or the world generation, the common thread will be constant.

Measured on a Ryzen 5900X, 12 cores/20 GB RAM, Leafs holds **208 players each with a unique region at 20 TPS** on an ungenerated world. The gains are multiplicative in the following cases:
- When several players are in the same region.
- When the world is pre-generated.
- When the world is a skyblock.

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# How regions work
The simulated chunks around a player form a region. Two players getting closer see their regions merge into one. A region that stretches until it splits in two becomes two regions.

This does not only apply to players but to any gameplay element that creates simulated chunks: **chunk loader**, the **/forceload** command, **enderpearls**...

Each region owns its chunks, its players and its own random generator.
Around each region, a ring of chunks absorbs what spills over: a piston, a projectile...

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Mod compatibility
Mods do not adapt to Leafs. Leafs adapts to mods.
Leafs only touches Minecraft's base methods: reading a block, loading a chunk, teleporting an entity.
Mods use these methods without knowing it, so they work. If a mod breaks with Leafs, it is a Leafs bug: open a ticket.

More information on [leafs.hardel.io/docs/mod-compatibility](https://leafs.hardel.io/docs/mod-compatibility).

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Mapple
Leafs optimizes neither the CPU, nor the RAM, nor the garbage collector. These optimizations live in **[Mapple](https://modrinth.com/mod/mapple)**, a separate mod that works with or without Leafs, with no config and no compromise.
Mapple is designed for optimizations that scale, so that they consume less with a high rate of players, chunks or entities.

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Good to know
- **Config file:** `config/leafs.json`, created on first start. The default values suit most servers. By default Leafs uses all your cores for regions and half for chunk generation.
- **server.properties:** on first start Leafs sets `sync-chunk-writes` to `false`.
- **Big servers:** the locator bar becomes unreadable with many players. Disable it with `/gamerule locator_bar false`, Leafs then cuts all its calculations.
- **The `/leafs` command:** for operators.
- **Datapacks:** datapacks are naturally compatible.
- **Mcfunction:** mcfunctions work but they only partially benefit from multithreading. `tick.json` is to be avoided despite Leafs support. [Leafs - Commands and Datapacks](https://leafs.hardel.io/docs/commands-datapacks/)

# Support the project
Join us on Patreon and help make Minecraft even more amazing for everyone!

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

[![Patreon](https://images.ctfassets.net/4rnblstkg79m/5TumqlgINUoJBVWFRRU6wz/e64dfea04fe5b19e60fa27310114853f/WolffOlins_Patreon.jpg)](https://www.patreon.com/hardel)
