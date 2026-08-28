# Comment fonctionne Minecraft Vanilla
Minecraft gère le monde avec un seul thread en séquentiel. Donc plus il y a de joueurs, d'entités, de machines redstone et de joueurs qui génèrent et plus vous tirez sur les performances. Comme tout est en commun chaque joueur impacte les autres joueurs.
Le serveur fait des boucles de 50ms, 20 fois par seconde. Le fameux 20 TPS. Quand il y a trop de joueurs le serveur peut mettre plus de temps que ces 50ms à traiter un tick, donc le TPS chute. Ce qui ralentit tout sans exception, c'est le lag que vous ressentez.

## Problèmes
Donc ça veut dire que si vous avez 6 ou 12 ou 50 cœurs le jeu en prend un seul pour tout gérer. Donc si vous achetez du matériel plus cher, vous n'y gagnez rien. Minecraft a été développé il y a 15 ans et à l'époque il n'était pas courant d'avoir plusieurs cœurs, le jeu a donc été conçu pour son temps.

## La solution : Les régions
Leafs regarde les chunks simulés, ceux autour du joueur définis par la `simulation distance`, le monde est découpé en une grille fixe de 2x2 chunks, et une section devient active quand un de ses chunks est simulé. Les sections actives qui se touchent sont regroupées en une région.

Les régions possèdent une couronne de 1 section autour des sections actives qu'elles ne tickent pas. Et deux couronnes de deux régions différentes ne se touchent jamais. Et il y a toujours au moins une section vide entre deux couronnes.

Deux joueurs éloignés sont donc dans une région différente chacun. Les régions bougent avec les joueurs. Deux joueurs qui se rapprochent voient leurs régions fusionner en une seule. Une région qui s'étire jusqu'à se couper en deux morceaux se scinde. Ces opérations se font entre deux ticks, jamais au milieu d'un tick.

Une région possède ses chunks, ses entités, ses joueurs, ses block entities, les paquets réseau de ses joueurs et son propre générateur aléatoire. Pendant son tick, rien d'autre au monde ne touche à son contenu.

# Thread
**Le thread serveur vanilla existe toujours.** Les régions tickent en même temps que lui. Il fait une fois par tick ce qui est global par nature, l'heure du monde, la météo, la bordure, la liste des joueurs et le déclencheur d'autosave. Il exécute aussi toutes les commandes. Son coût est parfaitement déterministe et minime, sans dépendre du nombre de joueurs, de chunks ou d'entités.

## Workers de Région
Une région n'est pas un thread ! Une région est une tâche. Les régions attendent dans une seule liste, triée par le moment du prochain tick. Un worker libre prend la première, la tick. Un worker occupé par une grosse région ne bloque personne, les autres prennent la suite.

Les TPS en vanilla sont globaux, sur Leafs ils sont par région. Chaque région a son propre TPS. Si une région est plus lourde cela baisse son TPS, cela n'affecte pas les autres régions qui gardent leur TPS au max.
- L'heure de la journée reste globale. Gérée par le thread global commun. Donc la météo, le soleil se couche à la même vitesse pour tout le monde peu importe vos TPS.
- Tout ce qui mesure une durée relative, la cuisson d'un four, les entités, la redstone, est géré par l'horloge de la région. Un four ne cuira pas à la même vitesse dans deux régions. Tout dépend du TPS.

Tout ce qui est lié à la téléportation, c'est-à-dire connexion/déconnexion/portail/respawn et autres, est géré avec les régions de départ et d'arrivée, elles communiquent entre elles sans passer par le commun.

## Workers de Chunks
Les workers de chunks sont parfaitement indépendants des workers de régions. Ils gèrent la lecture des chunks sur le disque, ils génèrent, chargent et déchargent les chunks. Ces workers tournent en priorité système minimale sur le système d'exploitation. Quand la machine n'a plus assez de ressources pour tout le monde, les ticks de régions passent devant, parce qu'eux ont une échéance de 50 ms à tenir. Les chunks prennent le reste. Pour faire simple :
- Un joueur qui explore ne fait plus laguer les autres joueurs, même de sa propre région.
- Une zone très dense, avec un TPS bas, n'affecte pas la vitesse de génération du monde donc il peut continuer à se déplacer fluidement.
- Chaque joueur est plafonné à 5 chunks par tick. (Configurable par `player_chunk_loads_per_tick`)

# Les commandes
Toutes les commandes tournent sur le thread serveur, peu importe qui les lance.
Il emprunte une région au moment où la commande touche un de ses chunks ou une de ses entités, la garde jusqu'à la fin de la commande, puis la rend.

Ce que la commande touche décide de ce qu'elle emprunte :
- Un `/say` n'emprunte rien.
- Un `/give @a` emprunte les régions où il y a des joueurs.
- Un `/setblock` emprunte la région du chunk visé, et charge le chunk avant si besoin.
- Un `/kill @e` emprunte toutes les régions, parce que c'est ce que la commande veut dire.
Un datapack coûte donc exactement ce qu'il coûte en vanilla.

# Connexion et déconnexion
La connexion et la déconnexion sont partiellement modifiées, elles sont asynchrones de manière à ce que ces deux tâches n'aient aucun impact de lag sur le serveur. L'objectif est qu'aucun joueur ne ressente le moindre tick de différence dans son expérience.
- Quand un joueur se connecte, ses fichiers sont lus pendant l'écran de connexion, le placement du joueur tourne sur la région de son point d'apparition.
- Quand un joueur se déconnecte, même une vague de 100 joueurs, cela n'affecte pas les autres joueurs.

# Compatibilité des mods.
Les primitives sont les méthodes dans le code de Minecraft qui sont les plus basses et les plus utilisées, où le plus de trafic passe par elles.
Leafs explore une voie assez simple, modifier toutes les primitives les plus basses de Minecraft, les fonctions de téléportation, de réseau, de lecture/écriture des chunks. Des portails, structures, entités...
Les mods utilisent ces fonctions sans le savoir et sont donc automatiquement compatibles.
Lithium/Ferrite/Mapple sont compatibles. C2ME, VMP, Moonrise sont incompatibles.

# Mapple
Leafs ne rajoute aucune optimisation, que ce soit `CPU`, `RAM`, `Garbage Collector` ou `load-time allocations`. N'importe quelle forme d'optimisation sera faite dans un mod indépendant nommé Mapple. Ce mod fonctionne avec ou sans Leafs comme un mod sans config/compromis, du pur gain. Mais pensé pour le meilleur gain possible pour le multithreading Leafs.
Actuellement ce mod réduit la consommation de la RAM d'environ 40%. Le Garbage Collector de 73% et l'allocation au chargement de 77%. Ce qui permet de créer davantage de régions.

# Debugging & Metrics
La création des metrics, la récupération des valeurs se fait dans Leafs. Il fournit toutefois des commandes simplifiées pour accéder à ces données.
`Leafs Debug and Metrics` est un mod indépendant, additionnel, qui permet l'affichage côté client F3, F8, F9 et l'analyse de la RAM.

# Thread Serveur
Le serveur fait dans l'ordre :
1. Lance le `tick.json` pour les commandes.
2. Puis met à jour l'heure du monde.
3. Tick chaque dimension.
4. Tout ce qui est redirigé vers le thread global. Comme les `command blocks`, `respawn`, `commande du chat`.
5. Les requêtes réseau de chaque connexion de joueur. Quand la région possède le joueur c'est elle qui s'en occupera.
6. La liste des joueurs.
7. Envoi des chunks aux joueurs que personne ne possède.
8. La quiesce.
9. L'horloge et le déclenchement de l'autosave, ce sont les régions qui s'occupent ensuite des opérations d'autosave.
10. Debug, Monitor.

### Les coûts du thread serveur.
- Les points `2. Heure du monde, 9. Autosave et 10. Debug` sont des coûts purement fixes, toujours identiques peu importe le serveur et le nombre de joueurs.
- Les points ` 5. Players Tabs, 6. Réseau de la connexion` sont des coûts qui varient avec le nombre de joueurs, qui sont si infimes que d'un serveur à l'autre le coût est pratiquement identique.
- Les points `1. Tick.json 4. Command blocks` sont fortement liés aux commandes, donc des coûts évitables/désactivables.
- Le point `3. Dimensions` a lui une quinzaine d'étapes, une bonne partie à zéro car déplacées sur les régions, ou à des coûts fixes.
- Les points `7. Envoie des chunk, 8. Quiesce` sont les seul cout qui ne sont pas fixes.

# Philosophie
Le mod se concentre beaucoup sur les lois d'Amdahl et Gustafson, l'objectif de Leafs est de permettre de scaler linéairement des joueurs selon les threads/RAM disponibles par l'infra. Bien sûr cela nécessite que les joueurs soient éparpillés dans le monde pour profiter des gains. Et c'est aussi recommandé de ne pas utiliser de commandes, même si le support existe et que son coût est le même que vanilla.

Pour cela le thread serveur a un coût relativement fixe, déterministe. Qui de plus tourne en parallèle des threads régions/chunks.
Durant le développement on essaye de ne rien mettre sur ce thread global, pour tirer parti de ces deux lois informatiques.
