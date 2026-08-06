# Leafs
Leafs est un mod Fabric côté serveur qui fait tourner le monde de Minecraft en parallèle. Dans un serveur classique, tout le monde partage un seul fil d'exécution : chaque entité, chaque machine, chaque joueur attend son tour sur le même thread. Leafs découpe le monde en régions indépendantes, et chaque région vit son propre tick à 20 TPS sur un pool de threads. Plus il y a de zones actives éloignées les unes des autres, plus le serveur utilise de cœurs.

L'idée vient de Folia, le fork de Paper. La différence, c'est que Leafs n'est pas un fork : c'est un mod, installé comme n'importe quel autre, qui transforme le serveur par mixins. Pas de jar patché, pas de launcher spécial. Et là où Folia désactive les command blocks, les fonctions de datapacks et une vingtaine de commandes, Leafs garde tout le contenu vanilla fonctionnel.

## Ce que le mod fait et ne fait pas
Leafs ajoute le multithreading, et rien d'autre. Aucune feature de gameplay, aucun changement de comportement volontaire, aucune API superflue. Quand le comportement vanilla ne peut pas être conservé à l'identique, l'écart est documenté dans [Compromis.md](Compromis.md), avec sa raison. Rien ne dévie en dehors de cette liste.

## Lire cette documentation
1. [Methodologie.md](Methodologie.md) dicte comment penser, tester, développer.
2. [Architecture.md](Architecture.md) explique le modèle : les threads, les régions, les horloges, la fenêtre barrière.
3. [Fonctionnement.md](Fonctionnement.md) décrit chaque système en place : le tick, le réseau, les téléportations, les chunks.
4. [Compromis.md](Compromis.md) liste chaque écart avec vanilla et pourquoi il existe.
5. [Mixins.md](Mixins.md) donne la philosophie d'injection et la liste des points d'entrée dans le code vanilla.
6. [Arborescence.md](Arborescence.md) décrit les dossiers du code, un dossier égale une responsabilité.