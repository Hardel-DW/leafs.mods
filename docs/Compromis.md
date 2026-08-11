# Compromis

Chaque écart volontaire avec le comportement vanilla est listé ici, avec sa raison et son effet visible. Rien ne dévie en dehors de cette liste. Un compromis se retire quand un chantier le rend inutile, il ne s'aggrave jamais en silence.

1. Chaque région a son propre générateur aléatoire. La suite exacte des tirages diffère donc d'un serveur vanilla, c'est inévitable dès qu'on paie plusieurs threads. Aucun effet visible en jeu.
2. Les plafonds de spawn de mobs sont calculés par région et non par dimension entière, comme chez Folia. Une dimension très peuplée répartit ses mobs un peu différemment.
3. Aux frontières de régions, Leafs préfère laisser tomber plutôt que crasher. Une mise à jour redstone qui sort de la zone atteignable est ignorée, un projectile gèle à la frontière, et ces situations se résolvent seules quand les régions fusionnent.
4. Les actions qui traversent les régions ou les dimensions arrivent avec au plus un tick de retard, 50 ms. Les téléportations hors région, les traversées de portails et les respawns sont dans ce cas. Folia paie le même tick par ses files, et en pratique ce retard est invisible.
5. Une région ne charge jamais un chunk de force. Le code qui tombe sur un chunk absent saute son passage ce tick, un ticket court fait charger le chunk par la phase sérielle, et le réessai suivant le trouve.
6. Quand un joueur passe le relais entre sa région et le filet global, une fenêtre rare de 50 ms peut compter un de ses ticks en double, une dérive d'un tick sur un compteur comme la faim. Leafs accepte cet écart pour ne maintenir aucune liste d'appartenance.
7. Un joueur qui se déconnecte pendant qu'il écrit un livre ou une pancarte abandonne le texte et son exécution, la même forme que Folia.
8. Les commandes tapées dans le chat s'exécutent sur la phase globale du serveur, pas sur le thread du joueur, parce que la phase globale est le seul endroit où une commande peut charger des chunks arbitraires.
9. Le tick d'une entité nouvellement créée se termine sur la phase sérielle du niveau. Sous forte charge, un item droppé apparaît au tick sériel suivant.
10. Un arrêt brutal de la machine, un kill ou une coupure de courant, peut perdre les dernières écritures de fichiers joueurs encore en attente sur le thread d'écriture, que vanilla aurait déjà posées sur le disque. Un arrêt normal attend toutes les écritures.
