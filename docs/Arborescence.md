# Arborescence

Un dossier égale une responsabilité. Le code vit dans `src/main/java/fr/hardel/leafs/`.

| Dossier | Responsabilité |
|---|---|
| `config/` | La configuration du mod, parsing strict, clés inconnues refusées. |
| `ownership/` | Qui possède quoi : le contexte de thread courant, l'exception de violation, le garde de dégradation `TickGuard`, le rapport de crash par région. |
| `region/` | Le découpage du monde : sections, régions, fusion, scission, le `Regionizer`. Pur, sans dépendance Minecraft. |
| `ticking/` | L'orchestration : le scheduler des workers, les unités de tick par niveau, le verrou par niveau, la barrière, le watchdog, les timings. |
| `world/` | Le corps du tick de région et les états de monde par région : ticks programmés, évènements de blocs, horloge, aléatoire, block entities, mises à jour de voisinage. |
| `entity/` | Les entités : listes de tick par région, index concurrents, téléportations et pipeline inter dimensions, schedulers d'entités, primitives concurrentes maison. |
| `chunk/` | Le système de chunks : la table concurrente des holders, l'ordonnancement shardé et le pool de génération dans `core/`, le chargeur par joueur dans `loader/`, le propagateur de niveaux dans `propagator/`, plus les lectures des régions, les tickets, le tracking et le verrou des villages. |
| `network/` | Le réseau : files de paquets par joueur, routage, le tick réseau par région, le filet global. |
| `scheduler/` | Les schedulers publics, par région et global, avec les tickets de rétention de chunks, et le moteur du travail différé `DeferredWork` avec ses trois destinations. |
| `global/` | La phase globale : la fenêtre barrière, le moniteur d'état partagé, les command blocks, les gamerules du mod, les écritures différées des fichiers de joueurs, la pause des évènements de tick de la Fabric API. |
| `metrics/` | La mesure : les durées par étape de tick (`StageTimings` et les trois enums d'étapes), les compteurs à fenêtre d'une minute, les stats de la fenêtre barrière, les compteurs de reports et de refus du contrat des chunks. Importable par tout le monde, ne dépend que de `ownership/`. |
| `debug/` | Les commandes `/leafs regions`, `/leafs timings`, `/leafs metrics` et `/leafs recommendation`. |
| `mixin/` | Les points d'accroche, un sous dossier par module servi, plus `compat/` pour ceux qui ciblent la Fabric API au lieu de vanilla. Aucune logique. |

Les règles de dépendance entre modules : `ownership/`, `config/` et `metrics/` sont importables par tout le monde. `region/` ne dépend de rien d'autre. `entity/` n'importe jamais `ticking/`, les surfaces passent par des interfaces construites au câblage. Rien ne dépend de `mixin/` ni de `debug/`.
Les tests vivent dans `src/test/java` en miroir des modules. Les ressources dans `src/main/resources` : le manifest du mod avec les options Lithium, la liste des mixins, l'access widener.
