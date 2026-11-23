# FuzzPlayer — corrections

Je n'ai pas pu compiler le projet ici : relance `./gradlew assembleDebug` après avoir copié les fichiers.

## Fichiers complets fournis (à copier dans le dépôt, mêmes chemins)

| Fichier | Correction |
|---|---|
| `PlaybackService.java` | Le lecteur ne gérait ni le **focus audio** ni le **casque/Bluetooth débranché** : il jouait par-dessus le GPS et les appels, et continuait sur le haut-parleur. Ajout de `setAudioAttributes(..., true)` et `setHandleAudioBecomingNoisy(true)`. La position n'est plus écrite toutes les 5 s quand rien ne joue. |
| `LibraryStore.java` | Après une édition de tags (album renommé), la bibliothèque n'était plus triée : un album pouvait s'afficher en plusieurs morceaux et « album suivant/précédent » sautait mal. Tri refait à chaque enregistrement. |
| `AlbumAdapter.java` | Les pochettes étaient décodées **sur le thread UI** à chaque ligne affichée, sans cache (défilement saccadé, mémoire). Décodage en arrière-plan, taille adaptée, cache, vues réutilisées. |
| `CarSettingsActivity.java` | Écran sous la barre d'état / de navigation (affichage bord à bord imposé avec targetSdk 35). Message de permission Bluetooth faux (renvoyait vers un écran qui ne la demande jamais). |

## Petits changements à faire à la main

**1. `MusicBrainzLookup.java`, méthode `search`** — un album contenant `"` ou `\` casse la requête :

```java
// avant
String query = "release:" + "\"" + album + "\"";
// après
String escaped = album.replace("\\", "\\\\").replace("\"", "\\\"");
String query = "release:\"" + escaped + "\"";
```

**2. `AndroidManifest.xml`** — le récepteur Bluetooth n'a pas besoin d'être exporté (les diffusions système lui arrivent quand même). Exporté, n'importe quelle appli peut lui envoyer de faux évènements et activer le mode voiture :

```xml
<receiver android:name=".BluetoothConnectionReceiver" android:exported="false">
```

**3. `LibraryScanner.java`, méthode `supported`** — le format Opus annoncé dans le README n'est pas reconnu par extension (seul le type MIME le rattrape, souvent absent) :

```java
|| n.endsWith(".ogg") || n.endsWith(".opus") || n.endsWith(".m4a")
```

**4. `.gitignore`** — ces fichiers sont dans le dépôt alors qu'ils ne devraient pas l'être (dont un APK de 12 Mo et un chemin local de ton PC) :

```
local.properties
/.idea/
/app/release/
*.apk
```
Puis, une fois : `git rm --cached local.properties app/release/app-release.apk` et `git rm -r --cached .idea`.

**5. `res/layout/widget_player_large.xml` et `widget_player_large_car.xml`** — dans le bloc `wMetadata`, remplace `android:layout_width="match_parent"` par `android:layout_width="0dp"` (il est dans une ligne horizontale avec un poids ; `match_parent` marche par hasard).

## À vérifier / incohérences (non modifiées)

- **Lecture auto en Bluetooth** : le README dit que l'appli ne lance pas la musique à la connexion, mais le code appelle `player.play()`. Dis-moi lequel est voulu.
- **Démarrage depuis le récepteur Bluetooth** : si l'appli n'est pas déjà en cours, Android 12+ interdit `startService` en arrière-plan ; l'évènement sera alors seulement journalisé.
- **Notifications** : la permission `POST_NOTIFICATIONS` (Android 13+) n'est ni déclarée ni demandée ; la notification de lecture peut donc ne pas s'afficher.
- **Albums multi-disques** : le disque n'est pas lu, donc `CD1/CD2` avec le même nom d'album mélangent les pistes.
- **README** : indique AGP 8.13.2 / compileSdk 35 alors que le projet utilise AGP 9.2.1 / compileSdk 36, et demande l'autorisation « fichiers audio » qui n'est pas utilisée (accès par dossiers).
