# Modifier Valve Player visuellement dans Android Studio

## Ouvrir les maquettes

1. Ouvre le dossier **ValvePlayer** dans Android Studio et attends la synchronisation Gradle.
2. Dans l’explorateur de projet, sélectionne **Android** puis `app > res > layout`.
3. Double-clique le fichier XML souhaité, puis sélectionne **Design** ou **Split** (en haut à droite de l’éditeur).
4. Dans Design, clique un élément ; dans **Attributes**, change sa taille, ses marges, sa couleur, son texte ou l’image utilisée.
5. Passe en **Code** pour ajuster précisément les proportions (`layout_weight`) et les propriétés.

## Quel fichier pour quel écran ?

| Fichier XML | À quoi sert-il ? |
|---|---|
| `activity_main.xml` | Page principale, lecteur et albums |
| `activity_settings.xml` | Réglages Bluetooth / mode voiture |
| `widget_player.xml` | Widget 4×1 normal |
| `widget_player_car.xml` | Widget 4×1 voiture |
| `widget_player_large.xml` | Widget 4×2 normal |
| `widget_player_large_car.xml` | Widget 4×2 voiture |

## Personnaliser les boutons

Les fichiers PNG se trouvent dans `app/src/main/res/drawable-nodpi/` :
- `button_album_prev.png`
- `button_track_prev.png`
- `button_play_pause.png`
- `button_track_next.png`
- `button_album_next.png`

Pour changer un bouton, remplace son PNG en conservant le **même nom**. Pour en changer la taille, clique sur l’`ImageButton` ou l’`ImageView` du layout concerné. Les IDs (`btnNext`, `wNext`, etc.) doivent être conservés car le Java les utilise pour piloter la lecture.

## Personnaliser les textes du widget 4×2

Les trois TextView `wArtist`, `wAlbum`, `wTitle` sont maintenant directement visibles dans le Designer Android Studio. Tu peux modifier `textSize`, `textColor`, `fontFamily`, alignements et marges. Les attributs `tools:text` sont des exemples visibles seulement dans le Designer : ils ne remplacent **pas** les informations réelles, envoyées par `PlayerWidgetProvider.java`.

La police intégrée se trouve dans `app/src/main/res/font/lobster_two_bold.otf` et est référencée par `@font/lobster_two_bold`.

**Attention :** les widgets Android (RemoteViews) ne supportent pas tous les composants et propriétés UI d’une Activity. Reste de préférence sur les `LinearLayout`, `TextView` et `ImageView` existants. La prévisualisation du widget dans Android Studio peut différer légèrement du lanceur du téléphone.

## Couleurs

Les couleurs principales sont centralisées dans `app/src/main/res/values/colors.xml`. Les textes de métadonnées du widget ont, pour l’instant, leurs couleurs directement dans `widget_player_large.xml` et `widget_player_large_car.xml` afin de faciliter leur modification directe.

## Mise à jour après changement

Recompile/installe l’APK. Les widgets existants peuvent être rafraîchis en changeant de piste ou en les supprimant et les ajoutant de nouveau.

**Compilation non vérifiée ici avec un SDK Android réel.**
