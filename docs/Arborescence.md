# Arborescence

Un dossier égale une responsabilité. Le code vit dans `src/main/java/fr/hardel/leafs/`.

| Dossier | Responsabilité |
|---|---|
| `config/` | La configuration du mod, parsing strict, clés inconnues refusées. |
| `ownership/` | Qui possède quoi : le contexte de thread courant, les assertions de propriété, l'exception de violation, le garde de dégradation `TickGuard`, le rapport de crash par région. |
| `region/` | Le découpage du monde : sections, régions, fusion, scission, le `Regionizer`. Pur, sans dépendance Minecraft. |
| `ticking/` | L'orchestration : le scheduler des workers, les unités de tick par niveau, le verrou par niveau, la barrière, le watchdog, les timings. |
| `world/` | Le corps du tick de région et les états de monde par région : ticks programmés, évènements de blocs, horloge, aléatoire, block entities, mises à jour de voisinage. |
| `entity/` | Les entités : listes de tick par région, index concurrents, téléportations et pipeline inter dimensions, schedulers d'entités, primitives concurrentes maison. |
| `chunk/` | L'accès des régions aux chunks : lectures par la carte visible, tickets, tracking, verrou des villages. |
| `network/` | Le réseau : files de paquets par joueur, routage, le tick réseau par région, le filet global. |
| `scheduler/` | Les schedulers publics : par région, global, asynchrone, avec les tickets de rétention de chunks. |
| `global/` | La phase globale : la fenêtre barrière, le moniteur d'état partagé, les command blocks, les gamerules du mod. |
| `compat/` | Les adaptations d'autres mods, aujourd'hui deux shims Fabric API. |
| `debug/` | Les commandes `/leafs regions` et `/leafs recommendation`, et l'enregistreur de métriques CSV. |
| `mixin/` | Les points d'accroche, un sous dossier par module servi. Aucune logique. |

Les règles de dépendance entre modules : `ownership/` et `config/` sont importables par tout le monde. `region/` ne dépend de rien d'autre. `entity/` n'importe jamais `ticking/`, les surfaces passent par des interfaces construites au câblage. Rien ne dépend de `mixin/` ni de `debug/`. `compat/` dépend de ce qu'il adapte mais personne ne dépend de lui.
Les tests vivent dans `src/test/java` en miroir des modules. Les ressources dans `src/main/resources` : le manifest du mod avec les options Lithium, la liste des mixins, l'access widener.
