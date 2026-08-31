# Les Compromis
Chaque écart volontaire est listé ici. Avec son explication. Un refactor/amélioration du code peut bien sûr en retirer, s'ils sont là c'est qu'on n'a pas eu le choix.

# Compromis bénéfiques.
Ces compromis sont un peu des fonctionnalités. En réalité ils sont même bénéfiques pour le jeu, moins de triche, ou plus de possibilités de gameplay indirectement. Honnêtement on évite de les supprimer.

1. Chaque région a son propre aléatoire, aucun effet visible en jeu.
2. La quantité de spawn de mobs est calculée pour chaque région et non par dimension entière.
3. Chaque région vit à son propre TPS. Un four peut être plus lent d'une région à l'autre par exemple.
4. Toutes les commandes s'exécutent sur le thread serveur, qui emprunte les régions qu'elles touchent. [Vulgarisation.md](Vulgarisation.md)

# Vrai Compromis
5. Écrire un bloc là où une autre région est en train de ticker arrive au tick suivant. Le bloc est bien posé, mais le relire tout de suite rend l'ancien. Partout ailleurs, y compris dans une dimension où personne ne se trouve, l'écriture est finie quand l'appel rend la main, comme en vanilla. Une région ne tick que là où un joueur est simulé, donc ce cas demande d'écrire chez un autre joueur pendant qu'il y est.
6. Les téléportations et les portails arrivent avec au plus 1 à 2 ticks de retard.
7. `END_SERVER_TICK` Les mods qui font leur travail "une fois par tick" via la Fabric API tournent toujours 20 fois par seconde, mais le monde autour n'a pas forcément avancé d'un tick entre deux appels. Une région à 10 TPS a fait un tick sur deux. Un mod qui suppose que tout le monde a tické exactement une fois depuis son dernier appel peut se tromper.
8. Un `command block` / `minecart à command block` déclenché par la redstone s'exécute un tick plus tard qu'en vanilla. La redstone tourne sur la région et la commande sur le thread serveur, et le passage de l'un à l'autre attend le tick suivant. Une commande tapée dans le chat ou lancée par un datapack n'a pas ce retard.
