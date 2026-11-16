package com.example.valveplayer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Manual MP3 tagging by track or complete source directory, with optional MusicBrainz preview. */
public class TagEditorActivity extends Activity {
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final List<Track> library = new ArrayList<>();
    private LinearLayout list;
    private TextView status;
    private boolean busy;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        Diagnostics.install(this);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(20, 24, 20, 20);
        page.setBackgroundColor(getColor(R.color.amp_black));
        TextView heading = new TextView(this);
        heading.setText("Édition des tags");
        heading.setTextSize(23);
        heading.setTextColor(getColor(R.color.amp_gold));
        page.addView(heading);
        TextView help = new TextView(this);
        help.setText("Modification réelle des tags MP3 (ID3v2.3/v2.4). Choisissez un répertoire pour modifier un album entier ou un titre. Pour les anciens dossiers, redonnez l'autorisation d'écriture depuis Options → Ajouter un dossier.");
        help.setTextColor(getColor(R.color.amp_cream));
        help.setPadding(0, 12, 0, 12);
        page.addView(help);
        status = new TextView(this);
        status.setTextColor(getColor(R.color.amp_dim));
        page.addView(status);
        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        Button back = new Button(this);
        back.setText("Retour");
        back.setOnClickListener(v -> finish());
        page.addView(back);
        setContentView(page);
        reload();
    }

    private void reload() {
        status.setText("Chargement de la bibliothèque enregistrée…");
        worker.execute(() -> {
            List<Track> saved = LibraryStore.load(this);
            runOnUiThread(() -> {
                library.clear();
                library.addAll(saved);
                showDirectories();
            });
        });
    }

    private static String folderKey(Track t) {
        try {
            String id = DocumentsContract.getDocumentId(t.uri);
            int slash = id.lastIndexOf('/');
            return slash >= 0 ? id.substring(0, slash) : id;
        } catch (Exception e) {
            String id = t.uri.toString();
            int slash = id.lastIndexOf('/');
            return slash >= 0 ? id.substring(0, slash) : id;
        }
    }

    private Map<String, List<Integer>> directories() {
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < library.size(); i++) {
            String key = folderKey(library.get(i));
            groups.computeIfAbsent(key, unused -> new ArrayList<>()).add(i);
        }
        return groups;
    }

    private void showDirectories() {
        list.removeAllViews();
        Map<String, List<Integer>> dirs = directories();
        status.setText(dirs.size() + " répertoire(s), " + library.size() + " titre(s).");
        for (Map.Entry<String, List<Integer>> entry : dirs.entrySet()) {
            List<Integer> indices = new ArrayList<>(entry.getValue());
            Track example = library.get(indices.get(0));
            Button button = new Button(this);
            String folder = entry.getKey();
            int slash = folder.lastIndexOf('/');
            button.setAllCaps(false);
            button.setText((slash < 0 ? folder : folder.substring(slash + 1)) + " — "
                    + example.album + " (" + indices.size() + " titres)");
            button.setOnClickListener(v -> openDirectory(indices));
            list.addView(button);
        }
    }

    private void openDirectory(List<Integer> indices) {
        if (busy) return;
        String[] actions = {"Modifier l'album / le répertoire", "Modifier un seul titre", "Chercher les tags sur MusicBrainz"};
        new AlertDialog.Builder(this).setTitle("Édition")
                .setItems(actions, (d, which) -> {
                    if (which == 0) editAlbum(indices);
                    else if (which == 1) selectTrack(indices);
                    else lookupMusicBrainz(indices);
                }).setNegativeButton("Annuler", null).show();
    }

    private EditText field(LinearLayout box, String hint, String value) {
        EditText edit = new EditText(this);
        edit.setSingleLine(true);
        edit.setHint(hint);
        edit.setText(value == null ? "" : value);
        box.addView(edit);
        return edit;
    }

    private LinearLayout form() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(24, 8, 24, 8);
        return box;
    }

    private void editAlbum(List<Integer> indices) {
        Track first = library.get(indices.get(0));
        LinearLayout box = form();
        EditText artist = field(box, "Artiste pour tous les titres", first.artist);
        EditText album = field(box, "Album pour tous les titres", first.album);
        new AlertDialog.Builder(this).setTitle("Modifier " + indices.size() + " titres MP3")
                .setMessage("Ces valeurs seront appliquées à tous les MP3 de ce répertoire. Les titres individuels seront conservés.")
                .setView(box).setNegativeButton("Annuler", null)
                .setPositiveButton("Vérifier", (d, w) -> {
                    List<Update> changes = new ArrayList<>();
                    for (int index : indices) {
                        Track old = library.get(index);
                        changes.add(new Update(index, old.title, artist.getText().toString().trim(),
                                album.getText().toString().trim(), old.trackNo));
                    }
                    confirm(changes);
                }).show();
    }

    private void selectTrack(List<Integer> indices) {
        String[] labels = new String[indices.size()];
        for (int i = 0; i < indices.size(); i++) labels[i] = library.get(indices.get(i)).title;
        new AlertDialog.Builder(this).setTitle("Choisir un titre")
                .setItems(labels, (d, which) -> editTrack(indices.get(which)))
                .setNegativeButton("Annuler", null).show();
    }

    private void editTrack(int index) {
        Track old = library.get(index);
        LinearLayout box = form();
        EditText title = field(box, "Titre", old.title);
        EditText artist = field(box, "Artiste", old.artist);
        EditText album = field(box, "Album", old.album);
        new AlertDialog.Builder(this).setTitle("Modifier le titre").setView(box)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Vérifier", (d, w) -> {
                    List<Update> changes = new ArrayList<>();
                    changes.add(new Update(index, title.getText().toString().trim(),
                            artist.getText().toString().trim(), album.getText().toString().trim(), old.trackNo));
                    confirm(changes);
                }).show();
    }

    private static final class Update {
        final int index, number;
        final String title, artist, album;
        Update(int index, String title, String artist, String album, int number) {
            this.index=index; this.title=title; this.artist=artist; this.album=album; this.number=number;
        }
    }

    private void confirm(List<Update> updates) {
        if (updates.isEmpty()) return;
        StringBuilder preview = new StringBuilder();
        int unsupported = 0;
        for (Update u : updates) {
            Track old = library.get(u.index);
            String name = old.uri.getLastPathSegment();
            if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp3")) {
                unsupported++;
                continue;
            }
            if (preview.length() < 2500) preview.append(old.title).append(" → ")
                    .append(u.title).append(" / ").append(u.artist)
                    .append(" / ").append(u.album).append('\n');
        }
        if (preview.length() == 0) {
            new AlertDialog.Builder(this).setMessage("Aucun MP3 modifiable dans cette sélection. FLAC, M4A et les autres formats ne sont pas modifiés.").setPositiveButton("OK", null).show();
            return;
        }
        final int skipped = unsupported;
        new AlertDialog.Builder(this).setTitle("Confirmer l'écriture des tags")
                .setMessage("Les fichiers MP3 originaux seront modifiés. Sauvegardez vos fichiers avant cette opération et arrêtez la lecture. "
                        + (skipped > 0 ? skipped + " fichier(s) non MP3 ignoré(s).\n\n" : "\n\n") + preview)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Modifier les MP3", (d, w) -> apply(updates))
                .show();
    }

    private void apply(List<Update> updates) {
        if (busy) return;
        busy = true;
        status.setText("Écriture des tags en cours…");
        worker.execute(() -> {
            int written = 0, skipped = 0, errors = 0;
            StringBuilder failures = new StringBuilder();
            for (Update u : updates) {
                Track old = library.get(u.index);
                try {
                    String name = old.uri.getLastPathSegment();
                    if (name == null || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".mp3")) {
                        skipped++;
                        continue;
                    }
                    Id3TagWriter.write(this, old, u.title, u.artist, u.album, u.number);
                    library.set(u.index, new Track(old.uri, u.title, u.artist, u.album,
                            old.duration, u.number, old.coverUri));
                    written++;
                } catch (Exception e) {
                    errors++;
                    Diagnostics.log("TAG", "Failed editing MP3", e);
                    if (failures.length() < 500) failures.append(e.getMessage()).append('\n');
                }
            }
            boolean saved = LibraryStore.save(this, library);
            int success = written, omitted = skipped, failed = errors;
            runOnUiThread(() -> {
                busy = false;
                showDirectories();
                status.setText(success + " MP3 modifié(s), " + omitted + " ignoré(s), " + failed + " échec(s).");
                if (saved) {
                    Intent intent = new Intent(this, PlaybackService.class)
                            .setAction(PlaybackService.ACTION_COMMAND)
                            .putExtra(PlaybackService.EXTRA_COMMAND, PlaybackService.CMD_RESCAN);
                    try { startService(intent); } catch (Exception e) { Diagnostics.log("TAG", "Player refresh unavailable", e); }
                } else Toast.makeText(this, "Sauvegarde de l'index impossible.", Toast.LENGTH_LONG).show();
                if (failed > 0) new AlertDialog.Builder(this).setTitle("Échec de modification")
                        .setMessage(failures.toString() + "\nVérifiez l'autorisation d'écriture du dossier.")
                        .setPositiveButton("OK", null).show();
            });
        });
    }

    private void lookupMusicBrainz(List<Integer> indices) {
        if (busy) return;
        busy = true;
        status.setText("Recherche MusicBrainz…");
        Track sample = library.get(indices.get(0));
        worker.execute(() -> {
            try {
                List<MusicBrainzLookup.Release> releases = MusicBrainzLookup.search(sample.album);
                runOnUiThread(() -> {
                    busy = false;
                    if (releases.isEmpty()) {
                        status.setText("Aucun album correspondant sur MusicBrainz.");
                        return;
                    }
                    String[] choices = new String[releases.size()];
                    for (int i=0; i<releases.size(); i++) choices[i] = releases.get(i).display;
                    new AlertDialog.Builder(this).setTitle("Choisissez l'édition exacte")
                            .setItems(choices, (d, selected) -> lookupRelease(releases.get(selected), indices))
                            .setNegativeButton("Annuler", null).show();
                });
            } catch (Exception e) {
                Diagnostics.log("MUSICBRAINZ", "Search failure", e);
                runOnUiThread(() -> {busy=false; status.setText("Recherche impossible : " + e.getMessage());});
            }
        });
    }

    private void lookupRelease(MusicBrainzLookup.Release release, List<Integer> indices) {
        if (busy) return;
        busy = true;
        status.setText("Chargement des titres MusicBrainz…");
        worker.execute(() -> {
            try {
                List<MusicBrainzLookup.Song> songs = MusicBrainzLookup.tracks(release.id);
                runOnUiThread(() -> {
                    busy = false;
                    if (songs.size() != indices.size()) {
                        new AlertDialog.Builder(this).setTitle("Correspondance non certaine")
                                .setMessage("MusicBrainz : " + songs.size() + " pistes ; répertoire : "
                                        + indices.size() + ". Aucune modification automatique.")
                                .setPositiveButton("OK", null).show();
                        return;
                    }
                    List<Update> changes = new ArrayList<>();
                    for (int i = 0; i < songs.size(); i++) {
                        MusicBrainzLookup.Song song = songs.get(i);
                        changes.add(new Update(indices.get(i), song.title,
                                song.artist.isEmpty() ? release.artist : song.artist,
                                release.title, song.number));
                    }
                    confirm(changes);
                });
            } catch (Exception e) {
                Diagnostics.log("MUSICBRAINZ", "Release lookup failed", e);
                runOnUiThread(() -> {busy=false; status.setText("MusicBrainz : " + e.getMessage());});
            }
        });
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
