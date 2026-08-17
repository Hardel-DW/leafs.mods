# Mixins

## Philosophie
Chaque mixin s'écrit dans l'optique de la meilleure compatibilité inter mods. Une injection ciblée, un `@Inject` ou un wrap, se compose avec les mixins des autres mods sur la même méthode. Un `@Overwrite` détruit le corps que les autres visent.

Un mixin est un point d'accroche, jamais un lieu de logique. Le corps d'un mixin tient en un appel vers le module qui possède la logique, et sa javadoc dit en une phrase pourquoi ce point d'accroche existe. Les primitives de concurrence, verrous, atomiques, pools, vivent dans nos classes, jamais dans un corps de mixin. Chaque mixin a une raison unique d'exister, liée au système de régions. Cette discipline est ce qui rend le mod lisible et compatible : la surface de contact avec vanilla est petite, nommée et justifiée.
Tout est côté serveur. Le mod n'embarque aucun code client.

## Par classe vanilla
Chaque entrée groupe tous les mixins qui ciblent la même classe vanilla. Quand une classe a plusieurs mixins de packages différents, le package est indiqué en préfixe.

### `MinecraftServer`
- Le mixin `ticking/` porte le `TickingManager` et fait passer chaque tick de niveau par son unité de région. Leafs vide les files des joueurs quand le serveur est en pause, et les pools de workers s'arrêtent proprement à l'extinction.
- Le mixin `global/` porte la `BarrierWindow`. À chaque tick global, il vide la file de tâches globales puis ouvre la barrier window. Les fonctions de datapacks y sont envoyées quand la gamerule le permet.
- Le mixin `entity/` porte le registre des schedulers d'entités, le `EntitySchedulerRegistry`.
- Le mixin `network/` fait sauter à la boucle d'envoi de chunks les joueurs qu'une région tique, leur région envoie pour eux. Pour les joueurs du filet global, l'envoi prend le verrou exclusif du niveau, parce que les chunks sérialisés appartiennent à des régions qui écrivent dedans. Il encadre aussi le tick de connexion avec le lot de pauses de `ticking/PauseBatch`, pour qu'une vague de déconnexions partage une seule pause de régions.
- Le mixin `compat/` encadre les deux émissions d'évènements de tick serveur de la Fabric API dans `tickServer` avec la barrière, quand elles ont des abonnés. Les ancres sont les appels vanilla juste avant et juste après chaque point d'émission, pour que la pause couvre exactement les injections de fabric-lifecycle-events quelle que soit leur priorité.

### `DedicatedServer`
Les commandes tapées dans la console passent par la barrier window, parce qu'une commande op peut toucher n'importe quel état du monde.

### `ServerLevel`
- Le mixin `ticking/` porte le `LevelRegions`, qui contient le `Regionizer` et le verrou de la dimension. Le champ s'initialise avant le premier chunk holder.
- Le mixin `chunk/` fait passer la sauvegarde du niveau sous le verrou exclusif de `LevelOwnership` et redirige les écritures de points d'intérêt vers la phase sérielle du niveau. Il fait aussi tiquer chaque spawner custom sous la portée de lecture dégradée : un spawner qui sonde un terrain jamais généré refuse et saute son passage au lieu de bloquer le thread sériel sur la génération, ce qui gelait toute la dimension.
- Le mixin `entity/` remplace `dragonParts` par une map concurrente et `players` par une `CopyOnWriteArrayList`. Il crée les listes d'entités par région et le routeur de téléportation. Les ajouts et retraits de joueurs prennent le verrou exclusif et se sérialisent en plus entre régions, parce que deux régions partagent le côté lecture du verrou et toucheraient sinon les mêmes maps de joueurs du niveau en même temps.
- Le mixin `global/` déplace l'exécution de la `TimerQueue` dans la barrier window, parce que les fonctions programmées peuvent toucher n'importe quel état du monde.
- Le mixin `world/` redirige les ticks programmés, les block events, l'horloge de région, le générateur aléatoire, les mises à jour de voisinage et le cache de path types vers la région propriétaire. Les recherches de structures depuis une région renvoient un résultat vide au lieu de crasher quand un chunk n'est pas chargé.

### `Level`
Le compteur de sub-tick, le générateur aléatoire et le neighbor updater deviennent dépendants de la région qui tique. L'appel à `getBlockEntity` répond correctement depuis un worker de région au lieu de renvoyer null. L'enregistrement d'un block entity ticker va dans la région propriétaire du chunk si elle existe, pour que la région tique ses propres block entities.

### `ChunkMap`
- Le mixin `chunk/` construit le cœur concurrent au constructeur : la `ConcurrentChunkTable` remplace le double buffer et `promoteChunkMap` se réduit à consommer son drapeau de changement, le dispatcher worldgen devient le `ParallelChunkTaskDispatcher` adossé au pool de chunks, la couche `ChunkScheduling` se câble sur le propagateur, et le `PlayerChunkLoader` naît avec ses tickets refcomptés. Il signale au `Regionizer` quand un chunk holder est créé ou détruit, revendique les décisions de déchargement sous la cellule d'ordonnancement de la position, route le corps de la promotion ticking, `onChunkReadyToSend` et `onFullChunkStatusChange` vers le propriétaire de la position, met les étapes de génération transfrontalières sous l'exclusion spatiale dans `applyStep`, fait partir la lecture disque des chunks sur le pool, et met en file les tâches de génération construites sous verrou pour les démarrer après. Il découpe aussi le tracking en deux, la passe par entité et le calcul des vues dans le corps de région, la phase sérielle pour les joueurs qu'aucune région ne tique, route `getChunkToSend` pour qu'une région ne sérialise que les chunks qu'elle possède, et détourne l'exécuteur du démontage de `scheduleUnload` vers la région qui possédait le chunk à la décision, avec la file sérielle vanilla en secours. `chunksToEagerlySave`, `toDrop`, `chunkTypeCache`, `pendingUnloads` et `nextChunkSaveTime` deviennent concurrents parce que les drains et les démontages traversent les threads.
- Le mixin `entity/` remplace `entityMap` par une map concurrente.

### `ServerChunkCache`
- Le mixin `chunk/` répond à `getChunkNow` et `hasChunk` depuis n'importe quel thread par les chunks publiés de la table, intercepte `getChunk` depuis un worker de région pour refuser au lieu de charger en synchrone, et met les chemins de requête, `getChunkFutureMainThread` et `addTicketAndLoadWithRadius`, sous le verrou d'ordonnancement avant de démarrer les tâches construites.
- Le mixin `world/` sépare la part du tick des chunks qui revient au corps de région, redirige les chunks modifiés et les chunks prêts à envoyer vers la liste de broadcast de la région propriétaire, et reporte les déplacements de joueurs à la phase sérielle.

### `ServerChunkCache.MainThreadExecutor`
La pompe tourne librement, les promotions vivent sous les verrous de zone. Le mixin ajoute le drain de propriétaire universel : un thread serveur qui attend un chunk en tenant l'exclusion du niveau ou la barrière exécute lui-même les tâches de chunks en file sur les régions, parce qu'aucune région ne peut le faire à sa place.

### `DistanceManager`
Le mixin lit la distance de spawn directement dans le compteur sans vider la file du tracker, que seule la phase sérielle a le droit de toucher. Il porte le propagateur de tickets de Leafs, `LevelTicketPropagator`, qui remplace le graphe de propagation vanilla, et le drain vanilla reste en filet pour les rares mises à jour d'avant le câblage, à vide en régime normal. Il débranche la moitié par joueur de vanilla, le ticket de simulation et le `PlayerTicketTracker`, que le chargeur par joueur remplace.

### `ChunkStatusTasks`
Le pas FULL publie le chunk dans le monde vivant, la construction du `LevelChunk` et l'enregistrement des block entities et des conteneurs de ticks. Le mixin le route vers le propriétaire de la position au lieu de la pompe, et son corps prend l'exclusion spatiale du pas FULL pour qu'une FEATURES voisine n'écrive pas dans le proto chunk pendant la copie.

### `ThreadedLevelLightEngine`
Une tâche de lumière déposée dans la file n'atteint le moteur que si quelqu'un appelle `tryScheduleUpdate`, et en vanilla ce quelqu'un est toujours la pompe du chunk source sur le thread serveur. Les pas INITIALIZE_LIGHT et LIGHT s'attendent sous l'exclusion spatiale, donc un worker de chunks tient une zone pendant qu'il attend ce thread, qui attend lui-même les ticks de région chaque fois qu'il met un niveau au repos. Le mixin fait que la voie de lumière se pilote toute seule. Chaque tâche arme son propre drain depuis l'intérieur de la file, et un drain qui laisse du travail derrière lui arme le suivant.

### `TicketStorage`
Le mixin met la table de tickets sous un moniteur, pour qu'une région pose ses tickets elle-même. Les écritures et les lectures passent toutes par ce moniteur, parce qu'un scheduler peut poser un ticket depuis une autre dimension ou un pool async pendant que la phase sérielle parcourt la table, et que le verrou par dimension n'exclut pas ces threads. Le listener de chargement nourrit le propagateur de Leafs en ligne, sous le moniteur, depuis n'importe quel thread, son verrou de zone rend le feed sûr. Le listener de simulation reste routé vers la phase sérielle, parce que le graphe de simulation vanilla reste mono thread.

### `SectionStorage`
Le mixin remplace `storage` par une map concurrente et porte le `PoiVillageLock`. Le flush de sauvegarde passe sous ce verrou.

### `PoiManager`
Le mixin remplace `loadedChunks` par un set concurrent. Les opérations qui touchent le graphe de distance des villages passent sous le `PoiVillageLock` porté par `SectionStorage`, parce que ce graphe ne peut pas devenir concurrent par une simple façade.

### `EntitySectionStorage`, `EntityLookup`, `PersistentEntitySectionManager`, `ChunkMap.TrackedEntity`
Quatre façades concurrentes sur les index d'entités. `EntitySectionStorage` remplace `sections` et `sectionIds`. `EntityLookup` remplace `byId` et `byUuid`. `PersistentEntitySectionManager` remplace `knownUuids`. `TrackedEntity` remplace `seenBy`, le set des joueurs qui voient une entité trackée, et ré-ancre la base de position du tracker au premier appairage, parce qu'un projectile spawné sur une région appaire son premier spectateur un tick après sa création.

### `ServerEntity`
Le mixin expose le ré-ancrage de la base de position que `TrackedEntity` déclenche au premier appairage. Quand la construction et l'appairage partagent le tick, le calendrier vanilla, le ré-ancrage ne change rien.

### `ServerLevel.EntityCallbacks`
Les callbacks de section routent les entités vers les listes de tick de la région propriétaire au lieu des listes globales du niveau, parce que les listes globales seraient écrites par plusieurs régions en même temps.

### `PersistentEntitySectionManager.Callback`
Le mixin capture la section avant un `onMove` et rattache l'entité aux listes de sa nouvelle région quand elle change de chunk. Le rattachement se fait avant le `updateStatus` pour qu'un changement de visibilité de ticking à hidden trouve l'entrée dans la bonne liste.

### `Entity`
Le mixin retire le scheduler d'une entité quand elle quitte le monde. Il intercepte `teleport` depuis un worker de région pour dévier le mouvement vers le routeur de téléportation au lieu de l'exécuter sur place. Il reporte la recherche de portail dans la barrier window, parce que la recherche peut créer des blocs dans une autre dimension, comme la plateforme d'obsidienne de l'End.

### `ServerPlayer`
Le mixin remplace le set d'ender pearls par un set concurrent, parce qu'une perle lancée depuis une autre région s'inscrit depuis un autre thread. Il intercepte `teleport` depuis un worker de région pour dévier le mouvement vers le routeur de téléportation.

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
Le mixin porte la `PlayerPacketQueue` du joueur. La chaîne du chat signé, le filtrage des livres et pancartes, et le chat s'exécutent dans la file du joueur. Les commandes restent sur la phase globale parce qu'elles peuvent charger des chunks arbitraires. Le respawn passe par la barrier window parce qu'il replace le joueur dans une dimension potentiellement différente. L'ack de batch de chunks se traite dans la file du joueur, sur le même thread que celui qui envoie ses chunks. Les clics de conteneur passent par le garde `network/ContainerClickGuard` : vanilla coupe la synchronisation du menu pendant un clic sans la rétablir en cas d'exception, et un thread de région peut lever là où le main thread vanilla ne levait jamais, ce qui rendait l'inventaire menteur jusqu'à la reconnexion. Le garde rétablit la synchronisation, renvoie l'état complet au client et logge le clic fautif.

### `PlayerChunkSender`
Le mixin remplace le set des chunks en attente d'envoi par un set concurrent, parce que la phase sérielle y marque les chunks fraîchement prêts pendant que la région du joueur collecte et envoie. Les quotas et les compteurs de batch restent sur le thread propriétaire du joueur.

### `Connection`
Le tick de connexion est coupé en deux. Le flush et la détection de déconnexion restent sur le thread global. La physique du joueur, les menus et le keepalive s'exécutent sur la région propriétaire.

### `ServerCommonPacketListenerImpl`
Les envois de paquets sont groupés par tick de région pour qu'ils partent ensemble. La déconnexion depuis un thread autre que le thread serveur devient non bloquante pour éviter de bloquer un worker.

### `PlayerList`
Le mixin remplace `players`, `playersByUUID` et les tables de stats et d'advancements par des structures concurrentes. Le placement d'un nouveau joueur s'exécute sur la région qui possède son chunk de spawn, que la file de tâches matérialise au besoin. La sauvegarde prend le verrou exclusif du niveau. Le retrait exécute le corps vanilla entier sous la pause de toutes les régions, par `network/PlayerTeardown`. Un placement ou un retrait qui dépasse son seuil de durée se logge avec le nom du joueur, pour attribuer les saccades de connexion.

### `PrepareSpawnTask`
Le mixin de la tâche garde le tag NBT que `start` lit et lance la lecture des stats et des advancements sur le pool d'entrées sorties, par `network/JoinPreload`. Le mixin de l'état interne `Preparing` retient la tâche tant que les entités du spawn ne sont pas chargées, quand des joueurs sont en jeu, par `network/SpawnEntityWait`. Le mixin de l'état interne `Ready` sert le tag gardé à la place de la deuxième lecture disque et saute l'attente bloquante des entités quand elle est déjà satisfaite.

### `PlayerDataStorage`, `ServerStatsCounter` et `PlayerAdvancements`
Les trois fichiers d'un joueur, le NBT de playerdata, les stats et les advancements, se sérialisent sur le thread appelant, qui tient la pause ou l'exclusion stabilisant le joueur. L'écriture disque part sur le thread d'écriture unique de `global/DeferredFileWrites`, dans l'ordre des sauvegardes. Un seul mixin par classe vanilla tient la lecture, par `network/JoinPreload`. Le mixin sert d'abord l'écriture encore en attente, la plus récente, si bien qu'un joueur qui se reconnecte immédiatement ne relit jamais un fichier périmé. Il sert ensuite le contenu que `JoinPreload` a lu pendant la configuration, pour que le basculement en jeu ne touche pas le disque. Quand aucune des deux sources n'a de contenu, vanilla lit le disque lui-même. Le test d'existence du fichier compte une écriture en attente comme un fichier présent, pour que vanilla ne saute pas la lecture d'un joueur dont le fichier n'a pas encore atteint le disque.

### `BlockableEventLoop`
Un appel à `MinecraftServer.execute` depuis un worker de région est redirigé vers la phase globale, pour que la tâche s'exécute au bon moment au lieu de tourner sur le thread d'une région.

### `ServerTickRateManager`
Un changement de tick rate se propage à toutes les boucles de régions.

### `ServerWatchdog`
Le mixin neutralise le watchdog vanilla, qui mesure un unique game thread qui n'existe plus. Leafs le remplace par le watchdog par unité de tick de `ticking/LeafsWatchdog`, qui alerte puis tue, et réutilise le format de crash report du watchdog vanilla pour le dump de tous les threads.

### `SerializableChunkData`
Le mixin fait packer les ticks programmés par rapport à l'horloge de la région propriétaire du chunk, pas par rapport au game time global, parce que les régions n'ont pas toutes vécu le même nombre de ticks.

### Compatibilité Fabric API
`FabricApiLookupCacheShim` cible `ServerLevel` et synchronise la map de cache de `fabric-api-lookup` sur le niveau. `FabricLoadedChunksShim` cible `Level` et remplace le `HashSet` de chunks chargés de `fabric-lifecycle-events` par un set concurrent. Les deux s'appliquent en priorité 1100 pour que les méthodes de la Fabric API existent au moment de l'injection. `ArrayBackedEventShim` cible l'implémentation d'évènement de fabric-api-base et expose si un évènement a au moins un abonné, ce que l'API publique ne dit pas. `ServerTickEventsShim` est décrit dans l'entrée `MinecraftServer`.
