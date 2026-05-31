# DMA Communication protocols

Author: Quentin Surdez

# Introduction
Ce laboratoire est séparé en 3 parties différentes.

Dans la première partie, notre objectif est d’intégrer les différentes manières de sérialiser et compresser des échanges de données avec un endpoint  sur un serveur mis à disposition.

La deuxième consiste à mettre en place un échange basé sur le protocole GraphQL.

La dernière partie nous permet de nous familiariser avec le service de notifications push Firebase Cloud Managing. 

# Sérialiser et compresser des échanges avec un serveur

## Architecture

Pour gérer la sérialisation et désérialisation, nous avons créé une interface nommée `MeasuresSerializer` contenant une fonction de sérialisation et une fonction de désérialisation pour les mesures. Nous avons ensuite créé trois classes implémentant cette interface, une pour chaque format de données demandé (JSON, XML, Protobuf). Pour effectuer une sérialisation, il suffit d'appeler la fonction du sérialiseur souhaité, de même pour la désérialisation. L'interface ainsi que les classes sont trouvables dans le package nommé `serializers`.

La gestion de la compression de données (deflate) est quant à elle écrite de manière "inline" dans le code.

## Question théorique

> Quel gain, en volume et en temps, peut-on constater en moyenne sur les données échangées (xml,
> json et aussi protobuf) en utilisant la compression mise en place au point 1.4 ? Vous comparerez
> plusieurs tailles de contenu, plusieurs vitesses de transmission et plusieurs méthodes de sérialisation.
> Vous pouvez vous aider des valeurs « Received Size » (taille en bytes du contenu transféré) et
> «Payload Size» (taille en bytes du contenu après décompression) indiquées dans l’interface de logs du
> serveur. Est-ce utile de compresser dans tous les cas ? Veuillez élaborer votre réponse avec des chiffres
> et des exemples.

### Méthodologie

Pour faire nos mesures, nous avons envoyé des données au nombre de 3 puis de 10 pour chaque type de sérialisation. 
Nous avons fait nos mesures pour les réseaux 2G, 3G, 4G et 5G. La colonne temps représente la valeur que nous postons
après réception de la réponse du serveur et avant update des différentes données sur l'interface. Cela permet d'avoir
une mesure bonne pour l'envoi/réponse de chaque requête.

### Gain de volume

La compression réduit constamment le volume des données envoyées au serveur, la payload. Cependant, on observe une vraie
différence de magnitude entre les différentes sérialisations mises en place. Nous allons nous intéresser aux formats JSON
et XML d'abord en choisissant les requêtes avec 10 mesures comme c'est là que la compression sera la plus évidente.

On observe que JSON et XML sont des formats basés sur du texte lisible et ils sont donc fortement impactés par la compression
de leur données. On observe pour JSON un taux de compression ~81% en passant de 1468B à 270B. Pour XML, l'ordre de magnitude
est similaire, mais avec un taux un peu plus bas ~77% en passant de 1502B à 336B.

On observe que la sérialisation Protobuf a un taux de compression bien moindre par rapport à XML et JSON. Cela s'explique
par le fait que Protobuf est déjà compressé par design comme c'est un format binaire. Le taux de
compression pour 10 mesures est ~47% en passant de 337B à 178B. Avec notre observation précédente, on relève que c'est 
un taux élevé malgré le fait que Protobuf soit en binaire. On observe aussi et surtout que c'est très très compact, que
les algorithmes de compression fonctionnent sur ses payload et que cela est très intéressant pour soulager la charge 
au niveau des réseaux. 

### Gain de temps

Ici, on observe que la compression n'est pas toujours égale à un gain de temps. Le gain est particulièrement dépendant 
du réseau avec lequel la requête est faite. 

En effet, pour le réseau 2G, on observe un gain substantiel lors de l'envoi de 10 mesures pour le JSON et XML. En effet, 
on passe de 267ms à 115ms pour le JSON et de 316ms à 136ms pour le XML. Pour Protobuf, plus de données seraient
nécessaires pour se faire une bonne idée comme on passe de 111ms à 94ms. Le gain actuel n'est pas très substantiel pour
Protobuf. Pour la 2G on voit que la bande passante est réduite et c'est donc important d'envoyer le moins possible de
data. 

Pour les réseaux 4G/5G, ici on observe plutôt l'inverse. La requête semble prendre plus de temps en étant compressée 
qu'en étant en "plain-text". Pour JSON on passe de 69ms à 73ms, pour XML de 78ms à 74ms et pour Protobuf de 64ms à 63ms. 
Plus de données seraient intéressant pour avoir un argument un peu plus fort. Cependant, on peut émettre l'hypothèse 
qu'il y a un traitement des données compressées de la part du serveur qui est plus lent, car il y a besoin d'opérations
liées au CPU pour décompresser, lire et recompresser sa réponse. Nous en concluons qu'il y a plusieurs facteurs
à prendre en compte pour savoir si la compression en vaut la peine ou non.

### Conclusion

Nous pensons qu'il est souvent intéressant de compresser le JSON et le XML. En effet, la perte ou le quasi nul gain de
temps sur des réseaux modernes est un petit sacrifice par rapport au gain de bande passante que la compression offre, particulièrement lorsqu'une 
application est utilisée par de nombreux et nombreuses utilisateur·ices.

Cependant, pour Protobuf, la conclusion est plus nuancée. Les payload étant déjà relativement petites, il y aurait un 
intérêt à ne pas les compresser pour garder une vitesse élevée sans avoir d'overload de la part du serveur pour décompresser
et recompresser les données. 

---

#### JSON

| Nb Measures | Network | Compression | Time (ms) | Received (B) | Payload (B) | Compression Rate |
|-------------|---------|-------------|-----------|--------------|-------------|------------------|
| 3           | 2G      | None        | 140       | 465          | 465         | 0%               |
| 3           | 2G      | Deflate     | 98        | 172          | 424         | 59.4%            |
| 10          | 2G      | None        | 267       | 1468         | 1468        | 0%               |
| 10          | 2G      | Deflate     | 115       | 270          | 1468        | 81.6%            |
| 3           | 3G      | None        | 35        | 433          | 433         | 0%               |
| 3           | 3G      | Deflate     | 83        | 148          | 433         | 65%              |
| 10          | 3G      | None        | 124       | 1461         | 1461        | 0%               |
| 10          | 3G      | Deflate     | 96        | 283          | 1461        | 80.2%            |
| 3           | 4G      | None        | 105       | 433          | 433         | 0%               |
| 3           | 4G      | Deflate     | 76        | 148          | 433         | 65%              |
| 10          | 4G      | None        | 42        | 1461         | 1461        | 0%               |
| 10          | 4G      | Deflate     | 75        | 283          | 1461        | 80.2%            |
| 3           | 5G      | None        | 36        | 433          | 433         | 0%               |
| 3           | 5G      | Deflate     | 30        | 148          | 433         | 65%              |
| 10          | 5G      | None        | 69        | 1461         | 1461        | 0%               |
| 10          | 5G      | Deflate     | 73        | 283          | 1461        | 80.2%            |

---

#### XML

| Nb Measures | Network | Compression | Time (ms) | Received (B) | Payload (B) | Compression Rate |
|-------------|---------|-------------|-----------|--------------|-------------|------------------|
| 3           | 2G      | None        | 169       | 561          | 561         | 0%               |
| 3           | 2G      | Deflate     | 121       | 253          | 561         | 54.9%            |
| 10          | 2G      | None        | 316       | 1502         | 1502        | 0%               |
| 10          | 2G      | Deflate     | 136       | 336          | 1502        | 77.6%            |
| 3           | 3G      | None        | 77        | 533          | 533         | 0%               |
| 3           | 3G      | Deflate     | 80        | 213          | 533         | 60%              |
| 10          | 3G      | None        | 95        | 1493         | 1493        | 0%               |
| 10          | 3G      | Deflate     | 86        | 353          | 1493        | 76%              |
| 3           | 4G      | None        | 78        | 533          | 533         | 0%               |
| 3           | 4G      | Deflate     | 72        | 213          | 533         | 60%              |
| 10          | 4G      | None        | 94        | 1493         | 1493        | 0%               |
| 10          | 4G      | Deflate     | 89        | 353          | 1493        | 76%              |
| 3           | 5G      | None        | 53        | 533          | 533         | 0%               |
| 3           | 5G      | Deflate     | 71        | 213          | 533         | 60%              |
| 10          | 5G      | None        | 78        | 1493         | 1493        | 0%               |
| 10          | 5G      | Deflate     | 74        | 353          | 1493        | 76%              |

---

#### Protobuf

| Nb Measures | Network | Compression | Time (ms) | Received (B) | Payload (B) | Compression Rate |
|-------------|---------|-------------|-----------|--------------|-------------|------------------|
| 3           | 2G      | None        | 86        | 94           | 94          | 0%               |
| 3           | 2G      | Deflate     | 49        | 72           | 94          | 23.4%            |
| 10          | 2G      | None        | 111       | 323          | 323         | 0%               |
| 10          | 2G      | Deflate     | 94        | 178          | 337         | 47%              |
| 3           | 3G      | None        | 64        | 105          | 105         | 0%               |
| 3           | 3G      | Deflate     | 74        | 53           | 105         | 49.5%            |
| 10          | 3G      | None        | 69        | 337          | 337         | 0%               |
| 10          | 3G      | Deflate     | 68        | 178          | 337         | 47%              |
| 3           | 4G      | None        | 34        | 105          | 105         | 0%               |
| 3           | 4G      | Deflate     | 33        | 53           | 105         | 49.5%            |
| 10          | 4G      | None        | 58        | 337          | 337         | 0%               |
| 10          | 4G      | Deflate     | 74        | 178          | 337         | 47%              |
| 3           | 5G      | None        | 30        | 105          | 105         | 0%               |
| 3           | 5G      | Deflate     | 39        | 53           | 105         | 49.5%            |
| 10          | 5G      | None        | 64        | 337          | 337         | 0%               |
| 10          | 5G      | Deflate     | 63        | 178          | 337         | 47%              |


# GraphQL

## Architecture

Afin de faciliter l'implémentation des méthodes de chargement de données (`loadAllAuthorsList()` et `loadBooksFromAuthor()`), nous avons créé une fonction nommée `executeQuery()`, prenant une query en paramètre et l'exécutant sur le serveur.

## Question théorique

> Par rapport à l’API GraphQL mise à disposition pour ce laboratoire. Avez-vous constaté des points qui
> pourraient être améliorés pour une utilisation mobile ? Veuillez en discuter en mettant en évidence
> les limitations de l’implémentation fournie, vous pouvez élargir votre réflexion à une problématique
> plus large que la manipulation effectuée.


En premier lieu, nous pourrions avoir le support, au niveau du serveur, pour les requêtes compressées. Comme ces 
dernières doivent se faire en JSON, les compresser serait intéressant pour économiser de la bande passante. 

Nous pourrions mettre en cache les requêtes précédentes et uniquement faire des requêtes au niveau du serveur après 
un certain laps de temps voir s'il a eu des modifications avec un simple timestamp de la dernière modification. Cela
permettrait de garder un contact régulier avec le serveur, sans avoir à lui envoyer une requête GraphQL à chaque fois. 
Une utilisation intelligente des ressources à disposition.

# Firebase Cloud Managing

## Question théorique

> Veuillez expliquer à quoi sert le token obtenu avec la méthode onNewToken du Service Firebase de
> réception des messages. Quand est-ce que ce token est généré ou regénéré, et qu’est-ce qu’une
> application doit faire avec celui-ci, vous illustrerez votre réponse en prenant l’exemple d’un service de
> messagerie, tel que WhatsApp. Si l’utilisateur dispose de son application de messagerie sur plusieurs
> appareils, par exemple sur son smartphone et sa tablette, comment doit-on gérer les tokens obtenus
> sur chaque device ?

Le token est un identifiant donné à une instance d'application par Firebase. Il va servir
à identifier de manière unique un appareil afin que le serveur puisse envoyer des messages
push vers cet appareil précis. 

Ce token est créé lors de l'installation initiale de l'application ou lorsque le service 
Firebase se lance pour la première fois. La méthode `onNewToken` du `FirebaseMessagingService`
est appelée lors de la première génération et à chaque fois que ce token est changé. Cela
peut arriver dans les situations suivantes:
- L'app est réinstallée ou les données de l'app sont effacées
- Le token expire
- Les politiques de sécurité de Firebase sont mise à jour et cela nécessite un nouveau token

Ensuite, l'app doit communiquer ce nouveau token au serveur backend Firebase afin que ce dernier 
conserve une liste des tokens à jour. 

Exemple: Lorsqu'une utilisatrice s'inscrit sur Whatsapp, l'installation reçoit un token unique.
Le serveur Whatsapp enregistre ce token associé au compte de l'utilisatrice. Si sur plusieurs appareils,
il y a un token par appareil qui sont tous reliés au compte de l'utilisatrice sur le serveur. 
Cela va permettre d'envoyer un message push en utilisant tous les tokens de l'utilisatrice 
afin que chacun de ses appareils aient la notification. Si l'un des tokens change, l'app 
en informe le serveurs pour que la mise à jour soit effectuée. 


