# Config
Leafs crée un fichier de configuration au démarrage, `config/leafs.json`.
- `region_threads` : Le nombre de workers de régions. Avec `-1` cela prend tous les coeurs.
- `chunk_threads` : Le nombre de workers de chunk. Avec `-1` cela prend la moités des coeurs.
- `section_size` : Nombre de chunks qui forment une section. Les sections proches forment une région. Une puissance de deux, `2` par défaut. Plus la section est grande plus la région est grande. (2 à 256)
- `region_merge_distance` : La distance en sections sous laquelle deux régions voisines fusionnent. `1` par défaut, de 1 à 8
- `region_buffer_distance` : L'épaisseur en sections de la couronne qu'une région possède. `1` par défaut, de 1 à 8

# Gameplay
- `gameplay.mob_cap_scope` : `level` par défaut, le mob cap se calcule sur toute la dimension comme en vanilla. Avec `region`, chaque région a son propre mob cap.
- `gameplay.mob_cap` : Valeurs vanilla par défaut, le nombre d'entités qui peuvent apparaitre. (`monster`, `creature`, `ambient`...)

# Debug
- `debug.watchdog_warn_seconds` : Au-delà de ce délai, un tick bloqué est loggé avec la pile de son thread. `15` par défaut
- `debug.slow_task_warn_millis` : Par défaut `50` (ms). Au-delà, un thread qui a dû attendre un chunk pas encore généré est logs, une tâche de la région anormalement longue est aussi logs avec sa classe.
- `debug.per_region_logs` : Chaque worker prend le nom de sa région (R#id dimension) pendant son tick, donc chaque ligne de log dit quelle région l'a écrite.
- L'arrêt forcé suit `max-tick-time` de `server.properties`, comme en vanilla. Au-delà de ce délai sur une région, Leafs écrit un crash report avec tous les threads puis tue la JVM. `-1` désactive, et en solo il n'y a pas d'arrêt forcé.


# Commandes
- `/leafs regions [dimension]` : Affiche les régions par dimension. Avec id, TPS, durée de tick, chunks, entités.
- `/leafs metrics` : Affiche les informations de la dernière minute. Les emprunts pour les évènements Fabric, les travaux passés à une autre région et les abandons, les paquets et les chunks.
- `/leafs config [clé] [valeur]` :  Si aucunes valeur est préciser, affiche la valeur de la clé, Sinon cela met a jour la config, qui prend effet au prochain démarrage.
- `/leafs crash <dimension> <region>` : permet de faire crasher une région.
- `/leafs ram` : Affiche la ram utiliser par le serveur, le "heap" ainsi que des infos techniques sur le GC/JVM.

La commande `/leafs timings` affiche coût de chaque étape d'un tick, moyenné sur 5 secondes. Si aucun argument, ça affiche le thread serveur.
`/leafs timings`: Pour consulter le thread serveur.
`/leafs timings <dimension>`: Pour consulter la dimensions.
`/leafs timings <dimension> <id>`: Pour consulter une régions.