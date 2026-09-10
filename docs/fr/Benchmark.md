# Benchmark
Un serveur vierge démarre sur un monde vierge avec une `seed` identique, Leafs utilise le mod Overstress qui simule des joueurs aussi authentiques que de vrais joueurs, avec des scénarios. Les bots font donc les mêmes actions dans le même ordre, sans aléatoire dans le jeu.
Ce benchmark s'opère dans un `repository` privé avec 12 cœurs / 24 threads sur un Ryzen 5900X, avec 12 threads de chunk et 12 threads de région.
Plusieurs scénarios existent, seulement quelques-uns sont mentionnés ici, `worldgen` et `ramp`.

## Leafs vise
- Un joueur seul dans sa région tient 20 TPS, quelle que soit la génération du monde autour de lui.
- Le chargement/déchargement de la `worldgen`, ainsi que la connexion/déconnexion ne doivent pas impacter le TPS d'une région.
- Le thread serveur doit avoir un coût fixe, sous 1 ms, sans dépendre du nombre de chunks, d'entités ou de joueurs.
- Le sériel par dimension reste sous 0,5 ms.
- La génération des chunks doit suivre linéairement le nombre de threads de chunk.
- Le nombre de joueurs et de régions doit scaler linéairement avec les threads et la RAM.
- La mémoire doit être stable/constante, sans fuite de mémoire.
- Aucun thread bloqué ne doit être détecté.
- Le processeur ne doit pas dépasser 70 % selon le benchmark établi.
- La lecture/écriture des chunks de mods tiers, commandes, datapacks et les machines redstone doivent fonctionner.
- Une exception dans le tick d'une région arrête le serveur avec le crash report de cette région.

## Le scénario worldgen
5 bots volent à 36 blocs/s avec 10 de `distance d'affichage` pendant 3 minutes. Ce scénario mesure la génération et son effet sur les régions.

| mesure | cible | run de référence |
|---|---|---|
| TPS minimal | 19,9 ou 20 | 20 |
| Mspt du thread serveur | sous 1 | 0,5 |
| Chunks/s complets | linéaire avec les workers de chunk | 383 |
| Vitesse des bots | 36,0 | 36,0 |
| Vue générée sur les 60 dernières secondes | 100 % | 100 % |
| Échantillons à vue incomplète | 0 % | 0 % |
| Cœurs consommés | sous 14 | 12,8 |
| Heap mémoire en fin de run | Stable < 1 Go | 0,68 Go |
| Holders en attente de démontage | 0 | 0 |
| Pire tick d'une étape de région | sous 50 ms | tasks 37 ms |

## Le scénario ramp
Un bot toutes les 3 secondes jusqu'à 300, sur 20 000 blocs, pendant quinze minutes, avec `locator_bar` désactivée. Les bots marchent dans un terrain vierge.
Cela calcule le nombre de joueurs que la machine tient à 20 TPS. C'est la limite matérielle.
Le nombre de chunks complets par seconde est plus faible dans ce scénario car les threads de régions sont davantage sollicités, moins de ressources sont donc allouées aux threads de génération du monde. Cela fonctionne comme attendu. L'expérience de jeu à 20 TPS est plus importante que la vitesse de génération du monde.

| mesure | run de référence |
|---|---|
| Capacité | 208 joueurs à 622 s |
| Chunks/s complets | 195 |
| Cœurs consommés | 13,4 |
| Mspt du thread serveur | 1,3 ms |

## Comparer à C2ME
Leafs possède des valeurs relativement identiques sur 12 threads, `329` annoncés par C2ME mais en interne ont a obtenu `366`, Pour Leafs il obtient `383`. Sois légèrement supérieurs de quelques chunks par seconde, le gains s'expliques par du bruit de mesure.
C2ME et Leafs sont tous deux basés sur ScalableLux et FastNoise, et l'approche reste relativement similaire.
Comme C2ME les gains scalent linéairement avec le nombre de threads.
Lithium/VMP/Chunky n'ont pas été utilisés pour les benchmarks.
