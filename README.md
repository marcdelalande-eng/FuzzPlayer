# Valve Player — Android 15

Lecteur audio local pour téléphone Android 15, en Java/AndroidX Media3, avec esthétique d'amplificateur vintage noir/laiton.

## Fonctions incluses

- Lecture : WAV, MP3, FLAC, AAC/M4A, OGG/Vorbis/Opus selon les décodeurs de l'appareil/Media3.
- Scan de la bibliothèque Android (`MediaStore`).
- Ajout de dossiers avec le Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`) et scan récursif.
- Classement par album puis numéro de piste/titre.
- Navigation : album précédent/suivant, titre précédent/suivant, lecture/pause.
- Lecture en arrière-plan avec `MediaSessionService` / ExoPlayer.
- Contrôles médias Android et périphériques grâce à la `MediaSession`.
- Widget 4x1 avec 5 commandes principales.
- Mode voiture manuel ou automatique.
- Sélection par cases à cocher parmi les périphériques Bluetooth appairés.
- Bascule automatique du widget vers une variante à boutons agrandis lors de la connexion d'un périphérique sélectionné.

## Compilation

Configuration :

- `compileSdk 35`
- `targetSdk 35`
- `minSdk 35` (Android 15 minimum)
- Java 17
- Android Gradle Plugin 8.13.2
- Gradle 9.4.1
- Media3 1.11.0

Ouvrir le dossier **ValvePlayer** dans Android Studio, laisser Gradle synchroniser les dépendances puis lancer `app` sur un appareil Android 15.

Le script `gradlew` fourni télécharge Gradle 9.4.1 à la première utilisation. Dans un terminal :

```bash
./gradlew assembleDebug
```

APK attendu après compilation :

`app/build/outputs/apk/debug/app-debug.apk`

## Première utilisation

1. Autoriser l'accès aux fichiers audio.
2. Autoriser l'accès aux périphériques Bluetooth si le mode voiture doit être utilisé.
3. Appuyer sur **Scanner**.
4. Optionnel : **Ajouter dossier** pour choisir un ou plusieurs répertoires non présents/complètement indexés dans MediaStore.
5. Ouvrir **Voiture**, cocher les périphériques souhaités et activer l'automatisme.
6. Ajouter le widget **Valve Player** à l'écran d'accueil et le dimensionner en 4x1.

## Remarques

- L'interface est seulement *inspirée* du langage visuel d'un amplificateur guitare vintage : aucun logo ou actif de marque Marshall n'est inclus.
- La détection voiture utilise les événements de connexion/déconnexion Bluetooth des appareils sélectionnés. Elle ne lance pas automatiquement la musique : elle active le mode voiture et adapte le widget.
- La liste Bluetooth présente les appareils **déjà appairés** dans Android. L'appairage lui-même reste géré par les réglages du téléphone.
- Les fichiers accessibles via un dossier SAF sans métadonnées indexées utilisent le nom de fichier comme titre et le nom du dossier parent comme album.

## V2 — Albums et commandes Sandero III

- L'accueil affiche **les albums**, dans l'ordre alphabétique, avec une vignette (si disponible), l'artiste et le nombre de pistes.
- Toucher un album ouvre sa liste de pistes ; « Lire l'album » démarre à sa première piste.
- La pochette est cherchée dans le **même dossier** que les pistes, avec le nom `folder`, `cover`, `front`, `album`, `artwork` ou `albumart` et une extension `.jpg`, `.jpeg`, `.png`, `.webp` (insensible à la casse). Cela fonctionne sur les dossiers SAF sélectionnés ou les images indexées dans MediaStore, sous réserve de l'autorisation Images.
- Commandes matérielles : `KEYCODE_MEDIA_NEXT` / `KEYCODE_MEDIA_PREVIOUS` (ainsi que `SKIP_FORWARD` / `SKIP_BACKWARD`) sont interceptées sur `MediaSession.Callback.onMediaButtonEvent` : un appui provoque une navigation de piste après 550 ms ; deux appuis dans **le même sens** et à moins de 550 ms d'intervalle provoquent un changement d'album, sans passer par deux pistes. Un changement de sens exécute d'abord l'appui précédent.
- Cette logique dépend de la transmission de ces événements **bruts** par l'autoradio Bluetooth. Si la Dacia transmet uniquement des commandes AVRCP traitées en amont par Android, le callback peut ne pas voir les deux pressions : valider sur le véhicule (Media Display / Media Nav selon équipement). La compatibilité véhicule n'est pas garantie sans essai réel.
- Pour les albums ayant le même titre dans plusieurs répertoires, le regroupement actuel reste basé sur le nom d'album renseigné par MediaStore ; un affinage par chemin/album-artiste pourra être nécessaire.

### Essai dans le véhicule (stationné)

1. Autoriser les fichiers audio, les images et Bluetooth dans l'application.
2. Associer la Sandero au téléphone, démarrer un album et vérifier que l'autoradio pilote bien Valve Player.
3. Faire un seul cran avant/arrière, attendre 550 ms : une piste doit changer.
4. Faire deux crans rapides du même côté : l'album doit changer.
5. Si les commandes passent à une autre application, sélectionner Valve Player comme lecteur audio actif sur le téléphone et dans les sources Bluetooth de la voiture.


## Boutons personnalisés (édition images)

Les cinq images fournies sont intégrées dans `app/src/main/res/drawable-nodpi/` :
- `button_album_prev.png` : album précédent (fond doré)
- `button_track_prev.png` : piste précédente (fond bleu)
- `button_play_pause.png` : lecture/pause (fond rouge, même illustration pour les deux états)
- `button_track_next.png` : piste suivante (fond bleu)
- `button_album_next.png` : album suivant (fond doré)

Les images sont utilisées dans l'application et les widgets 4×1 normal/voiture. Le bouton central
utilise la même illustration en lecture comme en pause. Les ressources sont redimensionnées à 360px
pour limiter la taille des RemoteViews Android.

## Widget 4×2 (nouveau)
Dans le sélecteur de widgets Android, choisir « Valve Player » en format **4×2** (le 4×1 reste disponible). Le haut affiche **Interprète / Album / Titre**, en écriture *Lobster Two Bold*, style rétro. Le bas reprend les cinq boutons guitare. La version voiture utilise des boutons de proportions plus grandes. Les métadonnées suivent les changements de piste du lecteur et sont mémorisées pour l’affichage si le service est arrêté. Si le lanceur force une autre dimension de widget, les lignes peuvent être tronquées.

Police : Lobster Two Bold (SIL Open Font License 1.1), détails : `FONT-LICENSE.txt`.


Voir **PERSONNALISER-INTERFACE.md** pour la modification graphique de tous les écrans et widgets dans Android Studio.
