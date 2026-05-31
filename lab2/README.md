# DMA - iBeacons

Auteur: Quentin Surdez

## Réponses

### Question 1.1.1

> Est-ce que toutes les balises à proximité sont présentes dans toutes les annonces de la
> librairie ? Que faut-il mettre en place pour permettre de « lisser » les annonces et ne pas
> perdre momentanément certaines balises ?

Non, toutes les balises ne sont pas présentes dans toutes les annonces. Il y a plusieurs causes
possibles à cette absence:

- Des interférences radio peuvent bloquer temporairement les signaux BLE
- Les obstacles physiques peuvent bloquer momentanément la transmission

Le fix face à cette perte momentanée de données est l'ajout d'un nouvel attribut pour nos
`PersistentBeacon`.
Cet attribut se nomme `lastSeen` et est un timestamp de la dernière fois qu'un beacon a été détecté.
Le choix d'implémentation décidé permet de retirer les beacons de notre cache seulement si l'
application
ne l'a pas détecté depuis `STALE_THRESHOLD_MS`, à la place de le retirer après un seul cycle
d'absence.

### Question 1.1.2

> Nous souhaitons effectuer un positionnement en arrière-plan, à quel moment faut-il démarrer
> et éteindre le monitoring des balises ? Sans le mettre en place, que faudrait-il faire pour
> pouvoir continuer le monitoring alors que l’activité n’est plus active ?


En premier lieu, il est nécessaire d'avoir toutes les permissions pour pouvoir lancer une tâche en
background. Pour cela, la permission `ACCESS_BACKGROUND_LOCATION` doit être ajoutée au Manifest.

Ensuite pour le positionnement en arrière-plan:

- *Démarrer*: dans `Application.onCreate()` pour que le scan survive au-delà du cycle de ve de
  l'activité
- *Eteindre*: dans `Application.onTerminate()` ou sur une action explicite de l'utilisateur·ice.

Il existe différentes stratégies offertes par AltBeacon pour avoir un monitoring lorsque l'activité
n'est pas active avec chacune des avantages et inconvénients:

| Stratégie          | Avantages                                                  | Inconvénients                                                       |
|--------------------|------------------------------------------------------------|---------------------------------------------------------------------|
| JobScheduler       | Setup simple, économe pour la batterie                     | Peut prendre 15min pour dire qu'un beacon a disparu                 |
| Intent Scan        | Donne des updates à 1Hz tant que les beacons sont visibles | Peut prender 15in pour dire qu'un beacon a disparu                  |
| Foreground Service | Détection la plus rapide des updates des beacons           | Peut utiliser beacuoup de batterie, demande beaucoup de permissions |

Le choix de la stratégie à adopter dépend fortement de l'application en elle-même.
La source pour ce tableau peut se
trouver [ici](https://altbeacon.github.io/android-beacon-library/documentation.html)

### Question 1.1.3

> On souhaite trier la liste des balises détectées de la plus proche à la plus éloignée, quelles
> sont
> les valeurs présentes dans les annonces reçues qui nous permettraient de le faire ? Comment
> sont-elles calculées et quelle est leur fiabilité ?

Deux valeurs présentes dans les annonces iBeacon permettent d'estimer la distance:

- *RSSI* (Received Signal Strength Indicator): puissance du signal reçue en dBm. Plus le beaucon est
  loin, plus le RSSI est faible (voir négatif). Valeur brute et très instable.
- *TxPower*: puissance de transmission calibrée à 1 mètre, encodée dans le paquet iBeacon par le
  fabricant. Sert de référence.

La distance mesurée par la librairie AltBeacon est calculée comme suit :

`d=A*(r/t)^B+C`, où d est la distance estimée en mètres, r est le RSSI mesuré par l'appareil, t est
le TxPower. A, B et C sont des constantes. La source de cette formule se
trouve [ici](https://altbeacon.github.io/android-beacon-library/distance-calculations.html).

Après une observation du code source de la librairie, il y a un détail manquant dans la
documentation.

```java
public double calculateDistance(int txPower, double rssi) {
    double ratio = rssi * 1.0 / txPower;
    double distance;
    if (ratio < 1.0) {
        distance = Math.pow(ratio, 10);
    } else {
        distance = (mCoefficient1) * Math.pow(ratio, mCoefficient2) + mCoefficient3;
    }
    return distance;
}
```

Dans la class `CuveFittedDistanceCalculator`, il y a le calcul d'un ratio entre le RSSI et le
TxPower. Si ce dernier est plus petit que 1, alors la distance est calculée avec `ratio^10`. Cette
formule est utilisée uniquement lorsque le signal est fort et que donc le beacon est proche.

A 1 mètre l'estimation se fera entre 0.5-2 mètres. A 20 mètres, l'estimation se fera entre 10-40
mètres.

Bien que la fiabilité soit faible, nous utilisons cet attribut `beacon.distance` pour trier notre
liste de beacons. Il est intéressant de noter que la librairie AltBeacon lisse la distance sur les
20 dernières secondes. On peut lire dans
la [documentation](https://altbeacon.github.io/android-beacon-library/distance-calculations.html)
que les estimations de distance se font toutes les 20s en faisant une moyenne sans les 10% plus
haute et les 10% plus basses. Ainsi, nous n'implémentons pas de lissage de distance comme la
librairie le fait elle-même.

### Question 2.1.1

> Comment pouvons-nous déterminer notre position ? Est-ce uniquement basé sur notion de
> proximité étudiée dans la question 1.1.3, selon vous est-ce que d’autres paramètres peuvent
> être pertinents ?

Dans notre implémentation, la position est déterminée simplement en prenant le beacon le plus proche
via `beacons.minByOrNull { it.distance }` et en mappant son identifiant `minor` à un nom de lieu.

Pour aller plus loin, d'autres approches sont possibles:

- *Trilatération*: avec au moins 3 beacons aux positions connues, on peut calculer une coordonnée 2D
  par intersection des sphères de distance
- *Moyenne pondérée*: pondérer chaque beacon par `1/distance^2` pour obternir une position
  interpolée entre plusieurs balises.
- *Fusion de capteurs*: combiner les distances BLE avec l'accéléromètre et le gyroscope pour lisser
  les variations et maintenir une position entre deux cycles de scan.
- *Fingerprinting*: pré-enregistrer des cartes RSSI de l'environnement et comparer par ML,
  mais c'est passablement coûteux

L'unique notion de proximité reste intéressante pour des cas simples comme "dans quelle salle je me
trouve ?".

### Question 2.1.2

> Les iBeacons sont conçus pour permettre du positionnement en intérieur. D’après l’expérience
> que vous avez acquise sur cette technologie dans ce laboratoire, quels sont les cas d’utilisation
> pour lesquels les iBeacons sont pleinement adaptés (minimum deux) ? Est-ce que vous voyez
> des limitations qui rendraient difficile leur utilisation pour certaines applications ?

Cas d'utilisation bien adaptés:

- *Navigation en intérieur de pièce en pièce*: Avoir un contenu particulier en fonction de la salle
  du musée dans laquelle on se trouve. La détection de zone suffit sans avoir besoin de
  positionnement précis.
- *Marketing de proximité*: déclencher des notifications en fonction du produit vers lequel le/la
  client·e se dirige.

Limitations:

- *Précision non garanties*: comme discuté précédemment les fluctuations RSSI rendent la distance
  estimée peu fiable.
- *Interférences*: si le musée est bondé, les corps humains peuvent intercepter les signaux et
  l'application ne saura pas qu'elle a changé de salle
- *Impact sur la batterie*: si le téléphone est constamment en train de scanner cela peut avoir un
  impact certain sur la consommation de la batterie.

## Architecture

Plusieurs choix architecturaux ont été fait dans ce laboratoire. Pour suivre le pattern MVVM, nous
avons décidé de créer une classe nommée `BeaconRepository` qui a comme responsabilité de s'occuper
des données. C'est elle qui aura connaissance de l'utilisation de la librairie AltBeacon et qui va
décider de comment traiter les données reçues par le `BeaconManager`. Cette classe va uniquement
communiquer avec `BeaconsViewModel`.

Nous avons décidé d'ajouter un attribut `lastSeen` sur les `PersistentBeacon` pour avoir
l'information de quand est-ce que le beacon a été vu pour la dernière fois. Cela nous permet de le
retirer de la liste après `STALE_THRESHOLD_MS` et non pas s'il n'est que momentanément absent.
Pour que l'utilisateur·ice ne voit pas une valeur constamment changer sur son écran, nous avons
décidé d'update ce champ `lastSeen` toutes les `STALE_THRESHOLD_MS/2` millisecondes.

Dans le ViewModel, nous avons donc notre repository qui est inité de manière lazy. Nous lui passons
en callback la mise à jour des live data mutables. Le ViewModel expose aussi les fonctions du
repository et sert de pont entre l'activité et le data layer. Pour se faire nous avons donc changé
la classe héritée par `BeaconsViewModel` de ViewModel à AndroidViewModel pour pouvoir passer le
contexte depuis l'activité principale au repository. Une alternative aurait été de donner
l'ownership du repository à l'activité principale en bypassant le ViewModel, mais cela nous semblait
anti-pattern.