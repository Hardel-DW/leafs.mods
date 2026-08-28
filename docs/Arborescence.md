# Arborescence
Un dossier est une responsabilité. Une brique indépendante. Le code vit dans `src/main/java/fr/hardel/leafs/`. Le dossier `excess` dans hardel vit à côté du mod. Il donne des classes utilitaires non liées à Minecraft.

- `chunk/` - Porte le système de chunks.
- `debug/` - Porte la commande `/leafs`
- `entity/` - Porte la snapshot des entités qu'une région tick.
- `global/` - Porte le moteur des commandes, le moniteur d'état partagé, les écritures différées des fichiers joueurs et l'emprunt pendant les évènements de tick de la Fabric API.
- `metrics/` - Mesure le serveur avec les durées par étape de tick du catalogue `TickStages` publié dans le registre `leafs:tick_stage`.
- `mixin/` - Patch de Minecraft.
- `network/` - Gère les files de paquets par joueur, le routage, le tick réseau par région, la déconnexion et la préparation de la connexion.
- `ownership/` - Dit qui possède quoi avec le contexte de thread courant, le refus typé `OwnershipViolationException`.
- `region/` - Découpe le monde en sections et en régions avec la fusion, la scission, sans aucune dépendance Minecraft.
- `scheduler/` - Expose le scheduler global et le moteur du travail différé `DeferredWork`, dont la seule destination est la région propriétaire d'une position.
- `ticking/` - L'unité de tick sérielle par dimension, l'emprunt de régions par le thread serveur, le budget sériel, le watchdog et les timings.
- `world/` - Porte le corps du tick de région, ce qu'un chunk tick lui-même, ticks programmés, block events, block entities, et le peu qu'une région garde, horloge, aléatoire, mises à jour de voisinage, photos de ses chunks et de ses entités, autosave par époque.
