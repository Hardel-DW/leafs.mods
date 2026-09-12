# Q&A
A few common questions and answers.

## Do redstone machines work near the region borders?
If a machine goes past the simulated area, it freezes like in vanilla. Nothing new.
Each region also has a ring that does not tick, an area around the region that handles overflows such as pistons, projectiles and the rest.

# Do niche concepts like orbital cannons work?
The code does nothing special for a fast entity, and does not need to.
No entity is sent between threads. A region does not "own" its entities. At the start of each tick it takes a snapshot of the entities of its chunks and ticks those. A TNT that crosses the border simply changes chunk section, like in vanilla, and on the next tick the other region sees it in its snapshot. A hundred or a thousand TNT cost the same as in vanilla.

# How are entities that cross regions handled?
- For teleportations and portals, the origin region does the work, then sends mail to the target region, which places the entity.
- For entities leaving the region, the answer is similar to the orbital cannons, at each tick start it takes an instant snapshot of the game state, if it were to land in another region, that region would then take a snapshot too on the next tick. Simple.
- Same thing, they freeze when leaving the simulated area, like in the original game. The ender pearl is an exception of the original game, it grows the region or can create one like a player.

> Important note, regions are always separated by a non-simulated section beyond the ring, so entities leaving the region freeze in that area, like in the original game beyond the simulation distance. If players are close enough to each other, their regions merge.

# Will chunk loaders/forceload work?
It works, and it is the cleanest point of the model. `Forceload` or `chunk loaders` load areas through simulation. The model is based on simulated areas, so this creates a region or extends its region if one already exists.

# Do datapacks/commands work?
Every command runs on the server thread, whoever launches it. It borrows a region the moment the command touches one of its chunks or one of its entities, keeps it until the end of the command, then gives it back. A command costs exactly its vanilla cost.
A heavy datapack using `tick.json` stays on a single thread, it does not benefit from multithreading. It slows down the server thread and the regions it borrows during its commands, not the others.

# Are there flaws cheaters could exploit, like detecting region merges/splits?
By nature yes, if you move from an area at 20 TPS to an area at 15 TPS you do not need to build a mod to know that something changed, and therefore that something here is loaded. A player, an `ender pearl`, a `forceload`, a `chunk loader` or something else.
The mob cap is per dimension by default, or per region depending on the config, for example two farms in two nearby regions run with a full mob cap each. However some obscure techniques based on analyzing randomness become harder to use since each region has its own random seed.

# Any recommendations for a big server?
- The `locator bar` with many players becomes unreadable, it is better to disable it. `/gamerule locator_bar false`. Moreover Leafs has an optimization that cleanly disables all the `locator bar` computations when it is disabled.
