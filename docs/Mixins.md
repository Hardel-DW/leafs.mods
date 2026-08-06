# Mixins

## Philosophie
Chaque mixin s'écrit dans l'optique de la meilleure compatibilité inter mods. Une injection ciblée, un `@Inject` ou un wrap, se compose avec les mixins des autres mods sur la même méthode. Un `@Overwrite` détruit le corps que les autres visent : c'est le dernier recours absolu, et s'il en existe un jour, sa javadoc justifie pourquoi rien d'autre n'était possible. Il n'y en a aucun aujourd'hui.

Un mixin est un point d'accroche, jamais un lieu de logique. Le corps d'un mixin tient en un appel vers le module qui possède la logique, et sa javadoc dit en une phrase pourquoi ce point d'accroche existe. Les primitives de concurrence, verrous, atomiques, pools, vivent dans nos classes, jamais dans un corps de mixin. Chaque mixin a une raison unique d'exister, liée au système de régions. Cette discipline est ce qui rend le mod lisible et compatible : la surface de contact avec vanilla est petite, nommée et justifiée.
Tout est côté serveur. Le mod n'embarque aucun code client.

## La liste par domaine

### chunk, l'accès aux chunks et aux points d'intérêt
`ServerChunkCacheMixin` fait répondre les lectures de chunks des régions par la carte visible, sans jamais autoriser un chargement synchrone. `ChunkMapMixin` et `DistanceManagerMixin` accrochent la sauvegarde concurrente et les compteurs de spawn. `PoiManagerMixin` et `SectionStorageMixin` posent les structures concurrentes des points d'intérêt et le verrou du graphe des villages. `ServerLevelMixin` route le chemin des block entities.

### entity, les entités et leurs index
`EntitySectionStorageMixin`, `EntityLookupMixin`, `PersistentEntitySectionManagerMixin` et `TrackedEntityMixin` remplacent les index d'entités du niveau par des structures concurrentes. `EntityCallbacksMixin` et `SectionCallbackMixin` routent les entités vers les listes de tick de leur région. `EntityMixin` et `ServerPlayerMixin` sont les portes de téléportation : un mouvement qui sort de la région propriétaire est routé au lieu de s'exécuter sur place. `ServerLevelMixin` porte les points de passage protégés d'ajout et de retrait de joueurs et d'entités. `MinecraftServerMixin` et `ChunkMapMixin` accrochent les schedulers d'entités et le split du tracking.

### global, l'état partagé du serveur
`ScoreboardMixin`, `ServerScoreboardMixin`, `SavedDataStorageMixin`, `MapIndexMixin`, `MapItemSavedDataMixin`, `RandomSequencesMixin` et `ServerWaypointManagerMixin` placent l'état que vanilla partage au niveau serveur sous le moniteur commun. `CommandBlockMixin` et `MinecartCommandBlockMixin` envoient l'exécution des command blocks dans la fenêtre barrière. `MinecraftServerMixin` ouvre la fenêtre et draine la file globale. `DedicatedServerMixin` et `ServerLevelMixin` complètent les entrées globales.

### network, le réseau
`PacketProcessorMixin` et `PacketUtilsMixin` répondent aux vérifications de thread de vanilla et de la Fabric API par la notion de drain en cours. `ServerGamePacketListenerImplMixin` porte la file du joueur, route les continuations de handlers vers elle, rejoue le respawn dans la fenêtre et renvoie l'ack des chunks au thread d'envoi. `ConnectionMixin` coupe le tick de connexion en deux, transport au global, jeu sur la région. `ServerCommonPacketListenerImplMixin` groupe les envois par tick de région et rend la déconnexion non bloquante hors thread serveur. `PlayerListMixin` rend les listes de joueurs concurrentes, protège la sauvegarde et le retrait d'un joueur.

### ticking, l'orchestration
`MinecraftServerMixin` fait passer chaque tick de niveau par son unité de région et maintient le drain en pause. `BlockableEventLoopMixin` route les `execute` hors thread vers la phase globale. `ChunkMainThreadExecutorMixin` est la pompe sous garde : la gestion des chunks d'une dimension n'avance jamais pendant qu'une de ses régions tique. `ServerTickRateManagerMixin` applique les commandes de tick rate à toutes les boucles. `ServerWatchdogMixin` branche notre watchdog par région. `ServerLevelMixin` complète le câblage par niveau.

### world, le tick du monde
`ServerLevelMixin` route tout ce qui est indexé par position vers la région propriétaire, ticks programmés, évènements de blocs, horloge, générateur aléatoire, mises à jour de voisinage, et fait dégrader proprement les recherches de structures côté région. `LevelMixin` route l'enregistrement des block entities et le chemin `getBlockEntity`. `ServerChunkCacheMixin` découpe le reste sériel du tick des chunks. `SerializableChunkDataMixin` accompagne la sauvegarde concurrente.

### compat, les shims Fabric API
`FabricApiLookupCacheShim` et `FabricLoadedChunksShim` adaptent deux caches internes de la Fabric API qui supposaient un seul thread.
