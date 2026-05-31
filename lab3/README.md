# Report - Trilateration

Author: Quentin Surdez


## Réponses

### Question 1.1

> Par rapport à un seul AP, que pouvez-vous dire sur la précision de la distance estimée ? Est-ce
que la présence d’un obstacle (fenêtre, mur, personne) entre l’AP et le smartphone a une
influence sur la précision ? Est-ce que faire une moyenne sur plusieurs mesures permet d’avoir
une estimation plus fiable de la distance ?

Pour un seul AP, lorsqu'il n'y a aucun obstacle entre le capteur et l'AP lui-même, la précision de la distance estimée est relativement bonne. Cependant, avec des obstacles, le signal peut être réfléchi sur plusieurs surfaces avant d'arriver et le temps entre le départ du signal et son arrivée au capteur est plus long que prévu. La distance estimée sera alors souvent plus grande que l'actuelle.

Faire une moyenne sur plusieurs mesures permet de réduire le bruit et d'améliorer la stabilité de la mesure estimée. Cela est vrai si la plupart des mesures proviennent d'un signal qui n'a pas rencontré d'obstacles. En effet, si l'obstacle n'est pas mobile comme une fenêtre, toutes les mesures sont plus hautes que la distance réelle et ainsi la moyenne sera tout autant impactée. Une solution possible serait de prendre la plus petite valeur estimée de la distance. Cette dernière pourrait nous orienter pour traiter les données suivantes comme un temps de parcours de l'air du signal minimum est souvent égal à la distance exacte. Cependant, si le bruit est fort, cela peut être une solution risquée.


### Question 2.1

> Nous avons également placé des AP à différents endroits de l’étage B. La carte et la position
de ces huit AP sont fournies dans le code. Pour activer une localisation sur l’étage B, il suffit de
modifier la configuration placée dans la LiveData _mapConfig dans le WifiRttViewModel. Que
pouvons-nous dire de la position obtenue en se promenant dans les couloirs de l’étage ? Doit-
on tenir compte de tous les AP pour calculer la position ?

La position obtenue est relativement précise, bien qu'elle soit parfois décalée par rapport à la position réelle. Il peut arriver que la position calculée soit complètement fausse et fasse sauter le point en dehors de la carte. L'hypothèse pour ce comportement est la distance entre le smartphone et les AP ou la présence d'obstacles qui amènent du bruit dans les mesures.

Il n'est pas nécessaire de prendre en compte tous les AP pour calculer la position. Comme nous devons calculer une position en 2 dimensions, au minimum 3 APs sont nécessaires. Un 4ème permet de garantir une meilleure précision comme avec seulement 3 APs, le système d'équations de la trilatération peut avoir plusieurs solutions.


### Question 2.2

> Pouvons-nous déterminer la hauteur du mobile par trilatération ? Si oui qu’est-ce que cela
implique ? La configuration pour l’étage B contient la hauteur des AP et vous permet donc de
faire des tests.

Oui, c'est tout à fait possible. Nous avons alors un système avec 3 inconnues, x, y et z. Nous devons avoir au minimum 4 APs disponibles pour pouvoir résoudre ce système d'équations. Nous pouvons observer que l'estimation de la hauteur est bonne pour la configuration de l'étage B.


## Architecture

Nous avons fait le choix de lancer `estimateLocation` dans une coroutine sur `Dispatchers.Default` comme le calcul de la trilatération est une opération CPU-intensive. La bloquer sur le thread principal nous semblait peu intéressant. Nous avons donc changé la signature de `estimateLocation` pour prendre en paramètres la liste d'APs et la configuration. Cela permet d'éviter une lecture de `LiveData` depuis une coroutine, ce qui nous semblait être une bonne pratique pour éviter des potentiels bugs de concurrence.

Concernant le choix du solver pour la trilatération, nous avons testé le `NonLinearLeastSquearesSolver` (Levenberg-Merquardt) et le `LinearLeastSquearesSolver`. Malgré la note dans la documentation de la library indiquant que le solver linéaire est réservé aux tests, celui-ci donne de meilleurs résultats en pratique. La version non-linéaire produisait des estimations moins stables et plus souvent aberrantes (le point sautait hors de la carte).

## Remarques

Nous avons pu observer, lors de nos tests, qu'un des APs de la salle B30 décroche par moments, ce qui perturbe profondément la position estimée (en debug le cercle jaune rétrécit d'un coup pour revenir juste après). Nous avons une piste d'amélioration non implémentée qui serait de faire une moyenne des précédentes estimations de la distance pour l'affichage. Ainsi, les momentanés problèmes/bruits, ne viendraient pas perturber l'affichage et l'expérience utilisateur·ice.
