# Mixins

## Philosophie
Chaque mixin s'écrit dans l'optique de la meilleure compatibilité inter mods. Une injection ciblée, un `@Inject` ou un wrap, se compose avec les mixins des autres mods sur la même méthode. Un `@Overwrite` détruit le corps que les autres visent.

Un mixin est un point d'accroche, jamais un lieu de logique. Le corps d'un mixin tient en un appel vers le module qui possède la logique, et sa javadoc dit en une phrase pourquoi ce point d'accroche existe. Les primitives de concurrence, verrous, atomiques, pools, vivent dans nos classes, jamais dans un corps de mixin. Chaque mixin a une raison unique d'exister, liée au système de régions. Cette discipline est ce qui rend le mod lisible et compatible : la surface de contact avec vanilla est petite, nommée et justifiée.
Tout est côté serveur. Le mod n'embarque aucun code client.

## Par classe vanilla
Chaque entrée groupe tous les mixins qui ciblent la même classe vanilla. Quand une classe a plusieurs mixins de packages différents, le package est indiqué en préfixe.

### `MinecraftServer`
- Le mixin `ticking/` porte le `TickingManager` et fait passer chaque tick de niveau par son unité de région. Le `MainThreadExecutor` des chunks ne traite ses tâches que quand aucune région ne tique, Leafs vide les files des joueurs quand le serveur est en pause, et le pool de workers s'arrête proprement à l'extinction.
- Le mixin `global/` porte la `BarrierWindow`. À chaque tick global, il vide la file de tâches globales puis ouvre la barrier window. Les fonctions de datapacks y sont envoyées quand la gamerule le permet.
- Le mixin `entity/` porte le registre des schedulers d'entités, le `EntitySchedulerRegistry`.
- Le mixin `network/` fait sauter à la boucle d'envoi de chunks les joueurs qu'une région tique, leur région envoie pour eux. Pour les joueurs du filet global, l'envoi prend le verrou exclusif du niveau, parce que les chunks sérialisés appartiennent à des régions qui écrivent dedans.

### `DedicatedServer`
Les commandes tapées dans la console passent par la barrier window, parce qu'une commande op peut toucher n'importe quel état du monde.

### `ServerLevel`
- Le mixin `ticking/` porte le `LevelRegions`, qui contient le `Regionizer` et le verrou de la dimension. Le champ s'initialise avant le premier chunk holder.
- Le mixin `chunk/` fait passer la sauvegarde du niveau sous le verrou exclusif de `LevelOwnership` et redirige les écritures de points d'intérêt vers la phase sérielle du niveau.
- Le mixin `entity/` remplace `dragonParts` par une map concurrente et `players` par une `CopyOnWriteArrayList`. Il crée les listes d'entités par région et le routeur de téléportation. Les ajouts et retraits de joueurs prennent le verrou exclusif.
- Le mixin `global/` déplace l'exécution de la `TimerQueue` dans la barrier window, parce que les fonctions programmées peuvent toucher n'importe quel état du monde.
- Le mixin `world/` redirige les ticks programmés, les block events, l'horloge de région, le générateur aléatoire, les mises à jour de voisinage et le cache de path types vers la région propriétaire. Les recherches de structures depuis une région renvoient un résultat vide au lieu de crasher quand un chunk n'est pas chargé.

### `Level`
Le compteur de sub-tick, le générateur aléatoire et le neighbor updater deviennent dépendants de la région qui tique. L'appel à `getBlockEntity` répond correctement depuis un worker de région au lieu de renvoyer null. L'enregistrement d'un block entity ticker va dans la région propriétaire du chunk si elle existe, pour que la région tique ses propres block entities.

### `ChunkMap`
- Le mixin `chunk/` remplace `chunksToEagerlySave` par un set concurrent, signale au `Regionizer` quand un chunk holder est créé ou détruit, et découpe le tracking en deux. La passe par entité et le calcul des vues des joueurs vont dans le corps de région, la phase sérielle ne calcule les vues que des joueurs qu'aucune région ne tique. Le mixin route aussi `getChunkToSend` pour qu'une région ne sérialise que les chunks qu'elle possède, et il détourne l'exécuteur du démontage de `scheduleUnload` vers la région qui possédait le chunk à la décision, avec la file sérielle vanilla en secours. `pendingUnloads` et `nextChunkSaveTime` deviennent atomiques parce que la revendication du démontage traverse les threads.
- Le mixin `entity/` remplace `entityMap` par une map concurrente.

### `ServerChunkCache`
- Le mixin `chunk/` intercepte `getChunkNow`, `getChunk` et `hasChunk` depuis un worker de région. Il répond par les chunks déjà chargés sans jamais déclencher de chargement synchrone.
- Le mixin `world/` fait tourner le `MainThreadExecutor` uniquement sous le verrou exclusif du niveau, sépare la part du tick des chunks qui revient au corps de région, redirige les chunks modifiés vers la liste de broadcast de la région propriétaire, et reporte les déplacements de joueurs à la phase sérielle.

### `ServerChunkCache.MainThreadExecutor`
Le `MainThreadExecutor` ne tourne que sous le verrou exclusif du niveau. Un worker de région qui tenterait de le déclencher lève une exception, parce que le traitement des chunks ne doit jamais tourner en même temps qu'un tick de région.

### `DistanceManager`
Le mixin lit la distance de spawn directement dans le compteur sans vider la file du tracker, que seule la phase sérielle a le droit de toucher.

### `SectionStorage`
Le mixin remplace `storage` par une map concurrente et porte le `PoiVillageLock`. Le flush de sauvegarde passe sous ce verrou.

### `PoiManager`
Le mixin remplace `loadedChunks` par un set concurrent. Les opérations qui touchent le graphe de distance des villages passent sous le `PoiVillageLock` porté par `SectionStorage`, parce que ce graphe ne peut pas devenir concurrent par une simple façade.

### `EntitySectionStorage`, `EntityLookup`, `PersistentEntitySectionManager`, `ChunkMap.TrackedEntity`
Quatre façades concurrentes sur les index d'entités. `EntitySectionStorage` remplace `sections` et `sectionIds`. `EntityLookup` remplace `byId` et `byUuid`. `PersistentEntitySectionManager` remplace `knownUuids`. `TrackedEntity` remplace `seenBy`, le set des joueurs qui voient une entité trackée.

### `ServerLevel.EntityCallbacks`
Les callbacks de section routent les entités vers les listes de tick de la région propriétaire au lieu des listes globales du niveau, parce que les listes globales seraient écrites par plusieurs régions en même temps.

### `PersistentEntitySectionManager.Callback`
Le mixin capture la section avant un `onMove` et rattache l'entité aux listes de sa nouvelle région quand elle change de chunk. Le rattachement se fait avant le `updateStatus` pour qu'un changement de visibilité de ticking à hidden trouve l'entrée dans la bonne liste.

### `Entity`
Le mixin retire le scheduler d'une entité quand elle quitte le monde. Il intercepte `teleport` depuis un worker de région pour dévier le mouvement vers le routeur de téléportation au lieu de l'exécuter sur place. Il reporte la recherche de portail dans la barrier window, parce que la recherche peut créer des blocs dans une autre dimension, comme la plateforme d'obsidienne de l'End.

### `ServerPlayer`
Le mixin remplace le set d'ender pearls par un set concurrent, parce qu'une perle lancée depuis une autre région s'inscrit depuis un autre thread. Il intercepte `teleport` depuis un worker de région pour dévier le mouvement. Le ticket de chunk de la perle est transmis au thread serveur.

### `Scoreboard` et `ServerScoreboard`
Toutes les mutations et les lectures qui parcourent le scoreboard passent sous le `SharedStateMonitor`. `ServerScoreboard` y ajoute le dirty flag, les tracked objectives, la construction des paquets et la sauvegarde.

### `SavedDataStorage`
L'accès au cache et la collecte des données modifiées passent sous le `SharedStateMonitor`. L'encodage pour la sauvegarde prend le moniteur de la donnée elle-même, pour que les régions n'écrivent pas en même temps dans la même instance.

### `MapIndex` et `MapItemSavedData`
`MapIndex` sérialise `getNextMapId` pour que deux régions ne produisent jamais le même identifiant de carte. `MapItemSavedData` donne à chaque carte son propre moniteur. Les joueurs de régions différentes qui portent la même carte sérialisent leurs ticks et leurs écritures.

### `RandomSequences`
Les séquences aléatoires passent sous le moniteur et chaque source renvoyée est enveloppée dans un `LockedRandomSource`. Les rolls de loot de n'importe quelle région partagent les mêmes sources, et le moniteur ordonne les tirages avec la sauvegarde.

### `ServerWaypointManager`
Toutes les mutations de la table des waypoints passent sous le moniteur. L'appel à `transmitters()` renvoie un snapshot immutable.

### `CommandBlock` et `MinecartCommandBlock`
L'exécution d'un command block, fixe ou sur un minecart, est reportée dans la barrier window, parce qu'une commande peut toucher n'importe quel état du monde.

### `PacketProcessor`
Les paquets de jeu sont routés vers la file du joueur au lieu de la file globale du processeur. Le thread qui vide une file de joueur est reconnu comme un thread de paquets valide.

### `PacketUtils`
La vérification de thread vanilla est remplacée. Le thread autorisé à traiter un paquet est celui qui vide la file du joueur, pas nécessairement le thread serveur.

### `ServerGamePacketListenerImpl`
Le mixin porte la `PlayerPacketQueue` du joueur. La chaîne du chat signé, le filtrage des livres et pancartes, et le chat s'exécutent dans la file du joueur. Les commandes restent sur la phase globale parce qu'elles peuvent charger des chunks arbitraires. Le respawn passe par la barrier window parce qu'il replace le joueur dans une dimension potentiellement différente. L'ack de batch de chunks se traite dans la file du joueur, sur le même thread que celui qui envoie ses chunks.

### `PlayerChunkSender`
Le mixin remplace le set des chunks en attente d'envoi par un set concurrent, parce que la phase sérielle y marque les chunks fraîchement prêts pendant que la région du joueur collecte et envoie. Les quotas et les compteurs de batch restent sur le thread propriétaire du joueur.

### `Connection`
Le tick de connexion est coupé en deux. Le flush et la détection de déconnexion restent sur le thread global. La physique du joueur, les menus et le keepalive s'exécutent sur la région propriétaire.

### `ServerCommonPacketListenerImpl`
Les envois de paquets sont groupés par tick de région pour qu'ils partent ensemble. La déconnexion depuis un thread autre que le thread serveur devient non bloquante pour éviter de bloquer un worker.

### `PlayerList`
Le mixin remplace `players` et `playersByUUID` par des structures concurrentes. Le placement d'un nouveau joueur s'exécute sur le thread qui possède le niveau de destination. La sauvegarde prend le verrou exclusif du niveau. Le retrait met toutes les régions en pause parce que le teardown touche des entités d'autres régions.

### `BlockableEventLoop`
Un appel à `MinecraftServer.execute` depuis un worker de région est redirigé vers la phase globale, pour que la tâche s'exécute au bon moment au lieu de tourner sur le thread d'une région.

### `ServerTickRateManager`
Un changement de tick rate se propage à toutes les boucles de régions.

### `ServerWatchdog`
Le mixin neutralise le watchdog vanilla, qui mesure un unique game thread qui n'existe plus. Leafs le remplace par un watchdog par région.

### `SerializableChunkData`
Le mixin fait packer les ticks programmés par rapport à l'horloge de la région propriétaire du chunk, pas par rapport au game time global, parce que les régions n'ont pas toutes vécu le même nombre de ticks.

### Compatibilité Fabric API
`FabricApiLookupCacheShim` cible `ServerLevel` et synchronise la map de cache de `fabric-api-lookup` sur le niveau. `FabricLoadedChunksShim` cible `Level` et remplace le `HashSet` de chunks chargés de `fabric-lifecycle-events` par un set concurrent. Les deux s'appliquent en priorité 1100 pour que les méthodes de la Fabric API existent au moment de l'injection.
