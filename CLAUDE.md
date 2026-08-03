# Project Overview
Fabric 26.2 mod in Java 25, server side. The goal: bring regionised multithreading to Fabric, like Folia, but completely rethought for the modern Minecraft architecture. Everything through Mixins, no fork, no patched jar, just a mod.
This is not a port of Folia patches. We take the concepts (regions, one tick loop per region, no global main thread) and we redesign them for vanilla 26.2 + Fabric.
The mod adds only the multithreading, nothing else. No extra features, no gameplay change, no API bloat.
The documentation TOC is docs\SUMMARY.md. Always read it first, the design docs (Architecture, Modules, Mixins, Roadmap, Compromises) are the source of truth during dev - we never reopen the vanilla/Folia/Fabric code, everything is in docs\sources\ with class references.
If you need other work check "repository\voxel.studio.mods".

# Reference Code
Decompiled and reference sources are in the "repository" folder outside the workspace, just ask if you need permissions:
- "minecraft-26.2" - Minecraft source code. (e.g repository\minecraft-26.2\net\minecraft\SharedConstants.java)
- "folia" - Fork of Paper, patch format. The whole region logic is in folia-server\minecraft-patches\features\0001-Region-Threading-Base.patch
- "paper" - Contains Moonrise readable in paper-server\src\main\java\ca\spottedleaf\moonrise (concurrent chunk system, concurrency utils). Folia is built on top of it.

# Core Design
- A Regionizer groups nearby loaded chunks into independent regions.
- Each region has its own tick loop at 20 TPS, executed on a thread pool.
- No global main thread anymore. Each region is its own "main thread" during its tick.
- A region owns its chunks, entities, block entities and players. Nobody else touches them during the tick.
- Cross-region logic (teleport, portals, commands) goes through schedulers: region scheduler, global scheduler, entity scheduler.
- Regions merge when they get close, split when they drift apart. Merge/split must stay cheap.
- Thread ownership checks everywhere in dev, we must crash early on wrong-thread access, not corrupt silently.

# Compatibility
- Mods and datapacks must keep working, that's the whole point of doing it on Fabric.
- We try to make the big mods compatible automatically (Applied Energistics, Mekanism, Create...). Only if possible, to confirm by reading the Minecraft codebase first. If it's not possible, we limit the scope to vanilla SMP.
- Vanilla behavior preserved as much as possible, determinism per region.
- We will make compromises. Some systems can stay single-threaded or globally scheduled at first if regionising them breaks too much. We document every compromise, with the reason.
- A mod that assumes one main thread must not corrupt the world. Worst case it degrades, it never crashes another region.

# Crash Isolation & Debugging
- A region that crashes only disconnects the players of that region, never the whole server. The other regions keep ticking.
- Crash isolation is a long term goal, not a priority for the initial dev. But we design with it in mind from the start: clear boundaries between region-owned state and shared state, so we can add it later without redoing everything from zero. A mid-tick crash can leave shared state half modified (chunk system, schedulers), even Folia doesn't fully isolate that. When it's not safe, we shut down properly instead of corrupting the save.
- Crash logs are scoped to the region: region id, dimension, chunks, entities, the tick where it died. We must be able to understand a region crash without digging in a global log.
- In-game debugging: an F3-style overlay showing region info (current region, thread, TPS/MSPT per region, chunk count, entity count).
- Maybe per-region or per-thread log files, format to be defined, we'll see when we get there.

# Global Rules
- Avoid Memory, use docs.
- No em-dash use ponctuations. Complete sentence
- No redundancy, we must avoid duplicating truth sources.
- No function/variable with a single line/reference. Except Getter/Setter...
- Avoid over engineering.
- No support of Legacy/Deprecated.
- A class should have a single dominant responsibility.
- Avoid dirty code / temporary code.
- It's better to tell me what you have in mind before doing it.
- Don't just write code that fixes a problem immediately, think long term and consider all possible future scenarios.
- Don't lie, prefer to tell the truth even when it's negative, don't please me just to please me, we must work factually.
- Try to criticize my choices which can sometimes go in the wrong direction.
- Avoid adding too many unnecessary comments that serve no purpose.
- Prioritize the OOP approach. Don't make everything in a static class. Use a correct Pattern. (Static is good but not for everything)
- Avoid unchecked, UNCHECKED_CAST find good architectural solutions that avoid them as much as possible.
- Avoid Inline Import.
- No Commented Code.
- If you have huge rename/move refactor, I can use Intelij, it does instantly.

# Mixins Rules
- Mixins as small as possible. The real logic lives in our own classes, the mixin is just the hook.
- Prefer targeted injections over @Overwrite. @Overwrite is a last resort and must be documented.
- Every mixin has one clear reason to exist, tied to the region system.
- Concurrency primitives (locks, atomics, thread pools) stay in our code, never inside mixin bodies.

# Long Term:
No leaving work for later. We do everything end-to-end, cleanly and completely. We can potentially make several commits, but no leaving things for later. No shortcuts, no hacks. Everything done properly.
With good coding practices, clean up dead code, duplication, etc.
We do it really clean, for the long term. Good code quality without hacks.
Removing code can sometimes require writing a bit of new code to eliminate a lot more. We're not aiming for fast delivery but for code quality over multiple years.
