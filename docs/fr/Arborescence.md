# Arborescence
Un dossier est une responsabilité. Une brique indépendante. Le code vit dans `src/main/java/fr/hardel/leafs/`. Le dossier `excess` dans hardel vit à côté du mod. Il donne des collections thread-safe non liées à Minecraft.
Le code commun à Fabric et NeoForge vit dans `common`.

- `debug/` - La commande `/leafs`
- `entity/` - La snapshot des entités qu'une région tick, les téléportations et la persistance des entités.
- `global/` - Le moteur des commandes, le scheduler global, le moniteur d'état, la locator bar, l'aléatoire.
- `metrics/` - Télémétrie, mesure du serveur, et la liste des étapes d'un tick.
- `mixin/` - Patch de Minecraft, un sous-dossier par brique. `compat/` corrige les mods qui partagent une collection entre threads.
- `network/` - Gère les files de paquets par joueur, le routage, le tick réseau par région, la déconnexion, la préparation de la connexion et la liste des chunks qu'un joueur attend.
- `region/` - Découpe le monde en sections et en régions avec la fusion, la scission, sans aucune dépendance Minecraft.
- `ticking/` - Le scheduler des régions, l'horloge de région, l'emprunt, le crash report par région, le watchdog, les époques de tick.
- `world/` - Tick de région, ce qu'un chunk tick lui-même, ticks programmés, block events, block entities, et le peu qu'une région garde, horloge, aléatoire, mises à jour de voisinage, photos de ses chunks et de ses entités, autosave par époque.
- `chunk/` - Le moteur de chunks, et le branchement de Firefly sur le pool.

# Chunks Folder
- `pool/` - Le pool de thread de chunks. 
- `ticket/` - Les trois graphes de tickets, `joueurs`, `simulation`, `chargement`. 
- `level/` - Les niveaux de chunk propagés par graphe. 
- `holder/` - La table des holders, l'attente d'un chunk absent, les étapes de génération. 
- `owner/` - Qui écrit à une position du monde, la boîte aux lettres d'une région, Les chunks sans région. 
- `view/` - Liés a la position des joueurs. 
- `disk/` - Le chunk en octets vers le thread disque.
