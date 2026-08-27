# Les Compromis
Chaque écart volontaire est listé ici. Avec sont explications. Un refactor/ammélioration du code peut biensur en retirer, s'ils ils sont là c'est qu'ont a pas eu le choix.

# Compromis bénéfiques.
Ces compromis sont un peu des fonctionnalités. En réalités ils sont même bénéfiques pour le jeu, moins de triches, ou plus de possibilités de gameplays indirectement. Honnétement ont évite des les supprimers.

1. Chaque région a son propre aléatoire, Aucun effet visible en jeu.
2. La quantité de spawn de mobs sont calculés pour chaque région et non par dimension entière.
3. Chaque régions vie a sont propre TPS. Un four peut être plus lent d'une régions a l'autre par exemple.
4. Toutes les commandes s'exécutent sur le thread serveur, qui emprunte les régions qu'elles touchent. [Architecture.md](Architecture.md)

# Vrai Compromis
5. Les actions qui traversent les régions comme les téléportations ou les portails arrivent avec au plus 2 a 3 tick de retard comme la téléportation.
6. Un joueur qui se déconnecte pendant qu'il écrit un livre ou une pancarte perd le texte.
7. Si la machine s'éteint brutalement, peut perdre les dernières écritures de fichiers joueurs encore en attente sur le thread d'écriture. Sans corruptions.
8. Deux joueurs qui localise une structure au même moment peuvent recevoir la même.
9. `END_SERVER_TICK` Les mods qui font leur travail "une fois par tick" via la Fabric API tournent toujours 20 fois par seconde, mais le monde autour n'a pas forcément avancé d'un tick entre deux appels. Une région à 10 TPS a fait un tick sur deux. Un mod qui suppose que tout le monde a tické exactement une fois depuis son dernier appel peut se tromper.