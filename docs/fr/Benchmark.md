# Benchmark
Les benchmarks ont lieu sur un repo privé sur un Ryzen 5900X, 12 cœurs / 24 threads et 32 Go de RAM. Le serveur tourne avec Leafs, Maple, Prune et ScalableLux/Firefly, en ZGC.
Les scénarios sont `worldgen`, `roam`, `isolation`, `solo`, `join`, `generate`, `ramp` et `hubs`.
Un serveur vierge démarre sur un monde vierge avec une `seed` identique, Leafs utilise le mod Overstress qui simule des joueurs aussi authentiques que de vrais joueurs, avec des scénarios. Les bots font donc les mêmes actions dans le même ordre, sans aléatoire dans le jeu.

## Leafs vise
- Un joueur seul dans sa région tient 20 TPS, quelle que soit la génération du monde autour de lui.
- Une régions A ne doit pas affecter les mspt d'une région B.
- Le chargement/déchargement de la `worldgen`, ainsi que la connexion/déconnexion ne doivent pas impacter le TPS d'une région.
- Le thread serveur doit avoir un coût fixe, sous 0.5 ms, sans dépendre du nombre de chunks, d'entités ou de joueurs ou de région.
- Le sériel par dimension reste sous 0,5 ms.
- La génération des chunks donc le nombre de chunks/sec doit suivre linéairement le nombre de threads.
- Le nombre de joueurs et de régions doit monter linéairement avec les threads et la RAM.
- La mémoire doit être stable/constante, sans fuite de mémoire.
- Aucun thread bloqué ne doit être détecté.
- Le processeur ne doit pas dépasser 70 % selon le benchmark établi.
- La lecture/écriture des chunks de mods tiers, commandes, datapacks et les machines redstone doivent fonctionner.
- Une exception dans le tick d'une région arrête le serveur avec le crash report de cette région.

## Le scénario worldgen
5 bots volent à 36 blocs/s avec 10 de `distance d'affichage` pendant 80 secondes. Le serveur a 12 threads de région, 12 threads de chunk et un tas de 12 Go.

| mesure | cible | run de référence |
|---|---|---|
| TPS minimal | 19,9 ou 20 | 20,0 |
| Mspt du thread serveur | sous 0.5 | 0,1 |
| Chunks/s complets | linéaire avec les workers de chunk | 387 |
| Vitesse des bots | 36,0 | 36,0 |
| Vue générée | 100 % | 100 % |
| Échantillons à vue incomplète | 0 % | 0 % |
| Cœurs consommés | sous 13 | 11,2 |
| Mémoire vivante en fin de run | Stable < 0.7 Go | 0,76 Go |
| Holders en attente de démontage en fin de run | 0 | 0 |
| Pire tick d'une étape de région | sous 50 ms | entities 59 ms |

## Les autres scénarios
Ces scénarios tournent avec la même configuration que `worldgen`.
- `roam` fait voler 40 bots en élytre avec 32 de `distance d'affichage`. La demande dépasse ce que la machine génère. Les 40 bots sont proches, donc ils forment une seule région de 14 000 chunks.
- `isolation` reprend `roam` et ajoute un bot seul à 20 000 blocs, dans sa propre région.
- `solo` fait voler un seul bot avec 32 de `distance d'affichage`.
- `join` fait rejoindre un bot immobile sur une sauvegarde déjà générée, avec 32 de `distance d'affichage`. Cela mesure que les premiéres secondes. Au `join`, toute la vue du bot est prête 1,6 s après son arrivée. Le tps se stabilises aprés 2 seconde environ a 20.

| scénario | TPS minimal | Mspt du thread serveur | Pire tick d'une région |
|---|---|---|---|
| `roam` | 19,2 | 0,2 | 85 ms |
| `isolation (le bot seul)` | 20,0 | 0,2 | 25 ms |
| `solo` | 20,0 | 0,1 | 27 ms |
| `join` | 13,8 | 0,3 | 102 ms |

## Le scénario ramp
Un bot immobile entre chaque seconde, chacun dans sa propre région, avec 5 de `distance de simulation`. Le serveur a 16 threads de région, 7 threads de chunk, un tas de 20 Go, et cinq gamerules de Prune.
Cela calcule le nombre de joueurs que la machine tient à 20 TPS. C'est la limite matérielle. La RAM limite ce scénario, pas le processeur.

| mesure | run de référence |
|---|---|
| Capacité | 600 joueurs, 600 régions |
| Chunks/s complets | 129 |
| Cœurs consommés | 11,5 |
| Mspt du thread serveur | 6,8 ms |

## Le scénario hubs
1 000 bots immobiles entrent en 50 groupes de 20, avec 8 de `distance d'affichage` et 4 de `distance de simulation`. Chaque groupe forme une région. Le serveur a la même configuration que `ramp`.

| mesure | run de référence |
|---|---|
| Capacité | 1 000 joueurs, 50 régions |
| Mspt du thread serveur | 1,0 ms |
| Cœurs consommés | 4,6 |
| Mémoire vivante en fin de run | 5,0 Go |
| Pire tick d'une région, autosave compris | 63 ms, dont 59 ms d'autosave |

## Comparer à C2ME
Leafs possède des valeurs relativement identiques sur 12 threads, `329` annoncés par C2ME mais en interne on a obtenu `366`. Pour Leafs il obtient `387`. Soit légèrement supérieur de quelques chunks par seconde, le gain s'explique par du bruit de mesure.
C2ME et Leafs sont tous deux basés sur ScalableLux, et l'approche reste relativement similaire.
Comme C2ME les gains scalent linéairement avec le nombre de threads.
Lithium/VMP/Chunky n'ont pas été utilisés pour les benchmarks.
