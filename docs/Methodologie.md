# Méthodologie
Comment on travaille sur ce projet. Ces règles ont fait leurs preuves, on ne les contourne pas.

## Compatibilités.
Il faut penser aux mods/datapacks dans notre mod. Par exemple, si on a des soucis sur le POI, il ne faut pas fixer seulement le Raid ou le Dragon, il faut prendre en compte les mods qui auraient du POI. Autre exemple, si les portails ne fonctionnent plus, il ne faut pas seulement fixer spécifiquement le Nether ou l'End, mais penser à tous les portails que les développeurs peuvent créer Aether, Twilight, etc. Donc globalement, on essaye de remonter plus haut dans la stack pour avoir la compatibilité la plus générique possible.

## Corriger un bug
Le cycle est toujours le même : on détecte, on reproduit dans un seul test unitaire qui doit être rouge, on corrige, le test passe au vert, on valide en jeu. Un fix sans reproduction d'abord est un fix qu'on ne comprend pas. Le test qui a reproduit un crash garde dans sa javadoc la date et le scénario, une ou deux phrases, c'est notre mémoire des bugs.
Quand un crash vient d'un rapport de région, tout y est déjà : l'id, la dimension, le tick, la pile. On lit la pile en entier avant de toucher au code, la cause racine est rarement la première ligne.

## Écrire du code
La logique vit dans nos modules, les mixins ne sont que des accroches d'une ligne, écrites pour la compatibilité inter mods : injection ciblée ou wrap qui se compose avec les mixins des autres, jamais d'`@Overwrite` sauf impasse absolue justifiée en javadoc. Pas de fonction à usage unique, pas de code mort, pas de code commenté, pas de duplication de source de vérité. Les primitives de concurrence restent dans nos classes. On évite les casts non vérifiés par l'architecture plutôt que par l'annotation.

Les commentaires : une phrase, deux au maximum quand il y a une trace de bug à garder. Un commentaire dit ce que le code ne peut pas dire, une contrainte, un pourquoi. Jamais de paraphrase du code, jamais de narration de la modification. Un commentaire écrit ne se réécrit pas à chaque passage.
On pense long terme : pas de fix rapide qui devient une dette, pas de cas par cas quand un point de passage unique traite toute la classe du problème. Si une correction propre demande de repenser un morceau d'architecture, on le fait.

## Tester
Trois niveaux. Les tests unitaires, `gradlew test`, tournent avec le vrai Minecraft bootstrappé quand il le faut. La validation en jeu suit une checklist courte : connexion, déconnexion, casser et poser, coffres, four, chat, commande, mort et respawn, portail aller retour. La charge se teste avec les bots overstress en montée progressive, spark en `--thread *` sans quoi on ne voit que le thread serveur, et le CSV de métriques par région.
Un chantier n'est terminé que vert aux trois niveaux. Entre deux étapes d'un gros chantier, l'arbre reste compilable, testé et jouable.

## Documenter et committer
La doc est factuelle et au présent : elle décrit ce qui est, jamais ce qu'on a tenté. Le passé utile vit dans l'historique de la roadmap. On écrit en français simple, phrases complètes, sans tiret de ponctuation, comme à l'oral.
Les commits racontent le pourquoi en anglais : une ligne de titre qui nomme le changement, un corps qui explique le problème, le mécanisme et la décision. On ne committe jamais sans l'accord du propriétaire du projet, qui review avant. Chaque écart avec vanilla nouveau ou supprimé se reflète dans `docs/Compromis.md` dans le même commit.