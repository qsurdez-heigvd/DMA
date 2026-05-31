# BLE

## Question 5.1

> La caractéristique permettant de lire la température retourne la valeur en degrés Celsius, multipliée par 10, sous la forme d’un entier non-signé de 16 bits. Quel est l’intérêt de procéder de la sorte ? Pourquoi ne pas échanger un nombre à virgule flottante de type float par exemple ?


Utiliser un entier non-signé de 16 bits plutôt qu'un float présente deux avantages principaux:

- Simplicité de traitement côté récepteur
- Economie de place comme 2 octets est plus léger que 4 octets

Avec un float, il faudrait gérer la mantisse et l'exposant, ce qui complexifie inutilement le décodage. Ici, une simple division par 10 suffit à retrouver la valeur en degrés. La multiplication par 10 permet par ailleurs de conserver une précision au dixième de degré sans sacrifier le poids plume du format. Cela devient très pertinent lors de transferts fréquents, où chaque optimisation compte. 



## Question 5.2

> Le niveau de charge de la pile est à présent indiqué uniquement sur l’écran du périphérique, mais nous souhaiterions que celui-ci puisse informer le smartphone sur son niveau de charge restante. Veuillez spécifier la(les) caractéristique(s) qui composerai(en)t un tel service, mis à disposition par le périphérique et permettant de communiquer le niveau de batterie restant via Bluetooth Low Energy. Pour chaque caractéristique, vous indiquerez les opérations supportées (lecture, écriture, notification, indication, etc.) ainsi que les données échangées et leur format.

Une seule caractéristique suffit pour répondre à ce besoin, le **BatteryLevel**.

Opérations supportées: 
- Lecture
- Notification

Format: 
- UInt8, valeur entre 0 et 100 représentant le pourcentage de charge

Le format UInt8 est parfaitement adapté puisqu'il couvre la plage 0-100% tout en restant minimal. Le support des notifications permet au périphérique d'alerter le smartphone lors de changements significatifs du niveau de batterie comme le passage en dessous de 10%. Cela permet d'éviter des lectures périodiques pouvant être inutiles. C'est donc une solution à la fois légère et efficace. 

