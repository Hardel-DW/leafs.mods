# Comment fonctionne Minecraft Vanilla
Minecraft gère le monde avec un seul thread en séquentiel. Donc plus y'a de joueurs, d'entités, de machines redstones et de joueurs qui génèrent et plus vous tirez sur les performances. Comme tout est en commun chaque joueur impacte les autres joueurs.
Le serveur fait des boucles de 50ms, 20 fois par seconde. Le fameux 20 TPS. Quand y'a trop de joueurs le serveur peut mettre plus de temps que ces 50ms a traiter un tick. donc le TPS chute. Ce qui ralentit tout sans exception c'est le lag que vous ressentez.

## Problèmes
Donc ça veut dire que si vous avez 6 ou 12 ou 50 cœurs le jeu en prend un seul pour tout gérer. Donc si vous achetez du matériel plus cher, vous n'y gagnez rien. Minecraft a été développé il y a 15 ans et à l'époque il n'était pas courant d'avoir plusieurs cœurs, le jeu a donc été conçu pour son temps.

## La solutions: Les régions
Leafs découpe le monde en sections de 16x16 chunks autour des joueurs en `Sections`. Les sections actives qui se touchent sont regroupées en une région. Avec toujours au moins une section vide entre deux régions. Cette marge vide est ce qui rend le système sûr. Deux joueurs éloignés sont donc dans une région différente chacun.
Les régions bougent avec les joueurs. Deux joueurs qui se rapprochent voient leurs régions fusionner en une seule. Une région qui s'étire jusqu'à se couper en deux morceaux se scinde. Ces opérations se font entre deux ticks, jamais au milieu d'un tick.

Une région possède ses chunks, ses entités, ses joueurs, ses block entities, les paquets réseau de ses joueurs et son propre générateur aléatoire. Pendant son tick, et rien d'autre au monde ne touche à son contenu.

# Thread
**Le thread serveur vanilla existe toujours.** Tout ce qu'il exécute est sériel, donc chaque milliseconde passée ici est une milliseconde où le serveur ne parallélise rien. Son coût est parfaitement déterministe et minime, sans dépendre du nombre de joueurs, de chunks ou d'entités.
Il fait une fois par tick ce qui est global par nature et n'appartient à personne:E l'heure du monde, la météo, la liste des joueurs, le déclencheur d'autosave.

## Workers de Région
Une région n'est pas un thread ! Une région est une tâche nommés Workers. Ça veut dire que 200 régions tournent très bien sur 8 threads. Cela fonctionne un peu comme les caisses de supermarché Dans l'idée à chaque tick ils vont choisir la file ayant le moins de monde. Ce qui par nature équilibre parfaitement la charge.

Les TPS en vanilla sont globaux sur Leafs ils sont par région. Chaque région a son propre TPS. Si une Région A exige plus de ressources cela baisse son TPS, cela n'affecte pas les autres régions qui gardent leurs TPS au max.
- L'heure de la journée reste globale. Gérée par le thread global commun. Donc la météo, le soleil se couche à la même vitesse pour tout le monde peu importe vos TPS.
- Tout ce qui mesure une durée relative, la cuisson d'un four, les entités, redstone, sont gérés par l'horloge de la région. Un four ne cuira pas à la même vitesse dans deux régions. Tout dépend du TPS.

Tous ce qui est liés a la téléportation c'est a dire, connexion/deconnexion/portail/respawn et autre. sont gérer avec les régions de départ et d'arriver, elles communique entre eux sans passer par le commun.

## Workers de Chunks
Les workers de chunks, parfaitement indépendants des workers de régions. Il gère la lecture des chunks sur le disque, il génère, charge et décharge les chunks. Plusieurs chunks à la fois par dimension. Ces workers tournent en priorité système minimale sur le systémes d'exploitation. Quand la machine n'a plus assez de ressources pour tout le monde, les ticks de régions passent devant, parce qu'eux ont une échéance de 50 ms à tenir. Les chunks prennent le reste. Pour faire simple :
- Un joueur qui explore ne fait plus laguer les autres joueurs, même de sa propre régions.
- Une zones trés denses, avec un TPS bas n'affecte pas la vitesse de générations du mondes donc il peut continuer a se déplacer fluidement.
- Si il y'a qu'une seul régions et que vous avez plusieurs workers de chunks. Les chunks charge proportionnellement plus vite au nombre de workers.

# La fenêtre barrière.
Ccette fenêtre permet temporairement de synchroniser le monde. C'est utilisée principalement pour les commandes et les évenements Fabric.
Le thread global met toutes les régions en pause, imperceptible sans impact sur les performances ou l'expérience de jeu, exécute ces actions une par une avec l'accès complet au monde, puis relâche tout. Elle peut s'ouvrir au plus une fois par tick global, cette fenêtre doit s'ouvrir le moins possible.
Deux gamerules existe pour désactiver le tag `#minecraft:tick` et les command blocks à répétition. Car ils détruisent un peu le parallélisme des régions et resynchronisent à chaque tick les régions.

# Connexion et déconnexion
La connexion et déconnexion sont partiellement modifier, elles sont asynchrones de manière à ce que ces deux tâches n'aient aucun impact de lag sur le serveur. L'objectif est qu'aucun joueur ne ressente le moindre tick de différence dans son expérience.
- Quand un joueur se connecte, Ses fichiers sont lu pendant l'écran de connexion, le placement du joueurs tourne sur la régions de sont point d'apparition.
- Quand un joueur se déconnecte, c'est instantané pour lui, mais les données joueur, chunks et la région peuvent prendre quelque temps avant de se décharger. Quand beaucoup de joueurs se déconnectent, tout faire d'un coup créerait un grand coup de lag. C'est pour cela qu'on délaye ça proprement sur le temps.

# Compatibilité des mods.
Primitives sont les méthodes dans le code de Minecraft qui sont les plus basses est sont les plus utiliser, ou le plus de traffic passe par elle.
Leafs explore une voie assez simple,  modifié toutes les primitives les plus basses de Minecraft les fonctions de téléportation, de réseau, de lecture/écriture des chunks. Des portails, structures, entités...
Les mods utilisent ces fonctions sans le savoir et sont donc automatiquement compatibles.
La listes des mods imcompatible n'est pas encore défini. Non tester. Pour sûr Lithium/Ferrite/Mapple seront compatible.

# Mapple
Leafs ne rajoute aucune optimisation que ça soit `CPU`, `RAM`, `Garbage Collector` ou `load-time allocations`. N'importe quelle forme d'optimisation sera faite dans un mod indépendant nommé Mapple. Ce mod fonctionne avec ou sans Leafs comme un mod sans config/compromis, du pur gain. Mais penser pour le meilleurs gains possible pour le multithreading Leafs.
Actuellement ce mod réduit la consommation de la RAM d'environ 40%. Le Garbage Collector de 73% et l'allocation au chargement de 77%. Ce qui permet de créer davantage de régions.

# Debuging & Metrics
La créations des metrics, récupération des valeurs se fait dans Leafs, Elle fourni toutes fois des commandes simplifier pour accéder a ces données.
`Leafs Debug and Metrics` est un mods indépendant, additionnel qui permet l'affichage cotés client F3, F8, F9 et l'analyse de la RAM.