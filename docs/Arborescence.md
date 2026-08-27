# Arborescence
Un dossier est une responsabilité. Une brique indépendante. Le code vit dans `src/main/java/fr/hardel/leafs/`. Le dossier `excess` dans hardel vit a cotés du mods. Il donnes des class utilitaires non liés a Minecraft.

- `chunk/` porte le système de chunks, la table concurrente des holders, l'ordonnancement et le pool de génération dans `core/`, le chargeur par joueur dans `loader/`, le propagateur de tickets dans `propagator/`, le contrat de lecture des chunks, la boîte aux lettres par chunk que la région propriétaire vide à chaque tick, les tickets, le tracking des entités et le verrou des villages.
- `debug/` porte les commandes `/leafs regions`, `timings`, `metrics` et `recommendation`, l'affichage côté client et l'analyse de la RAM étant dans le mod à part `Leafs Debug and Metrics`.
- `entity/` porte la photo des entités qu'une région tick, prise dans les sections de ses chunks à chaque tick, la persistance routée vers les régions propriétaires et les téléportations.
- `global/` porte la phase globale, la fenêtre barrière, le moniteur d'état partagé, les command blocks, les gamerules du mod, les écritures différées des fichiers joueurs et la pause des évènements de tick de la Fabric API.
- `metrics/` mesure le serveur avec les durées par étape de tick du catalogue `TickStages` publié dans le registre `leafs:tick_stage`, les compteurs à fenêtre d'une minute, les stats de la fenêtre barrière et les compteurs de reports et de refus du contrat des chunks.
- `mixin/` regroupe les points d'accroche sans logique, un sous dossier par module servi, plus `compat/` pour ceux qui visent la Fabric API.
- `network/` gère les files de paquets par joueur, le routage, le tick réseau par région, la déconnexion et la préparation de la connexion.
- `ownership/` dit qui possède quoi avec le contexte de thread courant, le refus typé `OwnershipViolationException`, le garde `TickGuard` qui saute une unité refusée et le crash report par région.
- `region/` découpe le monde en sections et en régions avec la fusion, la scission et le `Regionizer`, sans aucune dépendance Minecraft.
- `scheduler/` expose le scheduler global et le moteur du travail différé `DeferredWork` avec ses deux destinations, la fenêtre barrière et la région propriétaire d'une position.
- `ticking/` orchestre les régions avec le pool de workers, l'unité de tick sérielle par dimension, la barrière, le budget sériel, le watchdog et les timings.
- `world/` porte le corps du tick de région, ce qu'un chunk tick lui-même, ticks programmés, block events, block entities, et le peu qu'une région garde, horloge, aléatoire, mises à jour de voisinage, photos de ses chunks et de ses entités, autosave par époque.

`LeafsConfig` et les registres du mod sont à la racine du paquet. Le parsing de la config est strict, une clé inconnue est refusée.
Deux règles de dépendance existent. `region/` ne dépend de rien d'autre, et rien ne dépend de `mixin/` et de `debug/`.
Les tests vivent dans `src/test/java` en miroir des modules. Les ressources vivent dans `src/main/resources` avec le manifest du mod et ses options Lithium, la liste des mixins et l'access widener.
