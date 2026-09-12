# Q&A
Quelques questions et réponses courantes.

## Les machines en redstone fonctionnent-elles près des bords de la région ?
Si une machine dépasse de la zone simulée, elle se fige comme en vanilla. Rien de nouveau.
Chaque région a aussi une couronne qui ne tick pas, une zone autour de la région qui permet de gérer les débordements comme les pistons, les projectiles et le reste.

# Les concepts niche comme les canons orbitaux fonctionnent-ils ?
Le code ne fait rien de spécial pour une entité rapide, et n'en a pas besoin.
Aucune entité n'est envoyée entre les threads. Une région ne "possède" pas ses entités. Au début de chaque tick elle prend une photo des entités de ses chunks et tick celles-là. Une TNT qui traverse la frontière change simplement de section de chunk, comme en vanilla, et au tick suivant l'autre région la voit dans sa photo. Cent ou mille TNT coûtent la même chose qu'en vanilla.

# Comment sont gérées les entités qui traversent des régions ?
- Pour les téléportations et portails, la région d'origine fait le travail, puis envoie un courrier à la région cible qui place l'entité.
- Pour les entités qui sortent de la région, la réponse est similaire aux canons orbitaux, à chaque début de tick elle prend une photo instantanée de l'état du jeu, si elle devait arriver dans une autre région, celle-ci prendrait à ce moment-là une photo elle aussi sur le tick d'après. Simple.
- Identiques, ils gèlent en sortant de la zone simulée, comme dans le jeu d'origine. L'ender pearl est une exception du jeu d'origine, elle agrandit la région ou peut en créer une comme un joueur.

> Note importante, les régions sont toujours séparées par une section non simulée au-delà de la couronne, donc les entités qui sortent de la région seront gelées dans cette zone, comme dans le jeu d'origine au-delà de la simulation distance. Si les joueurs sont suffisamment près les uns des autres, ils fusionnent leurs régions.

# Pour les chunk loaders/forceload, fonctionneront-ils ?
Ça fonctionne, et c'est le point le plus propre du modèle. `Forceload` ou les `chunk loaders` font charger des zones via simulation. Le modèle est basé sur les zones simulées donc cela crée une région ou étend sa région s'il en existe une.

# Les datapacks/commandes fonctionnent-ils ?
Toutes les commandes tournent sur le thread serveur, peu importe qui les lance. Il emprunte une région au moment où la commande touche un de ses chunks ou une de ses entités, la garde jusqu'à la fin de la commande, puis la rend. Une commande coûte exactement son coût vanilla.
Un datapack lourd utilisant `tick.json` reste sur un seul thread, il ne profite pas du multithreading. Il ralentit le thread serveur et les régions qu'il emprunte pendant ses commandes, pas les autres.

# Y a-t-il des failles que des tricheurs pourraient exploiter comme détecter la fusion/scission de régions ?
Par nature oui, si vous changez d'une zone à 20 TPS à une zone à 15 TPS vous n'avez pas besoin de concevoir un mod pour savoir que quelque chose a changé et donc que quelque chose ici est chargé. Un joueur, une `ender pearl`, un `forceload`, un `chunk loader` ou autre.
Le mob cap est par sois dimensions par défaut, ou par région selon la config, par exemple deux usines dans deux régions proches tournent avec un mob cap complet. Cependant certaines techniques obscures basées sur l'analyse de l'aléatoire deviennent plus complexes à utiliser comme chaque région a sa propre graine d'aléatoire.

# Des recommendations pour un gros serveur ?
- La `locator bar` sur beaucoup de joueurs devient illisible, il est préférable de la désactiver. `/gamerule locator_bar false`. De plus leafs a une optimisations qui désactives tout les calcules proprement de la `locator bar` quand elle est désactiver.