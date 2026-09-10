# Benchmark
A blank server starts on a blank world with an identical `seed`, Leafs uses the Overstress mod which simulates players as authentic as real players, with scenarios. The bots therefore do the same actions in the same order, without randomness in the game.
This benchmark runs in a private `repository` with 12 cores / 24 threads on a Ryzen 5900X, with 12 chunk threads and 12 region threads.
Several scenarios exist, only a few are mentioned here, `worldgen` and `ramp`.

## Leafs aims for
- A player alone in their region holds 20 TPS, whatever the world generation around them.
- The loading/unloading of the `worldgen`, as well as connection/disconnection, must not impact the TPS of a region.
- The server thread must have a fixed cost, under 1 ms, without depending on the number of chunks, entities or players.
- The serial part per dimension stays under 0.5 ms.
- Chunk generation must follow the number of chunk threads linearly.
- The number of players and regions must scale linearly with the threads and the RAM.
- Memory must be stable/constant, without memory leaks.
- No stuck thread must be detected.
- The CPU must not exceed 70 % on the established benchmark.
- Reading/writing of chunks by third-party mods, commands, datapacks and redstone machines must work.
- An exception in a region's tick stops the server with the crash report of that region.

## The worldgen scenario
5 bots fly at 36 blocks/s with a `view distance` of 10 for 3 minutes. This scenario measures generation and its effect on the regions.

| measure | target | reference run |
|---|---|---|
| Minimum TPS | 19.9 or 20 | 20 |
| Server thread mspt | under 1 | 0.5 |
| Full chunks/s | linear with the chunk workers | 383 |
| Bot speed | 36.0 | 36.0 |
| View generated over the last 60 seconds | 100 % | 100 % |
| Samples with an incomplete view | 0 % | 0 % |
| Cores consumed | under 14 | 12.8 |
| Memory heap at end of run | Stable < 1 GB | 0.68 GB |
| Holders waiting for teardown | 0 | 0 |
| Worst tick of a region stage | under 50 ms | tasks 37 ms |

## The ramp scenario
One bot every 3 seconds up to 300, over 20,000 blocks, for fifteen minutes, with `locator_bar` disabled. The bots walk through blank terrain.
This computes the number of players the machine holds at 20 TPS. This is the hardware limit.
The number of full chunks per second is lower in this scenario because the region threads are more solicited, so fewer resources are allocated to the world generation threads. This works as expected. The game experience at 20 TPS matters more than the world generation speed.

| measure | reference run |
|---|---|
| Capacity | 208 players at 622 s |
| Full chunks/s | 195 |
| Cores consumed | 13.4 |
| Server thread mspt | 1.3 ms |

## Comparing to C2ME
Leafs has relatively identical values on 12 threads, `329` for C2ME, and `383` measured for Leafs, slightly higher by a few chunks per second thanks to the region system which lets the chunk threads focus exclusively on chunk generation, and to Mapple.
C2ME and Leafs are both based on ScalableLux and FastNoise, and the approach stays relatively similar.
Like C2ME the gains scale linearly with the number of threads.
Lithium/VMP/Chunky were not used for the benchmarks.
