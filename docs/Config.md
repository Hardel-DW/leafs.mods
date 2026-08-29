# Config
Leafs crée un fichier de configuration au démarrage, `config/leafs.json`.
- `max_threads` : Le nombre de workers de régions et de chunks. Les deux auront le même nombre. Avec `-1` cela prend tous les cœurs.
- `section_size` : Nombre de chunks qui forment une section. Les sections proches forment une région. Une puissance de deux, `2` par défaut. Plus la section est grande plus la région est grande.
- `region_merge_distance` : La distance en sections sous laquelle deux régions voisines fusionnent.
- `region_buffer_distance` : L'épaisseur en sections de la couronne qu'une région possède.
- `player_chunk_loads_per_tick` : Chaque joueur peut générer (par défaut `5`) chunks par tick.
- `telemetry` : `true` par défaut. À chaque crash de région, Leafs écrit une ligne JSON dans le log, `Leafs telemetry`, avec la dimension, la région, l'erreur, le détail vanilla de l'entité ou du block entity, et le mod dont le code est sur la pile. Rien n'est envoyé sur le réseau.

# Debug
- `debug.watchdog_warn_seconds` : Au-delà de ce délai, un tick bloqué est loggé avec la pile de son thread.
- `debug.slow_task_warn_millis` : Par défaut 50, une tâche sérielle > 50ms est loggée avec sa classe.
- `debug.per_region_logs` : Chaque worker prend le nom de sa région (R#id dimension) pendant son tick, donc chaque ligne de log dit quelle région l'a écrite.
- L'arrêt forcé suit `max-tick-time` de `server.properties`, comme en vanilla. Au-delà de ce délai sur une région, Leafs écrit un crash report avec tous les threads puis tue la JVM. `-1` désactive, et en solo il n'y a pas d'arrêt forcé.


# Commandes
- `/leafs regions [dimension]` : Affiche les régions par dimension. Avec id, TPS, durée de tick, chunks, entités.
- `/leafs timings [global ou <dimension>] [id]` : Le coût de chaque étape d'un tick, moyenné sur 5 secondes. Si aucun argument, ça affiche le thread serveur.
- `/leafs metrics` : Affiche les informations de la dernière minute. Les emprunts pour les évènements Fabric, les travaux passés à une autre région et les abandons, les paquets et les chunks.
- `/leafs config [clé [valeur]]` : sans argument affiche toutes les clés, avec une clé affiche sa valeur, avec une valeur réécrit le fichier de config, qui prend effet au prochain démarrage.
- `/leafs crash <dimension> <region>` : permet de faire crasher une région.
