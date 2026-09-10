# Arborescence
Un dossier est une responsabilité. Une brique indépendante. Le code vit dans `src/main/java/fr/hardel/leafs/`. Le dossier `excess` dans hardel vit à côté du mod. Il donne des classes utilitaires non liées à Minecraft.

- `debug/` - La commande `/leafs`
- `entity/` - La snapshot des entités qu'une région tick, les téléportations et la persistance des entités.
- `global/` - Le moteur des commandes, le moniteur d'état, l'emprunt pendant les évènements de tick de la Fabric API, la locator bar, l'aléatoire.
- `metrics/` - Télémétrie, Mesure du serveur. Et le registre `leafs:tick_stage`.
- `mixin/` - Patch de Minecraft. de la Fabric API (`compat/`) et de ScalableLux (`light/`)
- `network/` - Gère les files de paquets par joueur, le routage, le tick réseau par région, la déconnexion et la préparation de la connexion.
- `region/` - Découpe le monde en sections et en régions avec la fusion, la scission, sans aucune dépendance Minecraft.
- `scheduler/` - Expose le scheduler global dont la destination est le propriétaire d'un chunk, région, pool ou emprunteur
- `ticking/` - Le scheduler des régions, l'horloge de région, l'emprunt, le crash report par région, le watchdog.
- `world/` - Tick de région, ce qu'un chunk tick lui-même, ticks programmés, block events, block entities, et le peu qu'une région garde, horloge, aléatoire, mises à jour de voisinage, photos de ses chunks et de ses entités, autosave par époque.
- `chunk/` - Le moteur de chunks.

# Chunks Folder
- `pool/` - Le pool de thread de chunks. 
- `ticket/` - Les trois graphes de tickets, `joueurs`, `simulation`, `chargement`. 
- `level/` - Les niveaux de chunk propagés par graphe. 
- `holder/` - La table des holders, l'attente d'un chunk absent, les étapes de génération. 
- `owner/` - Qui écrit à une position du monde, la boîte aux lettres d'une région, Les chunks sans région. 
- `view/` - Liés a la position des joueurs. 
- `disk/` - Le chunk en octets vers le thread disque."
