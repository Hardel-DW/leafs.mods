# Chantier chunks

Ce document condense l'étude du 11 août des sources de Moonrise (le système de chunks de Paper, lu dans le patch `0001-Moonrise-optimisation-patches` du repo paper, adapté au vanilla moderne) et du patch Region Threading de Folia. Il fixe l'architecture cible de Leafs et le plan par étapes. La roadmap pointe ici.

Le dossier `..\moonrise-refs`, à côté des repos de référence, contient les sources extraites (ChunkHolderManager, ChunkTaskScheduler, RegionizedPlayerChunkLoader, ThreadedTicketLevelPropagator, et les primitives concurrentutil dont ReentrantAreaLock et AreaDependentQueue) et les quatre briefs d'étude détaillés : `brief-chunk-holder-manager.md`, `brief-task-scheduler-primitives.md`, `brief-player-chunk-loader.md`, `brief-folia-region-threading.md`. Toute session qui travaille ce chantier les lit d'abord.

## Ce que l'étude a établi

Moonrise n'a pas supprimé la coordination du système de chunks, il l'a shardée par coordonnées. Il reste un pas propriétaire, l'expiration des tickets, les baisses de niveau, les déchargements et les transitions de statut FULL, mais il s'exécute depuis le tick du propriétaire des coordonnées sans exclure personne d'autre. La sûreté vient de la géométrie : deux verrous de zone par monde, le ticket lock puis le scheduling lock, toujours dans cet ordre, au shift `max(shift de région, 6)`. Sous ces verrous ne passent que des métadonnées, jamais la génération, la lumière ou l'IO. Les tâches se construisent sous verrou et se démarrent après.

Folia consomme ce système tel quel depuis ses régions. Trois mécanismes portent tout l'édifice :
1. Des tickets concurrents adressés par position, posables depuis n'importe quel thread. Les hausses de niveau sont immédiates et concurrentes. Les baisses sont différées au tick propriétaire par un ticket `UNKNOWN` d'un tick, ce qui les rend déterministes.
2. Un point de routage unique des changements de statut, `addChangedStatuses` : le thread qui calcule dépose une tâche pour la région propriétaire, il ne fait jamais le travail lui-même.
3. La création de région pilotée par la création du chunk holder : une tâche destinée à une région qui n'existe pas encore pose un ticket qui matérialise le holder, donc la région, puis route la tâche. Leafs a déjà ce mécanisme, `SharedChunkHolds` plus `RegionScheduler.queue`.

Le propagateur de niveaux est une file de sections de 64x64 drainée coopérativement : plusieurs threads drainent en parallèle, chacun une section, et deux sections voisines se sérialisent entre elles. Notre `LeafsTicketPropagator` en ombre est déjà construit sur ce modèle, plafond de source à 62 compris.

Le chargement de vue est par joueur, `RegionizedPlayerChunkLoader` : des tickets nominatifs qui portent l'identité du joueur, trois niveaux (41 chargé, 33 généré, 31 tické), un ticket retardé au déchargement pour amortir les frontières, six files triées par distance au joueur, et trois limiteurs de débit par joueur (envoi, chargement, génération). Le tick du loader tourne sur le thread qui possède le joueur. Paper a débranché la boucle d'acquittement client de vanilla, le débit est une politique serveur.

## Ce que Leafs a déjà

- `AreaLock`, même sémantique que le `ReentrantAreaLock` de Moonrise, repli complet avant blocage compris.
- `LeafsTicketPropagator` en ombre sous assertions dev, comparé au graphe vanilla à chaque drain.
- La matérialisation de région par ticket, et l'invariant de Folia « une région en cours de tick ne gagne jamais de section », porté par `mergeIntoLater` dans le `Regionizer`.
- L'état de monde régionalisé avec fusion et scission : listes d'entités, ticks programmés, block events, tickers, horloges. C'est la moitié de la facture que Folia a payée, elle est déjà payée chez nous.
- La table de tickets vanilla sous moniteur, posable depuis n'importe quel thread.

Ce qui manque : le cœur concurrent. Chez nous la phase sérielle de chaque dimension possède les promotions, la propagation et les déchargements, et s'exclut avec les ticks de régions. C'est le goulot mesuré à 60 joueurs.

## Les invariants à copier tels quels

- Ordre de verrouillage ticket puis scheduling, jamais l'inverse, et jamais de zones sécantes partiellement possédées.
- Sous les verrous de zone, uniquement des métadonnées. Le travail lourd tourne dehors.
- Construire les tâches sous verrou, les démarrer après libération.
- Les baisses de niveau appartiennent au propriétaire, différées par ticket `UNKNOWN`.
- Le calculateur ne touche jamais l'état du propriétaire, il route par la file de tâches de la région, et le ticket de matérialisation garantit qu'une région propriétaire existe.
- Toute file par région porte un drapeau de destruction, et le producteur reboucle en relisant la région, sinon les tâches se perdent pendant une fusion.
- Un comparateur de tâches spatiales doit produire le même ordre partout, départagé par un identifiant global, sinon deux tâches qui se chevauchent s'interbloquent.
- Statuts parallélisables en génération : uniquement ceux qui ne lisent pas les blocs des voisins. FEATURES s'exclut par boîte 3x3, la lumière par 5x5, via une file à dépendance de zone.

## Les pièges relevés

- Le double buffer `updatingChunkMap` / `visibleChunkMap` de `ChunkMap` est incompatible avec des holders concurrents. Il se supprime, il ne se protège pas. Il n'y a pas de demi-portage du cœur.
- Le pool de Moonrise est petit par défaut, le gain vient du fait que plus personne ne bloque, pas du volume de threads. Son pool « équilibré » est inerte avec un seul groupe : une file priorisée unique et N workers font pareil en plus simple.
- Le chargement synchrone d'un chunk non FULL doit être asynchrone dans un monde régionalisé, Folia a dû le retravailler après coup, nous le concevons asynchrone d'emblée.
- Les transitions FULL, la publication du chunk dans le monde vivant et les déchargements restent au thread propriétaire des coordonnées. Chez nous ce propriétaire est la région, avec la phase sérielle en secours pour les positions sans région.
- Le prérequis dur du chargeur par joueur est le ticket identifié : le `Ticket` vanilla ne porte aucune identité.

## Le plan

Étape 0, les fondations, sur le système vanilla actuel, chacune livrable seule :
- 0a. Fait le 11 août : l'ombre validée, six heures à 550 joueurs plus une session de bots, zéro désaccord logué.
- 0b. Fait le 11 août au soir : le propagateur pilote au point de drain sériel. Le listener de chargement le nourrit en ligne sous le moniteur, le drain sériel écrit les niveaux des holders, le budget de 4096 a disparu et le drain vanilla reste en filet à vide pour les mises à jour d'avant le câblage. À valider en jeu : aucun chunk qui ne charge jamais ou ne décharge jamais, les comptes de `/leafs regions` stables.
- 0c. Le ticket `UNKNOWN` sur le retrait : les baisses de niveau deviennent déterministes au tick propriétaire. À faire avec le cœur, c'est lui qui libère le drain de la phase sérielle.

Étape 1, le cœur, un seul bloc parce qu'il ne se découpe pas : notre couche d'ordonnancement remplace les promotions vanilla. Un holder d'ordonnancement par chunk (statut de génération, dépendances de voisins, priorités remontées par les demandeurs), les deux verrous de zone, les tâches de progression sur notre pool, le routage des statuts FULL vers la région propriétaire par la file de tâches existante, les déchargements en trois temps chez le propriétaire. Le double buffer de `ChunkMap` et la pompe du `MainThreadExecutor` meurent. La phase sérielle cesse d'exclure les régions pour les chunks et garde la météo, le temps et les horloges.

Étape 2, le chargeur de chunks par joueur : ticket nominatif, trois niveaux, ticket retardé, files par distance, limiteurs par joueur, tick sur la région du joueur. Le guichet d'admission global disparaît. Un palier intermédiaire est possible avant l'étape 1, sur le `TicketStorage` vanilla avec un seul type de ticket identifié.

Étape 3, la lecture concurrente des chunks FULL publiés : les refus de lecture disparaissent, la génération de portail sort de la fenêtre barrière.

Étape 4, le placement parallèle des joueurs à la connexion, dernière brique de la fluidité.

Le pool de workers se décide pendant l'étape 1 : une file priorisée, N workers, et l'arbitrage entre ticks de régions et génération comme seul vrai réglage.
