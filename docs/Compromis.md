# Les Compromis
Chaque écart volontaire est listé ici. Avec sont explications. Un refactor/ammélioration du code peut biensur en retirer, s'ils ils sont là c'est qu'ont a pas eu le choix.

# Compromis bénéfiques.
Ces compromis sont un peu des fonctionnalités. En réalités ils sont même bénéfiques pour le jeu, moins de triches, ou plus de possibilités de gameplays indirectement. Honnétement ont évite des les supprimers.

1. Chaque région a son propre aléatoire, Aucun effet visible en jeu.
2. La quantité de spawn de mobs sont calculés pour chaque région et non par dimension entière.

# Vrai Compromis
3. Les actions qui traversent les régions comme les téléportations ou les portails arrivent avec au plus un tick de retard comme la téléportation.
4. Un joueur qui se déconnecte pendant qu'il écrit un livre ou une pancarte perd le texte.
5. Les commandes tapées dans le chat s'exécutent sur le thread globale, pas sur la région du joueur, parce que la phase globale est le seul endroit où une commande peut charger des chunks arbitraires.
6. Si la machine s'éteint brutalement, peut perdre les dernières écritures de fichiers joueurs encore en attente sur le thread d'écriture. Sans corruptions.
7. Deux joueurs qui localise une structure au même moment peuvent recevoir la même.
8. Une déconnexion met toutes les régions en pause quelques millisecondes le temps de retirer le joueur.