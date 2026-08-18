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
| File sérielle et décisions de déchargement | O(min(travail, 10 ms partagées entre toutes les dimensions)), plancher de progrès par voie, report au tick suivant | ticking/SerialWorkBudget.java |
| Autosave | O(1), le déclencheur incrémente une époque par dimension. Chaque région sauvegarde ses chunks, ses chunks d'entités et ses joueurs à son propre tick, une vingtaine de chunks par tick. Le global ne sauvegarde que les joueurs orphelins | world/RegionAutosave.java |
| Purge résiduelle des tickets à timeout | O(sections orphelines de l'index), quasi nul. Chaque région purge ses propres sections pendant son tick | chunk/TicketTimeoutIndex.java |
| Pipeline d'entités | O(D) de dispatch pour les décharges; l'ajout au monde, la sérialisation NBT et la désérialisation tournent sur les régions et le pool de chunks | entity/RegionEntityPersistence.java |

## Ce qui casse le déterminisme

Chaque item ci-dessous fait dépendre le coût du thread global de la charge. Tous sont confirmés par les deux audits, dans le même ordre de sévérité.

### 1. Le drain global sans budget de temps

`GlobalScheduler.drain` (GlobalScheduler.java:15) borne le nombre de tâches par un snapshot de la file, mais pas leur durée. Les commandes tapées en chat y atterrissent (ServerGamePacketListenerImplMixin.java:53), et une commande comme /fill ou /locate s'exécute intégralement ici. Un seul joueur peut faire dépasser le thread global à volonté, sans aucun garde fou.

Le routage des commandes vers le global est un choix assumé, une commande doit pouvoir charger des chunks arbitraires. Un budget de temps avec report au tick suivant serait un filet, au prix d'une latence de commande.

## Les seconds couteaux

| Item | Coût | Détail | Fix |
|---|---|---|---|
| Drain de la pompe dans la quiesce | O(Q_pompe + R) par dimension (TickingManager.java:149) | Le balayage des régions ne se fait que pompe vide, il reste le drain de la pompe sans budget de temps, qui suit le churn de chunks | Budget de temps avec report |
| Trackers de distance vanilla | O(Δ) ≈ O(P × vue²) en pointe (DistanceManager.java:69) | Seul le propagateur de chargement est shardé, la simulation, le spawn naturel et les tickets joueur restent sériels | Porter sur le modèle shardé |
| Schedulers d'entité | O(S) de tests sériels, le travail tourne sur les régions (EntitySchedulerRegistry.tickOwned) | Chaque contexte de tick ne tique que les schedulers des entités qu'il possède ; la map est vide tant qu'aucun mod ne planifie | Aucun tant que S reste petit ; indexer par région si un mod planifie en masse |
| Trois balayages O(P) à test unitaire O(1) | La capture des orphelins de network/OrphanNetworkSweep, le filet loader, le filet tracking | Une lecture volatile par joueur et par balayage. Le suspend, l'envoi et le resume-flush ne touchent plus que les orphelins capturés, une exclusion par niveau au lieu d'une par joueur. Les filets gardent la liste vivante parce qu'un instantané pourrait re-tiquer un joueur retiré | Aucun, le coût restant est le test lui-même |
| Test de sommeil | O(P) par dimension, deux lectures de booléens par joueur (SleepStatus.update) | Décision de gameplay qui doit voir tous les joueurs, régionalisés compris | Aucun, vanilla incompressible |
| Fenêtre barrière | Sur ouverture: τ_max d'attente puis toutes les régions parquées pendant le drain (BarrierWindow.java:41) | Sans fonction `#tick` ni command block en repeat, elle ne s'ouvre que sur évènement: respawn, portail, commande console, /schedule, exécution déclenchée sur une région (récompense d'advancement, enchantement `run_function`, clic sur un panneau). Avec du contenu `#tick` et les gamerules par défaut, elle s'ouvre chaque tick et le serveur redevient mono thread | Les deux gamerules coupent le contenu fautif; réduire les raisons qui atterrissent en fenêtre |
| Graphe de distance des villages | O(Δ_POI) sériel sous le verrou POI global (PoiManager.java:240) | Explicitement non concurrent | Aucun fix connu |
| Attente de l'exclusion | Prise deux fois par tick et par dimension, chaque prise attend au pire τ_max | Le temps mural du thread global dépend de la région la plus lourde | Faire céder les régions à un point de sûreté |
| Évènements de tick Fabric | Deux levées de barrière par tick si un mod s'abonne | Même modèle de coût que la fenêtre | Aucun, c'est le contrat rendu aux mods |
| Tickables et code de mods | Arbitraire (MinecraftServer.java:1151) | Un mod qui enregistre un tickable coûteux le paie sur le global | Aucun, code tiers |
| Paquets de login | O(Q_login) par itération | Pic sur vague de connexions, le reste du login est déjà bien traité | Budget de N logins par tick |

## La formule du tick global

```
T_global ≈ O(1)                                    socle constant
  + 4 × O(P)                                       balayages à test unitaire O(1) : capture des orphelins, filet loader, filet tracking, sommeil
  + O(K)                                           transport des connexions
  + L × [ O(sections orphelines de l'index)        purge résiduelle des tickets, quasi nul
        + O(Q_pompe + R)                           quiesce
        + O(D)                                     dispatch des décharges d'entités
        + O(Δ_tickets)                             trackers vanilla
        + O(S) + O(Δ_POI) ]
  + O(min(Q_serial + D, 10 ms))                    file sérielle et décisions de déchargement, budget partagé entre dimensions
  + O(Q_global × coût_commande)                    drain global, item 1
  + [fenêtre non vide] × (τ_max + O(Q_window))     barrière, sur évènement
  + [tick d'autosave] × O(P_orphelins + L)         autosave : bump d'époque par dimension, sauvegarde des seuls joueurs orphelins
```

Le "L × [...]" se lit comme une somme sur les dimensions, chacune avec ses propres valeurs. Une dimension vide coûte quasi rien, et c'est presque toujours l'overworld qui domine chaque terme.

## Ordre d'attaque

L'autosave, les correctifs moyens et les schedulers d'entité sont traités. Restent, par rentabilité décroissante : les trackers de distance vanilla, le budget du drain de la pompe dans la quiesce, et le budget des logins.
