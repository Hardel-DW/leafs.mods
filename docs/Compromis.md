# Compromis

Chaque écart volontaire avec le comportement vanilla est listé ici, avec sa raison et son effet visible. Rien ne dévie en dehors de cette liste. Un compromis se retire quand un chantier le rend inutile, il ne s'aggrave jamais en silence.

## Déterminisme et frontières
1. Chaque région a son propre générateur aléatoire. La suite exacte des tirages diffère donc d'un serveur vanilla, c'est inévitable dès qu'on paie plusieurs threads. Aucun effet visible en jeu.
2. Les plafonds de spawn de mobs sont calculés par région et non par dimension entière, comme chez Folia. Une dimension très peuplée répartit ses mobs un peu différemment.
3. Aux frontières de régions, la politique est de laisser tomber plutôt que de crasher : une mise à jour redstone qui sortirait de la zone atteignable est ignorée, un projectile gèle à la frontière en attendant la fusion des régions. Ces situations se résolvent seules quand les régions fusionnent.

## Les différés d'un tick
4. Les actions qui traversent les régions ou les dimensions arrivent avec au plus un tick de retard, 50 ms : téléportations hors région, traversées de portails, respawns, certaines mises à jour de tracking. Folia paie le même tick par ses files. En pratique c'est invisible.
5. La traversée d'un portail vers une zone jamais générée prend le temps de générer la destination, comme vanilla, mais ce temps s'ajoute au différé ci-dessus.

## Les refus de lecture
6. Une région ne charge jamais un chunk de force, et la phase sérielle applique la même règle à ses spawners custom. Quand du code vanilla atteint un chunk absent de la carte visible, le lecteur pose un ticket de chargement court et le garde `ownership/TickGuard` transforme le refus en dégradation locale : l'entité concernée saute son tick, la passe de spawn du chunk concerné saute ce tick, un spawner custom, chats, patrouilles, phantoms, marchand ambulant, saute son passage, une recherche de structure répond non trouvé comme vanilla sait déjà le faire. Le ticket fait charger le chunk par la phase sérielle, et le réessai suivant le trouve. Effets visibles possibles : un villageois qui met une seconde de plus à trouver son lit près d'un chunk en cours de chargement, un œil d'ender qui demande un tick de plus très loin de toute zone explorée.

## Le réseau
7. Le passage de relais d'un joueur entre une région et le filet global peut, dans une fenêtre rare de reprise simultanée, produire un double tick de listener ou un double calcul de diff de vue dans les 50 ms concernées. L'effet est une dérive d'un tick sur des compteurs comme la faim, ou un paquet de vue redondant. Accepté contre l'absence totale de liste d'appartenance à maintenir.
8. Une continuation de filtrage de texte, livre ou pancarte, dont le joueur se déconnecte avant l'exécution est abandonnée. Même forme que Folia.
9. Les commandes tapées dans le chat s'exécutent sur la phase globale du serveur, pas sur le thread du joueur. C'est le comportement le plus proche de vanilla et la seule place où une commande peut charger des chunks arbitraires.
10. Les logins restent sérialisés sur le thread global. Un afflux massif de connexions simultanées se met en file plutôt que de se paralléliser.

## Divers
11. En solo, la pause du serveur intégré draine les paquets mais ne tique rien d'autre, parité vanilla exacte.
12. Le tick des entités nouvellement créées se termine sur la phase sérielle du niveau, un item droppé apparaît donc au tick sériel suivant sous forte charge.
13. Les caches de Lithium incompatibles avec des régions concurrentes sont désactivés par options, quatre règles et leurs dépendances. Le détail et les preuves sont dans l'historique de la roadmap.
14. Les fichiers d'un joueur, playerdata, stats et advancements, s'écrivent sur un thread d'écriture dédié au lieu du thread serveur, dans l'ordre des sauvegardes. Une reconnexion immédiate relit l'écriture en attente, jamais un fichier périmé, et l'extinction attend la fin de toutes les écritures. Le seul écart : un arrêt brutal de la machine, kill ou coupure de courant, peut perdre les toutes dernières écritures en attente, que vanilla aurait déjà posées sur le disque. En échange, une déconnexion n'impose plus trois écritures disque au serveur entier pendant la pause des régions.
