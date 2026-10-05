# Config
Leafs creates a configuration file at startup, `config/leafs.json`. On first startup, Leafs sets `sync-chunk-writes` to `false` in `server.properties`. The admin can set it back to true, Leafs never touches it again.

- `region_threads` : The number of region workers. With `-1` it takes every core. `0` is refused.
- `chunk_threads` : The number of chunk workers. With `-1` it takes half of the cores. `0` is refused.
- `section_size` : Number of chunks that form a section. Nearby sections form a region. A power of two, `2` by default. The bigger the section, the bigger the region. (2 to 256)
- `region_merge_distance` : The distance in sections under which two neighboring regions merge. `1` by default, from 1 to 8
- `region_buffer_distance` : The thickness in sections of the ring a region owns. `1` by default, from 1 to 8

# Gameplay
- `gameplay.mob_cap_scope` : `level` by default, the mob cap is computed over the whole dimension like in vanilla. With `region`, each region has its own mob cap.
- `gameplay.mob_cap` : Vanilla values by default, the number of entities that can spawn, from 0 to 100,000 for each category. (`monster`, `creature`, `ambient`...)

# Debug
- `debug.watchdog_warn_seconds` : Disabled by default (`-1`). Beyond this delay, a stuck tick is logged with the stack of its thread.
- `debug.slow_task_warn_millis` : Disabled by default (`-1`). Beyond this delay in ms, a thread that had to wait for a chunk not yet generated is logged. An abnormally long region task is logged too with its class.
- `debug.per_region_logs` : `false` by default. Each worker takes the name of its region (R#id dimension) during its tick, so each log line says which region wrote it.
- The forced shutdown follows `max-tick-time` from `server.properties`, like in vanilla. Beyond this delay on a region, Leafs writes a crash report with every thread then kills the JVM. `-1` disables it, and in singleplayer there is no forced shutdown.

# Commands
The `/leafs` command is reserved for operators.

- `/leafs regions [dimension]` : Shows the regions per dimension. With id, TPS, tick duration, chunks, entities.
- `/leafs metrics` : Shows the information of the last minute. The borrows for the Fabric events, the work handed to another region and the abandons, the packets and the chunks.
- `/leafs config [key] [value]` : If no value is given, shows the value of the key. Otherwise it updates the config, which takes effect at the next startup.
- `/leafs crash <dimension> <region>` : Crashes a region.
- `/leafs ram` : Shows the RAM used by the server, the "heap" as well as technical information about the GC/JVM, and per dimension the number of holders and of sections of the graphs
- `/leafs chunk [position]` : Shows the state of a chunk. Without a position, it looks at the chunk you are in.

The `/leafs timings` command shows the cost of each stage of a tick, averaged over 5 seconds. With no argument, it shows the server thread.
`/leafs timings`: To look at the server thread.
`/leafs timings <dimension>`: To look at the dimension.
`/leafs timings <dimension> <id>`: To look at a region.
