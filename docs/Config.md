# Config
Leafs créer un fichier de configuration au démarrage, `config/leafs.json`. Une clef incorrect ou une valeurs non prévu, arrétes le serveur, voici les options.
- `max_threads` : Le nombre de workers de regions et de chunks. Les deux auront le même nombre. Avec `-1` cela prend tout les coeurs.
- `section_size` : Nombre de chunks qui formes une section. Les sections proches formes une régions., Une puissance de deux. 16 par défaut. Plus la sections est grandes plus la régions est grande.
- `region_merge_distance` : La distance en sections sous laquelle deux régions voisines fusionnent.
- `region_buffer_distance` : L'épaisseur en sections de la marge vide qu'une région possède. Cette marge est ce qui garantit que deux régions ne touchent jamais le même bloc.

# Debug
- `debug.watchdog_warn_seconds` : Au delà de ce délai, un tick bloqué est loggé avec la pile de son thread.
- `debug.per_region_logs` : Chaque worker prend le nom de sa région (R#id dimension) pendant son tick, donc chaque ligne de log dit quelle région l'a écrite.
- L'arret forcer suit `max-tick-time` de `server.properties`, comme en vanilla. Au delà de ce délai sur une région, Leafs écrit un crash report avec tous les threads puis tue la JVM. `-1` désactive, et en solo il n'y a pas d'arret forcer.


# Commandes
- `/leafs regions [dimension]` : Affiches les régions par dimension. Avec id, TPS, durée de tick, chunks, entités.
- `/leafs timings [dimension] [id]` : Le coût de chaque étape d'un tick, moyenné sur 5 secondes. Si aucun argument, ça affiche le thread serveur.
- `/leafs metrics` : Affiche les informations de la dernière minute. Les emprunts pour les évènements Fabric, reports et abandons, refus de chunks, paquets, churn de chunks. Un refus "étranger".
