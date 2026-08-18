# Audit du thread global

Cet audit inventorie tout ce que le thread serveur exécute à chaque tick, avec le coût de chaque item. L'objectif du projet est que ce coût soit déterministe. On doit pouvoir le calculer à l'avance, et il ne doit dépendre ni du nombre de joueurs, ni du nombre de chunks, ni du nombre d'entités. Deux audits indépendants ont vérifié chaque affirmation dans le code, ligne par ligne. Cet état est celui du 18 août 2026.

Variables utilisées. P joueurs, C chunks chargés, R régions d'un niveau, L dimensions, E entités, T positions portant un ticket, S schedulers d'entité, K connexions, D chunks décidés à décharger, Q taille d'une file, ΔC churn de chunks du tick, τ_max durée du plus long tick de région en vol.

## Les deux couches du thread serveur

Le thread serveur exécute deux couches, et les deux comptent dans la fraction sérielle d'Amdahl.

1. La phase globale. Le corps restant de `MinecraftServer.tickServer` et `tickChildren`, le drain du `GlobalScheduler` et la fenêtre barrière.
2. La phase sérielle de chaque niveau. `LevelTickUnit.tick` s'exécute sur le thread appelant via `RegionTickScheduler.runAttached` (RegionTickScheduler.java:75). Les L dimensions passent en série dans la boucle vanilla.

## Ce qui est borné et déterministe

Ces items forment le socle O(1) que le projet vise. Ils ne posent aucun problème.

| Item | Coût | Où |
|---|---|---|
| Bordure du monde, météo, luminosité du ciel, temps | O(1) par dimension | ServerLevel.java:361-384 |
| Horloges de datapack, respawn effectif | O(1) | MinecraftServer.java:1109, 1120 |
| Métriques StageTimings, watchdog, compteurs minute | O(1), rien n'itère le monde | metrics/StageTimings.java:21 |
| Commandes /leafs | O(L + R), le compte d'entités se somme depuis les tailles de listes | debug/ |
| Placement d'un joueur qui se connecte | O(1) sur le thread global, le corps part sur la région du spawn | mixin/network/PlayerListMixin.java:89 |
| playerList.tick, buildServerStatus, timeSync | O(P) amorti sur 600, 100 et 20 ticks | MinecraftServer.java |
| Ticks programmés, block events, entités et block entities attachés | O(résidu), proche de zéro dès que les régions couvrent les chunks actifs | world/RoutingScheduledTicks.java |
| Purge résiduelle des tickets à timeout | O(sections orphelines de l'index), quasi nul. Chaque région purge ses propres sections pendant son tick | chunk/TicketTimeoutIndex.java |

## Ce qui casse le déterminisme

Chaque item ci-dessous fait dépendre le coût du thread global de la charge. Tous sont confirmés par les deux audits, dans le même ordre de sévérité.

### 1. Le pipeline d'entités

`entityManager.tick()` (ServerLevel.java:456) draine toute la boîte de réception des chunks d'entités fraîchement chargés et ajoute chaque entité au monde sur le thread sériel, sans budget (PersistentEntitySectionManager.java:242). Le coût suit le débit de chargement, donc la vitesse de déplacement des joueurs. Un joueur en élytres le fait exploser.

Les chunks sont régionalisés en profondeur, table concurrente, propagateur shardé, démontage et autosave offerts au propriétaire. Les entités entrent et sortent du monde entièrement en sériel. Cette asymétrie est le prochain gain structurel. L'ajout d'une entité au monde appartient à la région de sa position, comme le pas FULL des chunks.

### 2. L'autosave

Toutes les 300 secondes environ, l'autosave fait trois choses sur le thread global. P prises d'exclusion pour les sauvegardes de joueurs (PlayerListMixin.java:118). Un balayage de tous les holders visibles, O(L × C), chaque chunk étant offert à sa région (ChunkMapMixin.java:335). Et `entityManager.autoSave` (ServerLevel.java:891) qui sérialise les entités de tous les chunks à sauver sur le thread global, sous exclusion, sans offre à la région.

Le résultat est un pic de latence proportionnel au monde, concentré sur un tick. Le balayage peut s'étaler, et la sauvegarde d'entités peut suivre le même chemin que celle des chunks.

### 3. Le drain global sans budget de temps

`GlobalScheduler.drain` (GlobalScheduler.java:15) borne le nombre de tâches par un snapshot de la file, mais pas leur durée. Les commandes tapées en chat y atterrissent (ServerGamePacketListenerImplMixin.java:53), et une commande comme /fill ou /locate s'exécute intégralement ici. Un seul joueur peut faire dépasser le thread global à volonté, sans aucun garde fou.

Le routage des commandes vers le global est un choix assumé, une commande doit pouvoir charger des chunks arbitraires. Un budget de temps avec report au tick suivant serait un filet, au prix d'une latence de commande.

## Les seconds couteaux

| Item | Coût | Détail | Fix |
|---|---|---|---|
| Budget de la file sérielle | 10 ms par dimension et par tick (LevelTickUnit.java:44) | Le pire cas légal à 3 dimensions est 30 ms sur un tick de 50, sans alarme | Budget global partagé entre dimensions |
| Drain de la pompe dans la quiesce | O(Q_pompe + R) par dimension (TickingManager.java:149) | Le balayage des régions ne se fait que pompe vide, il reste le drain de la pompe sans budget de temps, qui suit le churn de chunks | Budget de temps avec report |
| Décisions de déchargement | O(D) sans budget (ChunkMapMixin.java:239), plus l'échappatoire vanilla qui draine de force au-delà de 2000 chunks en file | Une vague de déchargement perce le budget | Budgéter la boucle de décisions |
| Trackers de distance vanilla | O(Δ) ≈ O(P × vue²) en pointe (DistanceManager.java:69) | Seul le propagateur de chargement est shardé, la simulation, le spawn naturel et les tickets joueur restent sériels | Porter sur le modèle shardé |
| Schedulers d'entité | O(S × L) (EntitySchedulerRegistry.java:37) | La map serveur entière est rebalayée une fois par dimension | Indexer par dimension, ou régionaliser |
| Quatre balayages O(P) par tick | suspendFlushing et resumeFlushing, le filet loader, le filet tracking, le test de sommeil | La doc revendique l'absence de liste d'orphelins, le prix est ces balayages. Anecdotique à 100 joueurs, plus à 500 | Une liste d'orphelins entretenue par estampille les supprime tous |
| Envoi de chunks aux orphelins | Une prise d'exclusion par joueur orphelin (mixin/network/MinecraftServerMixin.java:23) | N joueurs dans le générique de fin font N attentes séquentielles de τ_max | Grouper les orphelins d'un niveau sous une seule exclusion |
| Fenêtre barrière | Sur ouverture: τ_max d'attente puis toutes les régions parquées pendant le drain (BarrierWindow.java:41) | Sans fonction `#tick` ni command block en repeat, elle ne s'ouvre que sur évènement: respawn, portail, commande console, /schedule. Avec ce contenu et les gamerules par défaut, elle s'ouvre chaque tick et le serveur redevient mono thread | Les deux gamerules coupent le contenu fautif; réduire les raisons qui atterrissent en fenêtre |
| Graphe de distance des villages | O(Δ_POI) sériel sous le verrou POI global (PoiManager.java:240) | Explicitement non concurrent | Aucun fix connu |
| Attente de l'exclusion | Prise deux fois par tick et par dimension, chaque prise attend au pire τ_max | Le temps mural du thread global dépend de la région la plus lourde | Faire céder les régions à un point de sûreté |
| Évènements de tick Fabric | Deux levées de barrière par tick si un mod s'abonne | Même modèle de coût que la fenêtre | Aucun, c'est le contrat rendu aux mods |
| Tickables et code de mods | Arbitraire (MinecraftServer.java:1151) | Un mod qui enregistre un tickable coûteux le paie sur le global | Aucun, code tiers |
| Paquets de login | O(Q_login) par itération | Pic sur vague de connexions, le reste du login est déjà bien traité | Budget de N logins par tick |

## La formule du tick global

```
T_global ≈ O(1)                                    socle constant
  + 4 × O(P)                                       balayages
  + O(K)                                           transport des connexions
  + L × [ O(sections orphelines de l'index)        purge résiduelle des tickets, quasi nul
        + O(Q_pompe + R)                           quiesce
        + O(ΔC_load × E/chunk)                     entités, item 1
        + O(Δ_tickets)                             trackers vanilla
        + O(min(Q_serial, 10 ms))                  file sérielle, budgétée par dimension
        + O(S) + O(D) + O(Δ_POI) ]
  + O(Q_global × coût_commande)                    drain global, item 3
  + [fenêtre non vide] × (τ_max + O(Q_window))     barrière, sur évènement
  + [tick d'autosave] × O(P + L×C + E)             autosave, item 2
```

Le "L × [...]" se lit comme une somme sur les dimensions, chacune avec ses propres valeurs. Une dimension vide coûte quasi rien, et c'est presque toujours l'overworld qui domine chaque terme.

## Ordre d'attaque

1. Les correctifs moyens. Budget global de la file sérielle, budget des décisions de déchargement, liste d'orphelins, groupage des exclusions d'envoi.
2. Régionaliser le pipeline d'entités, chargement et autosave. C'est le chantier structurel suivant.
