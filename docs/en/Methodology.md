# Methodology
How we work on this project. These rules have proven themselves, we do not bypass them.

## Compatibility.
We must think about mods/datapacks inside our mod, for example. If we have issues with POI, we must not fix only the Raid or the Dragon, we must take into account the mods that would have POI. We must always consider mod content, portals, blocks, entities, POI, custom structures... as well as unique concepts that do not exist in vanilla.

## Fixing a bug
The cycle is always the same, we detect, we reproduce in a single unit test that must be red, we fix, the test turns green, we validate in game. A fix without a reproduction has no value.
When a crash is global or comes from a region, we have the id, the dimension, the tick, the stack. We read the whole stack before touching the code, the root cause is rarely the first line.

## Writing code
The logic lives in our modules, the mixins are only hooks, written for cross-mod compatibility: a targeted injection or a wrap that composes with the mixins of others. No single-use function, no dead code, no commented code, no duplicated source of truth. Concurrency primitives stay in our classes. We avoid unchecked casts through the architecture rather than through the annotation.

Comments: one sentence, two at most when there is a bug trace to keep. A comment says what the code cannot say, a constraint, a why. Never a paraphrase of the code, never a narration of the change. A written comment is not rewritten at every pass.
We think long term: no quick fix that becomes debt, no case by case when a single choke point handles the whole class of the problem. If a clean fix requires rethinking a piece of architecture, we do it.

## Testing
- Unit tests, `gradlew test`, run with the real Minecraft bootstrapped when needed.
- In-game validation follows: connect, disconnect, break and place, chests, furnace, chat, command, death and respawn, portal round trip. Load is tested with the overstress bots in a gradual ramp-up, and spark with `--thread *`, otherwise we only see the server thread.

# Mod compatibility.
When we modify game concepts such as primitive functions (reading/writing blocks, chunks, the teleportation mechanisms or others), a mod that uses them must work exactly as before.
If we know of a discrepancy, or a difference/bug that could occur, we simply change the architecture, the rules, and rethink Leafs. Mods/modders must see no difference in their development experience. This is the most important point and it comes before everything else. Mod compatibility takes priority over everything.
I would rather sacrifice an optimization but let mods work perfectly. Telling them that what they do will work, but differently, is not an option either.

# The absolute rule
Leafs does what vanilla does, but multithreaded. When a rule of ours duplicates a vanilla rule, we remove ours. Mods do not see Leafs, they call the same functions as usual, and these functions do the same thing as before. It is better to remove logic to get closer to vanilla than to add some.
The development experience of modders comes before optimizations.
When we talk about mods we mean the big ones, Meka, Applied, Create, Ars Nouveau, which bring unique mechanics unknown to Minecraft, magic, pollution, machines, energy, Dyson spheres...
