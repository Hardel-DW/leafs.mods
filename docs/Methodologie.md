# Méthodologie
Comment on travaille sur ce projet. Ces règles ont fait leurs preuves, on ne les contourne pas.

## Compatibilités.
Il faut penser aux mods/datapacks dans notre mod, exemples. Si on a des soucis sur le POI, il ne faut pas fixer seulement le Raid ou le Dragon, il faut prendre en compte les mods qui auraient du POI. On doit toujours considérer les contenus de mods, portails, blocs, entités, POI, structures customs... ainsi que des concepts uniques qui n'existent pas en vanilla.

## Corriger un bug
Le cycle est toujours le même, on détecte, on reproduit dans un seul test unitaire qui doit être rouge, on corrige, le test passe au vert, on valide en jeu. Un fix sans reproduction n'a aucune valeur.
Quand un crash est global ou vient d'une région, on a l'id, la dimension, le tick, la pile. On lit la pile en entier avant de toucher au code, la cause racine est rarement la première ligne.

## Écrire du code
La logique vit dans nos modules, les mixins ne sont que des accroches, écrites pour la compatibilité inter mods : injection ciblée ou wrap qui se compose avec les mixins des autres. Pas de fonction à usage unique, pas de code mort, pas de code commenté, pas de duplication de source de vérité. Les primitives de concurrence restent dans nos classes. On évite les casts non vérifiés par l'architecture plutôt que par l'annotation.

Les commentaires : une phrase, deux au maximum quand il y a une trace de bug à garder. Un commentaire dit ce que le code ne peut pas dire, une contrainte, un pourquoi. Jamais de paraphrase du code, jamais de narration de la modification. Un commentaire écrit ne se réécrit pas à chaque passage.
On pense long terme : pas de fix rapide qui devient une dette, pas de cas par cas quand un point de passage unique traite toute la classe du problème. Si une correction propre demande de repenser un morceau d'architecture, on le fait.

## Tester
- Les tests unitaires, `gradlew test`, tournent avec le vrai Minecraft bootstrappé quand il le faut.
- La validation en jeu suit : connexion, déconnexion, casser et poser, coffres, four, chat, commande, mort et respawn, portail aller retour. La charge se teste avec les bots overstress en montée progressive, et spark en `--thread *` sans quoi on ne voit que le thread serveur.
