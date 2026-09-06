# Comment fonctionne Minecraft Vanilla
Minecraft gère le monde avec un seul thread en séquentiel. Donc plus il y a de joueurs, d'entités, de machines redstone et de joueurs qui génèrent et plus vous tirez sur les performances. Comme tout est en commun chaque joueur impacte les autres joueurs.
Le serveur fait des boucles de 50ms, 20 fois par seconde. Le fameux 20 TPS. Quand il y a trop de joueurs le serveur peut mettre plus de temps que ces 50ms à traiter un tick, donc le TPS chute. Ce qui ralentit tout sans exception, c'est le lag que vous ressentez.

## Problèmes
Donc ça veut dire que si vous avez 6 ou 12 ou 50 cœurs le jeu en prend un seul pour tout gérer. Donc si vous achetez du matériel plus cher, vous n'y gagnez rien. Minecraft a été développé il y a 15 ans et à l'époque il n'était pas courant d'avoir plusieurs cœurs, le jeu a donc été conçu pour son temps.
Leafs, imposes une régles simple, en étudiant les loi Ahmdal et Gustafon, le mods doit garantir de scales le nombre de joueurs/régions en fonction de la ram/thread disponible. Si ont veut plus de joueurs ont achéte plus de ram ou un meilleurs cpu de maniére linéare.

## La solution : Les régions
Leafs regarde les chunks simulés, ceux autour du joueur définis par la `simulation distance`, le monde est découpé en une grille fixe de 2x2 chunks définit par la config (`section_size`), et une section devient active quand un de ses chunks est simulé. Les sections actives qui se touchent sont regroupées en une région.

Les régions possèdent une couronne de 1 section autour des sections actives, qu'elles ne tickent pas. Deux couronnes de deux régions différentes ne se chevauchent jamais.

Deux joueurs éloignés sont donc dans une région différente chacun. Les régions bougent avec les joueurs. Deux joueurs qui se rapprochent voient leurs régions fusionner en une seule. Une région qui s'étire jusqu'à se couper en deux morceaux se scinde. Ces opérations se font entre deux ticks, jamais au milieu d'un tick.

Une région possède ses chunks, ses entités, ses joueurs, ses block entities, les paquets réseau de ses joueurs et son propre générateur aléatoire. Pendant son tick, personne d'autre n'écrit dans son contenu. Lire chez une autre région reste libre pour tout le monde.

# Thread
**Le thread serveur vanilla existe toujours.** Les régions tickent en même temps que lui. Il fait une fois par tick ce qui est global par nature, l'heure du monde, la météo, la bordure, la liste des joueurs et le déclencheur d'autosave. Il exécute aussi toutes les commandes. Son coût est fixe et minime, sans dépendre du nombre de chunks ou d'entités. Seule la liste des joueurs grandit avec eux, et son coût par joueur est infime. 

## Workers de Région
Une région n'est pas un thread ! Une région est une tâche. Les régions attendent dans une seule liste, triée par le moment du prochain tick. Un worker libre prend la première, la tick. Un worker occupé par une grosse région ne bloque personne, les autres prennent la suite.

Les TPS en vanilla sont globaux, sur Leafs ils sont par région. Chaque région a son propre TPS. Si une région est plus lourde cela baisse son TPS, cela n'affecte pas les autres régions qui gardent leur TPS au max.
- L'heure de la journée reste globale. Gérée par le thread global commun. Donc la météo, le soleil se couche à la même vitesse pour tout le monde peu importe vos TPS.
- Tout ce qui mesure une durée relative, la cuisson d'un four, les entités, la redstone, est géré par l'horloge de la région. Un four ne cuira pas à la même vitesse dans deux régions. Tout dépend du TPS.

La connexion et la déconnexion passent par le thread serveur, qui emprunte la région du joueur. Le respawn part de la région du joueur vers la région de son point de réapparition, sans passer par le thread serveur.

## Workers de Chunks
Les workers de chunks sont parfaitement indépendants des workers de régions. Ils génèrent, éclairent, chargent et déchargent les chunks, et préparent les octets à écrire sur le disque. Le thread disque de vanilla ne fait plus que lire et écrire ces octets.

Ces workers tournent en priorité système minimale sur le système d'exploitation. Quand la machine n'a plus assez de ressources pour tout le monde, les ticks de régions passent devant, parce qu'eux ont une échéance de 50 ms à tenir. Les chunks prennent le reste. Pour faire simple :
- Un joueur qui explore ne fait plus laguer les autres joueurs, même de sa propre région.
- Une zone très dense, avec un TPS bas, n'affecte pas la vitesse de génération du monde donc il peut continuer à se déplacer fluidement.

# La sauvegarde
- La commande `/save-all flush`  et l'arrêt du serveur, figent toutes les régions le temps de la sauvegarde, comme vanilla fige le serveur.
- L'autosave périodique, lui est fait par les régions.

# Emprunts et Courrier
Deux concepts de Multithread de Leafs simples.
**Courrier**: Quand une régions veut effectuer une tache sur une autre régions, il envoie un courrier, au début de sont tick elle lira tout les courrier dans l'ordre.
**L'emprunt**: Ils est utile notament aux `commandes`. Le thread serveur peut créer un emprunt en visant une entités/chunks cela emprunte leurs régions. Le thread serveur fait alors le travail lui-même, dans le même ordre que vanilla, et rend tout à la fin.

Une seule règle du projet: **le thread serveur ne touche jamais une région sans l'emprunter**, `Commandes`, `event Fabric`, `arrivée`, `départ`. 

# Les commandes
Toutes les commandes tournent sur le thread serveur, peu importe qui les lance.
Il emprunte une région au moment où la commande touche un de ses chunks ou une de ses entités, la garde jusqu'à la fin de la commande, puis la rend.

Ce que la commande touche décide de ce qu'elle emprunte :
- Un `/say` n'emprunte rien.
- Un `/give @a` emprunte les régions où il y a des joueurs.
- Un `/setblock` emprunte la région du chunk visé, et charge le chunk avant si besoin. Ce chargement bloque le thread serveur, comme en vanilla.
- Un `/kill @e` emprunte toutes les régions, parce que c'est ce que la commande veut dire.
Un datapack coûte donc exactement ce qu'il coûte en vanilla.

# Connexion et déconnexion
Le thread serveur gère l'arrivée et le départ d'un joueur. il emprunte la région du joueur le temps de l'opération, c'est moins d'une milliseconde. Les autres régions ne voient rien, aucun joueur ne ressent de différence, même sur une vague de 100 connexions.

# Compatibilité des mods.
Les primitives sont les méthodes dans le code de Minecraft qui sont les plus basses et les plus utilisées, où le plus de trafic passe par elles.
Leafs explore une voie assez simple, modifier toutes les primitives les plus basses de Minecraft, les fonctions de téléportation, de réseau, de lecture/écriture des chunks. Des portails, structures, entités...
Les mods utilisent ces fonctions sans le savoir et sont donc automatiquement compatibles.
Lithium/Ferrite/Mapple sont compatibles. C2ME, VMP, Moonrise sont incompatibles.

Lors du dév de Leafs, toutes a était penser pour que la moindre changement d'une fonction internes de mojang qui est utiliser par les moddeurs comme lire/écrire des chunks, blocs, ou de la teleportion. Soit parfaitement identiques en pratiques. Pour que les moddeurs n'est pas de mauvaises surprises. Les mods ne s'adpate pas a Leafs. C'est Leafs qui s'adaptes au mods. Leafs ne doit en aucuns cas créer de bugs, problémes. Sinon faites un ticket.

# Mapple
Leafs ne rajoute aucune optimisation, que ce soit `CPU`, `RAM`, `Garbage Collector` ou `load-time allocations`. N'importe quelle forme d'optimisation sera faite dans un mod indépendant nommé Mapple. Ce mod fonctionne avec ou sans Leafs comme un mod sans config/compromis, du pur gain. Mais pensé pour le meilleur gain possible pour le multithreading Leafs.
Sur les benchmark de Leafs, un joueur isolé au repos coûte 33 MiB de RAM avec Mapple au lieu de 52. Les chiffres à jour sont dans la doc de Mapple

# Debugging & Metrics
La création des metrics, la récupération des valeurs se fait dans Leafs. Il fournit toutefois des commandes simplifiées pour accéder à ces données.
`Leafs Debug and Metrics` est un mod indépendant, additionnel, qui permet l'affichage côté client F3, F8, F9 et l'analyse de la RAM.

# Thread Serveur
Le serveur fait dans l'ordre :
1. Lance le `tick.json` pour les commandes.
2. Puis met à jour l'heure du monde.
3. La dimension gére l'heure, la météo, la bordure, les tickets et la vue des joueurs, les déchargements, les spawn (phantoms, marchand...), puis demande en une ligne aux workers de chunks leur passe sur les chunks sans région. Les raids et le combat du dragon tickent sur la région qui possède leur centre
4. Tout ce qui est redirigé vers le thread global. Comme les `command blocks`, `respawn`, `commande du chat`.
5. Les requêtes réseau de chaque connexion de joueur. Le transport seulement, le tick du joueur tourne sur sa région.
6. La liste des joueurs.
7. L'horloge et le déclenchement de l'autosave, ce sont les régions et les workers de chunks qui font ensuite les sauvegardes.
8. Debug, Monitor. l'envoi des chunks aux joueurs

### Les coûts du thread serveur.
- Les points `2. Heure du monde, 7. Autosave et 8. Debug` sont des coûts purement fixes, toujours identiques peu importe le serveur et le nombre de joueurs.
- Les points `5. Réseau de la connexion, 6. Liste des joueurs` sont des coûts qui varient avec le nombre de joueurs, si infimes que d'un serveur à l'autre le coût est pratiquement identique.
- Les points `1. Tick.json, 4. Command blocks` sont liés aux commandes, donc des coûts évitables.
- Le point `3. Dimensions` a une quinzaine d'étapes, une bonne partie à zéro car déplacées sur les régions, le reste à coût fixe.

# Philosophie
Le mod se concentre beaucoup sur les lois d'Amdahl et Gustafson, l'objectif de Leafs est de permettre de scaler linéairement des joueurs selon les threads/RAM disponibles par l'infra. Bien sûr cela nécessite que les joueurs soient éparpillés dans le monde pour profiter des gains. Et c'est aussi recommandé de ne pas utiliser de commandes, même si le support existe et que son coût est le même que vanilla.

Pour cela le thread serveur a un coût relativement fixe, déterministe. Qui de plus tourne en parallèle des threads régions/chunks.
Durant le développement on essaye de ne rien mettre sur ce thread global, pour tirer parti de ces deux lois informatiques.
