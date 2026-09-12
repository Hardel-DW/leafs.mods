# Leafs
On a classic server, every cost is shared. Leafs splits the world into independent regions, and each region runs its own tick at 20 TPS. Leafs adds multithreading, and nothing else. No gameplay feature, no API.
The RAM/CPU optimizations we made live in a separate mod, Mapple, which works with or without Leafs. Leafs depends on Mapple, ScalableLux and FastNoise.
The mods C2ME, Moonrise and VMP are incompatible.

## Reading this documentation
1. [Methodology.md](Methodology.md) - Dictates how to think, test and develop.
2. [Overview.md](Overview.md) - Explains the model, the threads, the regions, the clocks, the commands, in simple terms for players.
3. [Tradeoffs.md](Tradeoffs.md) - Lists every difference from vanilla and why it exists.
4. [Layout.md](Layout.md) - Describes the code folders, one folder equals one responsibility.
5. [Config](Config.md) - Shows the config options and the commands
6. [Q&A](Q&A.md) - The common questions from players.
7. [Benchmark](Benchmark.md) - What Leafs aims for and what the bench measures.
