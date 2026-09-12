# Leafs - Multi thread on Fabric
Leafs est un mod côté serveur pour Fabric/NeoForge. Il découpe le monde en régions indépendantes et fait tourner chaque région sur un thread, avec ses propres TPS.

Sur un serveur vanilla, un seul thread fait tout, de plus tout le jeu fonctionne de manière à ce que n'importe quel joueur impacte tous les autres, et on atteint donc vite la limite de joueurs.

Leafs ajoute le multithreading et rien d'autre. Aucune feature de gameplay, aucune API, aucune optimisation cachée. Vos mods, datapacks et command blocks fonctionnent comme en vanilla.

Plus d'informations sur le site. Les simulations interactives et l'explication complète sont sur [leafs.hardel.io](https://leafs.hardel.io).

## Features :
- Une ou plusieurs régions sont sur un thread. Une ferme lourde ne ralentit que ses propres joueurs.
- Un joueur seul dans sa région tient 20 TPS, quelle que soit la génération du monde autour de lui.
- La génération des chunks tourne sur son propre pool de threads et scale avec vos cœurs, une architecture différente de C2ME mais en pratique les mêmes gains qu'elle.
- Fonctionne avec vos mods de contenu, datapacks préférés comme AE2.
- Côté serveur uniquement. Les joueurs n'ont aucun mod à installer sur leur client.
- Disponible pour Fabric et NeoForge, à partir de Minecraft 26.1.

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Pourquoi ça scale
Leafs a été pensé pour que le gain soit infini et linéaire, plus vous allouez de cœurs et de RAM, plus vous avez de joueurs.

Il a été pensé en tenant compte des lois d'Amdahl et de Gustafson. Leafs garde la partie thread commun déterministe et minime < 1ms, et met le reste dans les threads régions/chunks. Peu importe le nombre de joueurs ou de régions ou la génération du monde, le thread commun sera constant.

Mesuré sur un Ryzen 5900X, 12 cœurs/20 Go RAM, Leafs tient **208 joueurs dont chacun a une région unique à 20 TPS** sur un monde non généré. Les gains sont multiplicatifs dans les cas suivants :
- Quand plusieurs joueurs sont dans la même région.
- Quand le monde est pré-généré.
- Quand le monde est un skyblock.

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Comment fonctionnent les régions
Les chunks simulés autour d'un joueur forment une région. Deux joueurs qui se rapprochent voient leurs régions fusionner en une seule. Une région qui s'étire jusqu'à se couper en deux devient deux régions.

Cela ne s'applique pas qu'aux joueurs mais à n'importe quel élément de gameplay qui crée des chunks simulés : **chunk loader**, la commande **/forceload**, les **enderpearl**...

Chaque région possède ses chunks, ses joueurs et son propre générateur aléatoire.
Autour de chaque région, une couronne de chunks absorbe ce qui déborde : un piston, un projectile...

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Compatibilité des mods
Les mods ne s'adaptent pas à Leafs. C'est Leafs qui s'adapte aux mods.
Leafs ne touche que les méthodes de base de Minecraft : lire un bloc, charger un chunk, téléporter une entité.
Les mods utilisent ces méthodes sans le savoir, donc ils fonctionnent. Si un mod casse avec Leafs, c'est un bug de Leafs : ouvrez un ticket.

Plus d'informations sur [leafs.hardel.io/docs/mod-compatibility](https://leafs.hardel.io/docs/mod-compatibility).

![Delimeter](https://cdn.modrinth.com/data/cached_images/c57c204c55df0ce5357df6501f616f2c7b7c6df1.png)

# Mapple
Leafs n'optimise ni le CPU, ni la RAM, ni le garbage collector. Ces optimisations vivent dans **[Mapple](https://modrinth.com/mod/mapple)**, un mod séparé qui fonctionne avec ou sans Leafs, sans config et sans compromis.
Mapple est pensé pour des optimisations qui scalent, pour qu'elles consomment moins avec un fort taux de joueurs, de chunks ou d'entités.

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
