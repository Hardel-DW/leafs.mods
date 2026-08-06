# Fonctionnement
Ce document décrit chaque système en place, ce qu'il fait et où il vit dans le code.

## Le tick de région
Le corps du tick vit dans `world/RegionTickBody`. À chaque tick, une région exécute dans l'ordre vanilla toutes les phases ancrées à des chunks : le drain des paquets de ses joueurs, les ticks programmés de blocs et de fluides, l'orage, le spawn naturel, les random ticks, la diffusion des changements de chunks, le tracking d'entités, les évènements de blocs, le tick des entités, les block entities, et pour finir le tick réseau complet de chaque joueur possédé. Chaque phase travaille uniquement sur les chunks que la région possède.

Ce qui reste au tick sériel du niveau, dans `ticking/LevelTickUnit`, c'est ce qui est global par nature : la bordure du monde, la météo, le sommeil, le temps, les raids, le combat du dragon, et surtout le système de chunks vanilla, chargements, déchargements et diffs de vue des joueurs.

## Le réseau
Le principe est de redonner au joueur le contrat vanilla d'un seul thread. La région qui possède un joueur draine ses paquets entrants en début de tick et exécute son tick de listener en fin de tick, ce qui inclut la physique du joueur, ses menus, sa nourriture, le keepalive et les kicks. Le code est dans `network/RegionNetworkTick` et `network/PlayerPacketQueue`.

Le thread global garde le transport : le flush des files d'envoi, la détection des déconnexions, et l'envoi des chunks aux clients au même endroit que vanilla. Il sert aussi de filet de sécurité : une connexion dont aucune région ne s'occupe depuis un quart de seconde, un joueur dans le générique de fin par exemple, retombe automatiquement sur le tick global vanilla complet. Ce mécanisme n'a pas de liste à maintenir, chaque région estampille les joueurs qu'elle traite et le global observe la fraîcheur de la marque.

Le drain revérifie la propriété entre chaque paquet, car un handler peut téléporter le joueur ailleurs en plein milieu. Les continuations de handlers, la chaîne du chat signé, le filtrage des livres et pancartes, s'exécutent dans la file du joueur, en ordre avec ses paquets. Les commandes tapées dans le chat restent sur la phase globale, parce qu'une commande comme /locate doit pouvoir charger des chunks arbitraires, ce que seul le thread serveur sait faire. Le login et la configuration restent également sur le thread global.

## Les téléportations et portails
Tout mouvement qui sort du périmètre d'une région est routé au lieu d'être exécuté sur place. Le code central est `entity/EntityTeleports`.

Un déplacement dans la même dimension qui reste dans la région propriétaire s'exécute en direct, comme vanilla. S'il en sort, il est rejoué au prochain tick sériel du niveau, sous le verrou exclusif. Un changement de dimension d'une entité normale passe par un pipeline asynchrone : l'arbre monture passagers est détaché côté origine, recopié, et replacé côté destination comme une tâche de la région d'arrivée. À l'extinction du serveur, tout ce qui est en vol est posé avant la sauvegarde, aucune entité ne se perd.

Les portails du Nether et de l'End passent par la fenêtre barrière : la recherche de destination crée des blocs dans une dimension qu'on ne connaît qu'après l'avoir interrogée, la plateforme d'obsidienne de l'End par exemple, donc elle s'exécute avec toutes les régions en pause et la sémantique vanilla exacte. N'importe quel portail moddé qui implémente l'interface vanilla `Portal` bénéficie du même traitement. Le respawn d'un joueur suit le même chemin, sa branche vanilla entière rejouée dans la fenêtre.

## Les chunks
Le système de chunks reste celui de vanilla, piloté par le thread serveur. Les régions y accèdent en lecture par la carte visible des chunks, via `chunk/RegionChunkAccess`, et n'ont jamais le droit de déclencher un chargement synchrone : là où vanilla comblerait un trou en chargeant le chunk sur le champ, une région refuse proprement. Ces refus sont absorbés par `ownership/TickGuard` : l'entité, la passe de spawn ou la recherche de structure concernée saute son tick ou renvoie le résultat vanilla non trouvé, et réessaie naturellement quand le chunk finit d'arriver.

La pompe de gestion des chunks ne tourne jamais pendant qu'une région de la dimension est en plein tick, ce qui ordonne les promotions de chunks vis à vis du jeu. Un chunk situé dans une région vivante ne se décharge pas.

## Les données partagées
Les quelques structures que vanilla partage à l'échelle du niveau ou du serveur ont été rendues sûres une par une. Les index d'entités et de points d'intérêt sont devenus des structures concurrentes, y compris un index ordonné de longs fait sur mesure pour le chemin de requête le plus chaud du jeu, dans `entity/`. Le scoreboard, les données sauvegardées, les cartes et les séquences aléatoires passent sous un moniteur partagé, dans `global/SharedStateMonitor`. Le graphe de distance des villages, qui ne peut pas être rendu concurrent, est sérialisé par un petit verrou dédié.

## Les gamerules
Un command block en repeat et les fonctions de datapack du tag `#minecraft:tick` s'exécutent dans la fenêtre barrière, donc ils mettent toutes les régions en pause à chaque tick. Leafs ajoute deux gamerules pour couper ce coût quand un serveur n'a pas besoin de ce contenu en continu. Les deux valent `true` par défaut, et le comportement vanilla est alors intact.

`leafs:tick_functions_work` à `false` coupe la boucle du tag `#minecraft:tick`. La commande `/function` et le tag `#minecraft:load` continuent de fonctionner, et un `/reload` joue encore sa fenêtre de rechargement, qui exécute `#minecraft:load` et une seule passe de `#minecraft:tick`.

`leafs:repeating_command_blocks_work` à `false` fait sauter l'exécution des command blocks en repeat sans les désarmer. Chaque bloc coupé se réarme à vide sur son propre thread de région, sans ouvrir la fenêtre, et repart tout seul quand la règle revient à `true`. Les command blocks en impulse et en chain continuent de fonctionner pour les commandes ponctuelles.

## La compatibilité mods
L'approche est générique avant tout. Les payloads réseau de la Fabric API arrivent sur le thread qui possède le joueur, ce que leur vérification de thread accepte naturellement. Les mods qui lisent ou écrivent l'état d'un joueur depuis un handler retrouvent la cohérence single thread de vanilla. Un appel à `MinecraftServer.execute` depuis n'importe quel thread aboutit dans la phase globale au lieu de lever une exception comme chez Folia. Le principe directeur : un mod qui suppose un seul thread ne doit jamais corrompre le monde, au pire il se dégrade, il ne fait jamais crasher une autre région.

Quand un mod d'optimisation entre en conflit structurel, la réponse passe par ses propres options : le `fabric.mod.json` de Leafs désactive quatre règles de Lithium dont les caches ne survivent pas à des régions concurrentes, et Lithium coupe leurs dépendances tout seul. Les incompatibilités définitives se déclarent dans le manifest, une liste courte et nominative où chaque entrée se justifie par le conflit précis. La liste est vide.

## Le débogage
- La commande `/leafs regions` liste les régions vivantes par dimension : id, état, TPS et durée moyenne de tick, nombre de chunks et d'entités, avec un marqueur sur la région du joueur. Un enregistreur optionnel écrit ces mesures par région dans un CSV, activé par `metrics_log_seconds` dans la config.
- La commande `/leafs recommendation` lit l'état du serveur et suggère des actions qui améliorent le parallélisme.

Chaque ligne de log émise par un worker porte le préfixe de sa région, `R#id` et la dimension, et l'option `per_region_logs` sépare les journaux par région. Quand une région crashe, elle écrit un rapport dédié dans `crash-reports/`, avec son id, sa dimension, son tick, ses chunks et ses entités : on doit pouvoir comprendre un crash de région sans fouiller un log global. Un watchdog par région signale les régions bloquées avant que le garde fou de vanilla ne tue le serveur, seuil réglé par `watchdog_warn_seconds`.
