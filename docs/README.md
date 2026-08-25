# Leafs
Dans un serveur classique, tout les couts sont partager. Leafs découpe le monde en régions indépendantes, et chaque région vit son propre tick à 20 TPS sur un threads. Plus il y a de zones actives éloignées, plus le serveur utilise de cœurs. Leafs ajoute le multithreading, et rien d'autre. Aucune feature de gameplay, aucune API. Les optimisations RAM/CPU sont fait dans un mods séparer Mapple qui fonctionne sans ou avec Leafs.

## Lire cette documentation
1. [Methodologie.md](Methodologie.md) dicte comment penser, tester, développer.
2. [Architecture.md](Architecture.md) explique le modèle : les threads, les régions, les horloges, la fenêtre barrière.
3. [Compromis.md](Compromis.md) liste chaque écart avec vanilla et pourquoi il existe.
4. [Arborescence.md](Arborescence.md) décrit les dossiers du code, un dossier égale une responsabilité.