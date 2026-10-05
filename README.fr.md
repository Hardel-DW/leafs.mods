# Leafs - Multi thread pour Fabric/NeoForge
Leafs est un mod côté serveur qui fonctionne aussi en solo pour Fabric/NeoForge. Il découpe le monde en régions indépendantes et fait tourner chaque région sur un thread, avec sont propres TPS.
Sur un serveur vanilla, un seul thread fait tout, le jeu fonctionne de manière à ce que n'importe quel joueur impacte tous les autres, et on atteint donc vite la limite de joueurs.

Leafs ajoute le multithreading et rien d'autre. Aucune feature de gameplay, aucune API, aucune optimisation cachée. Les mods, datapacks et command blocks fonctionnent comme en vanilla.
Tout les informations sont sur le site avec des simulations interactives [leafs.hardel.io](https://leafs.hardel.io).

> Leaf néccéssite Fabric API, ainsi que ScalableLux ou Firefly.

## Fonctionnalités :
- Chaque régions a sont 20 TPS. Une giga ferme ne ralentit que les joueurs de cette région.
- La `worldgen` n'impacte pas le TPS des régions. 
- La `worldgen` est multithread et scale avec vos cœurs.
- Fonctionne avec vos mods de contenu, datapacks préférés comme AE2.
- Côté serveur uniquement. Les joueurs n'ont aucun mod à installer sur leur client.
- Disponible pour Fabric et NeoForge, à partir de Minecraft 26.1.

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Pourquoi ça scale
Leafs a été pensé pour que le gain soit infini et linéaire, plus vous allouez de cœurs et de RAM, plus vous avez de joueurs.

Il a été pensé en tenant compte des lois d'Amdahl et de Gustafson. Leafs garde la partie thread commun déterministe et minime < 1ms, et met le reste dans les threads régions et de chunks. Peu importe le nombre de joueurs ou de régions ou de la génération du monde, le thread commun sera constant.

Mesuré sur un Ryzen 5900X - 12 cœurs - 20 Go RAM sur un monde pas généré sur la seed `0` sans aléatoire, pour obtenir des chiffres stables entre deux bench, Leafs tient **308 joueurs dont chacun a une région unique à 20 TPS**. Les gains sont multiplicatifs dans les cas suivants :
- Quand plusieurs joueurs sont dans la même région.
- Quand le monde est pré-généré.
- Quand le monde a peu de bloc/entité par exemple un skyblock.

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Comment fonctionnent les régions
Les chunks simulés autour d'un joueur forment une région. Deux joueurs qui se rapprochent voient leurs régions fusionner en une seule. Une région qui s'étire jusqu'à se couper en deux devient deux régions.
Cela ne s'applique pas qu'aux joueurs mais à n'importe quel élément de gameplay qui crée des chunks simulés : **chunk loader**, la commande **/forceload**, les **enderpearl**...

Chaque région possède ses chunks, ses joueurs et son propre générateur aléatoire.
Autour de chaque région, une couronne de chunks absorbe ce qui déborde : un piston, un projectile...

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Compatibilité des mods
Les mods ne s'adaptent pas à Leafs. C'est Leafs qui s'adapte aux mods, Leafs ne touche que les méthodes de base de Minecraft : lire un bloc, charger un chunk, téléporter une entité.
Les mods utilisent ces méthodes sans le savoir, donc ils fonctionnent. Si un mod casse avec Leafs, c'est un bug de Leafs : ouvrez un ticket.

Plus d'informations sur [leafs.hardel.io/docs/mod-compatibility](https://leafs.hardel.io/docs/mod-compatibility).

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# L'écosystémes.
Ces trois mods font partie de l'écosystems de Leaf, car leaf n'optimise ni le CPU/RAM ou le GC, seulement l'architecture multithread et l'architecture régions.
- **Maple** - Est penser pour les optimisations qui scalent sans config qui n'ont aucunes conséquence sur le jeu et les joueurs.
- **Firefly** - Un mods qui multithread la lumiéres. En expérimentation.
- **Prune** - Contrairement a Maple il fait des optimisations majeur qui modifie le jeu et le gameplay, similaire aux plugins loader chaque option est une gamerule.
- **Overstress** - Un mods qui simule le cout de vrai joueurs avec des scénario `pvp/elytra/minage/déplacement/dimension`. pour les bench/test en jeu.
- **Leaf - Debug and Metrics** - Un mods cotés client qui affiche une carte des régions des options F3 avancés pour debug et mesurer Leaf.

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Bon à savoir
- **Fichier de config :** `config/leafs.json`, créé au premier démarrage. Les valeurs par défaut conviennent à la plupart des serveurs. Par défaut Leafs utilise tous vos cœurs pour les régions et la moitié pour la génération des chunks.
- **server.properties :** au premier démarrage Leafs met `sync-chunk-writes` à `false`.
- **Gros serveurs :** la locator bar devient illisible avec beaucoup de joueurs. Désactivez-la avec `/gamerule locator_bar false`, Leafs coupe alors tous ses calculs.
- **La commande `/leafs` :** pour les opérateurs.
- **Datapacks :** les datapacks sont compatibles naturellement.
- **Mcfunction :** les mcfunctions fonctionnent mais elles profitent partiellement du multithread. Le `tick.json` est à éviter malgré un support de Leafs. [Leafs - Commande et Datapack](https://leafs.hardel.io/docs/commands-datapacks/)

# Soutenir le projet
Rejoignez-nous sur Patreon et aidez à rendre Minecraft encore plus incroyable pour tout le monde !

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

[![Patreon](https://images.ctfassets.net/4rnblstkg79m/5TumqlgINUoJBVWFRRU6wz/e64dfea04fe5b19e60fa27310114853f/WolffOlins_Patreon.jpg)](https://www.patreon.com/hardel)
