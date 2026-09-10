# The Tradeoffs
Every deliberate difference is listed here. With its explanation. A refactor or a code improvement can of course remove some, if they are here it is because we had no choice.

# Beneficial tradeoffs.
These tradeoffs are a bit like features. They are actually even good for the game, less cheating, or more gameplay possibilities indirectly. Honestly we avoid removing them.

1. Each region has its own randomness, no visible effect in game.
2. Each region runs at its own TPS. A furnace can be slower from one region to another for example.
3. Every command runs on the server thread, which borrows the regions it touches. [Overview.md](Overview.md)

# Real tradeoffs
4. Writing a block where another region is currently ticking lands on the next tick. The block is placed, but reading it back right away returns the old one. Everywhere else, including in a dimension where nobody is, the write is done when the call returns, like in vanilla. A region only ticks where a player is simulated, so this case requires writing at another player's place while they are there.
5. Teleportations and portals land at the latest on the next tick of the target region.
6. `END_SERVER_TICK` Mods that do their work "once per tick" through the Fabric API still run 20 times per second, but the world around has not necessarily advanced by one tick between two calls. A region at 10 TPS has ticked once out of two. A mod that assumes everyone has ticked exactly once since its last call can be wrong.
7. A `command block` / `command block minecart` triggered by redstone runs one tick later than in vanilla. The redstone runs on the region and the command on the server thread, and going from one to the other waits for the next tick. A command typed in chat or run by a datapack has no such delay.
