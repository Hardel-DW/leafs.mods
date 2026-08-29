# Arborescence
Un dossier est une responsabilité. Une brique indépendante. Le code vit dans `src/main/java/fr/hardel/leafs/`. Le dossier `excess` dans hardel vit à côté du mod. Il donne des classes utilitaires non liées à Minecraft.

- `chunk/` - Porte le système de chunks, la boîte aux lettres par chunk, l'attente d'un chunk absent et les chunks que les workers de chunks possèdent.
- `debug/` - Porte la commande `/leafs`
- `entity/` - Porte la snapshot des entités qu'une région tick.
- `global/` - Porte le moteur des commandes, le moniteur d'état partagé, les écritures différées des fichiers joueurs et l'emprunt pendant les évènements de tick de la Fabric API.
- `metrics/` - Télémétrie, Mesure du serveur. Et le registre `leafs:tick_stage`.
- `mixin/` - Patch de Minecraft.
- `network/` - Gère les files de paquets par joueur, le routage, le tick réseau par région, la déconnexion et la préparation de la connexion.
- `region/` - Découpe le monde en sections et en régions avec la fusion, la scission, sans aucune dépendance Minecraft.
- `scheduler/` - Expose le scheduler global et le moteur du travail différé `DeferredWork`, dont la seule destination est la région propriétaire d'une position.
- `ticking/` - L'unité de tick sérielle par dimension, le contexte du thread courant, l'emprunt de régions et de chunks, le crash report par région, le watchdog et les timings.
- `world/` - Porte le corps du tick de région, ce qu'un chunk tick lui-même, ticks programmés, block events, block entities, et le peu qu'une région garde, horloge, aléatoire, mises à jour de voisinage, photos de ses chunks et de ses entités, autosave par époque.
