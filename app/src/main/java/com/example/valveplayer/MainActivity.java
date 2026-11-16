package com.example.valveplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.FileInputStream;
import java.io.OutputStream;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.LinearLayout;
import android.util.Log;
import android.content.ComponentName;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import com.google.common.util.concurrent.ListenableFuture;
import android.view.View;
import android.view.WindowInsets;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_FOLDER = 8, REQ_EXPORT_LOG = 9;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Track> tracks = new ArrayList<>();
    private AlbumAdapter adapter;
    private final List<AlbumAdapter.Album> albums = new ArrayList<>();
    private TextView count;
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController mediaController;
    private VuMeterView vuMeter;
    private final Player.Listener metadataListener = new Player.Listener() {
        @Override public void onMediaMetadataChanged(MediaMetadata metadata) { refreshNowPlaying(); }
        @Override public void onMediaItemTransition(MediaItem item, int reason) { refreshNowPlaying(); }
    };
    private final Handler meterHandler = new Handler(Looper.getMainLooper());
    private final Runnable meterTick = new Runnable() {
        @Override public void run() {
            if (vuMeter != null) {
                boolean playing = mediaController != null ? mediaController.isPlaying() : Prefs.isPlaying(MainActivity.this);
                vuMeter.setPlaying(playing);
            }
            meterHandler.postDelayed(this, 120);
        }
    };

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Diagnostics.install(this);
        Diagnostics.log("ACTIVITY", "onCreate");
        setContentView(R.layout.activity_main);
        View root = findViewById(R.id.mainRoot);
        final int baseLeft = root.getPaddingLeft(), baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight(), baseBottom = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(baseLeft + bars.left, baseTop + bars.top,
                    baseRight + bars.right, baseBottom + bars.bottom);
            return insets;
        });
        root.requestApplyInsets();
        count = findViewById(R.id.libraryCount);
        vuMeter = findViewById(R.id.vuMeter);
        View drawer = findViewById(R.id.sideDrawer);
        findViewById(R.id.btnMenu).setOnClickListener(v -> drawer.setVisibility(View.VISIBLE));
        findViewById(R.id.menuScrim).setOnClickListener(v -> drawer.setVisibility(View.GONE));
        findViewById(R.id.btnCloseMenu).setOnClickListener(v -> drawer.setVisibility(View.GONE));
        findViewById(R.id.btnOptions).setOnClickListener(v -> {
            drawer.setVisibility(View.GONE);
            showOptions();
        });
        findViewById(R.id.btnEditTags).setOnClickListener(v -> {
            drawer.setVisibility(View.GONE);
            startActivity(new Intent(this, TagEditorActivity.class));
        });
        adapter = new AlbumAdapter(this, albums);
        ListView list = findViewById(R.id.listAlbums); list.setAdapter(adapter);
        list.setOnItemClickListener((parent, v, pos, id) -> openAlbum(pos));
        bind(R.id.btnPrevAlbum, PlaybackService.CMD_PREV_ALBUM); bind(R.id.btnPrev, PlaybackService.CMD_PREV); bind(R.id.btnPlay, PlaybackService.CMD_PLAY_PAUSE); bind(R.id.btnNext, PlaybackService.CMD_NEXT); bind(R.id.btnNextAlbum, PlaybackService.CMD_NEXT_ALBUM);
        loadSavedLibrary();
    }

    private void refreshNowPlaying() {
        if (mediaController == null) return;
        MediaMetadata meta = mediaController.getMediaMetadata();
        TextView title = findViewById(R.id.titleNow);
        TextView artist = findViewById(R.id.artistNow);
        title.setText(meta.title == null || meta.title.length() == 0 ? "Aucun titre" : meta.title);
        String who = meta.artist == null ? "" : meta.artist.toString();
        String album = meta.albumTitle == null ? "" : meta.albumTitle.toString();
        artist.setText(who.isEmpty() ? album : album.isEmpty() ? who : who + " • " + album);
    }

    private void bind(int id, String cmd) { findViewById(id).setOnClickListener(v -> send(cmd, null)); }
    private void send(String cmd, String uri) {
        Intent i = new Intent(this, PlaybackService.class).setAction(PlaybackService.ACTION_COMMAND).putExtra(PlaybackService.EXTRA_COMMAND, cmd);
        if (uri != null) i.putExtra(PlaybackService.EXTRA_URI, uri); startService(i);
    }
    private void playTrack(int pos) { if (pos >= 0 && pos < tracks.size()) { Track t = tracks.get(pos); send(PlaybackService.CMD_PLAY_URI, t.uri.toString()); ((TextView)findViewById(R.id.titleNow)).setText(t.title); ((TextView)findViewById(R.id.artistNow)).setText(t.artist + " • " + t.album); } }

    private void openAlbum(int albumIndex) {
        AlbumAdapter.Album album = albums.get(albumIndex);
        String[] names = new String[album.count];
        for (int i=0; i<album.count; i++) names[i] = tracks.get(album.firstTrack+i).title;
        new android.app.AlertDialog.Builder(this).setTitle(album.name)
                .setItems(names, (dialog, choice) -> playTrack(album.firstTrack+choice))
                .setPositiveButton("Lire l'album", (dialog, which) -> playTrack(album.firstTrack))
                .setNegativeButton("Fermer", null).show();
    }
    private void displayLibrary(List<Track> found) {
        tracks.clear();
        tracks.addAll(found);
        albums.clear();
        for (int i = 0; i < found.size();) {
            Track first = found.get(i);
            int end = i + 1;
            while (end < found.size() && found.get(end).album.equals(first.album)) end++;
            Uri cover = null;
            for (int k = i; k < end; k++) {
                if (found.get(k).coverUri != null) { cover = found.get(k).coverUri; break; }
            }
            albums.add(new AlbumAdapter.Album(first.album, first.artist, cover, i, end - i));
            i = end;
        }
        adapter.notifyDataSetChanged();
        count.setText(albums.size() + " album(s) • " + found.size() + " titre(s)");
    }

    private void loadSavedLibrary() {
        count.setText("Chargement de la bibliothèque enregistrée…");
        executor.execute(() -> {
            List<Track> saved = LibraryStore.load(this);
            boolean exists = LibraryStore.exists(this);
            runOnUiThread(() -> {
                displayLibrary(saved);
                if (!exists) count.setText("Bibliothèque vide. Choisissez des dossiers, puis lancez Scanner dans Options.");
                Diagnostics.log("LIBRARY", "Loaded " + saved.size() + " saved tracks");
            });
        });
    }

    private void scan() {
        if (Prefs.folders(this).isEmpty()) {
            Toast.makeText(this, "Ajoutez d'abord un dossier dans Options.", Toast.LENGTH_LONG).show();
            return;
        }
        count.setText("Scan des dossiers sélectionnés et sous-dossiers…");
        executor.execute(() -> {
            Diagnostics.log("SCAN", "Explicit user scan started");
            try {
                List<Track> found = LibraryScanner.scan(this);
                // The new scan replaces the previous snapshot, including when empty.
                boolean saved = LibraryStore.save(this, found);
                runOnUiThread(() -> {
                    if (!saved) {
                        count.setText("Erreur d'enregistrement : ancienne bibliothèque conservée.");
                        Toast.makeText(this, "Impossible d'enregistrer le nouveau scan.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    displayLibrary(found);
                    Diagnostics.log("SCAN", "Saved " + found.size() + " tracks");
                    send(PlaybackService.CMD_RESCAN, null);
                    if (found.isEmpty()) Toast.makeText(this, "Aucun fichier audio trouvé.", Toast.LENGTH_LONG).show();
                });
            } catch (Exception e) {
                Diagnostics.log("SCAN", "Scan failed; previous library preserved", e);
                runOnUiThread(() -> count.setText("Échec du scan : ancienne bibliothèque conservée."));
            }
        });
    }

    private void showOptions() {
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, pad / 2);

        TextView version = new TextView(this);
        try {
            android.content.pm.PackageInfo pkg = getPackageManager().getPackageInfo(getPackageName(), 0);
            version.setText("Version " + pkg.versionName);
        } catch (Exception ignored) { version.setText("Version indisponible"); }
        version.setTextColor(getColor(R.color.amp_dim));
        version.setPadding(0, 0, 0, pad);
        content.addView(version);

        TextView reading = new TextView(this);
        reading.setText("Mode de lecture");
        reading.setTextSize(18f);
        content.addView(reading);

        RadioGroup mode = new RadioGroup(this);
        RadioButton ordered = new RadioButton(this);
        ordered.setText("Ordre des pistes");
        ordered.setId(View.generateViewId());
        RadioButton shuffled = new RadioButton(this);
        shuffled.setText("Lecture aléatoire");
        shuffled.setId(View.generateViewId());
        mode.addView(ordered);
        mode.addView(shuffled);
        mode.check(Prefs.isShuffle(this) ? shuffled.getId() : ordered.getId());
        content.addView(mode);

        CheckBox resume = new CheckBox(this);
        resume.setText("Reprendre la dernière lecture");
        resume.setChecked(Prefs.isResumeLast(this));
        content.addView(resume);

        TextView library = new TextView(this);
        library.setText("Bibliothèque");
        library.setTextSize(18f);
        library.setPadding(0, pad / 2, 0, 0);
        content.addView(library);

        android.widget.Button scanButton = new android.widget.Button(this);
        scanButton.setText("Scanner les dossiers");
        content.addView(scanButton);

        android.widget.Button folderButton = new android.widget.Button(this);
        folderButton.setText("Ajouter un dossier");
        content.addView(folderButton);

        android.widget.Button manageFolders = new android.widget.Button(this);
        manageFolders.setText("Voir / retirer les dossiers");
        content.addView(manageFolders);

        android.widget.Button carButton = new android.widget.Button(this);
        carButton.setText("Voiture / Bluetooth");
        content.addView(carButton);

        android.widget.Button logButton = new android.widget.Button(this);
        logButton.setText("Exporter le journal de diagnostic");
        content.addView(logButton);

        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.addView(content);
        android.app.AlertDialog dialog = new android.app.AlertDialog.Builder(this)
                .setTitle("Options FuzzPlayer")
                .setView(scroll)
                .setNegativeButton("Fermer", null)
                .setPositiveButton("Enregistrer", (d, which) -> {
                    Prefs.setShuffle(this, mode.getCheckedRadioButtonId() == shuffled.getId());
                    Prefs.setResumeLast(this, resume.isChecked());
                    send(PlaybackService.CMD_SET_SHUFFLE, null);
                    Toast.makeText(this, "Options enregistrées", Toast.LENGTH_SHORT).show();
                }).create();

        scanButton.setOnClickListener(v -> {
            saveOptions(mode, shuffled.getId(), resume);
            dialog.dismiss();
            scan();
        });
        folderButton.setOnClickListener(v -> {
            saveOptions(mode, shuffled.getId(), resume);
            dialog.dismiss();
            chooseFolder();
        });
        manageFolders.setOnClickListener(v -> {
            dialog.dismiss();
            showFolders();
        });
        carButton.setOnClickListener(v -> {
            saveOptions(mode, shuffled.getId(), resume);
            dialog.dismiss();
            startActivity(new Intent(this, CarSettingsActivity.class));
        });
        logButton.setOnClickListener(v -> {
            dialog.dismiss();
            exportLog();
        });
        dialog.show();
    }

    private void saveOptions(RadioGroup mode, int shuffleId, CheckBox resume) {
        Prefs.setShuffle(this, mode.getCheckedRadioButtonId() == shuffleId);
        Prefs.setResumeLast(this, resume.isChecked());
        send(PlaybackService.CMD_SET_SHUFFLE, null);
    }

    private void showFolders() {
        java.util.List<String> folders = new java.util.ArrayList<>(Prefs.folders(this));
        java.util.Collections.sort(folders);
        if (folders.isEmpty()) {
            new android.app.AlertDialog.Builder(this).setTitle("Dossiers à scanner")
                    .setMessage("Aucun dossier sélectionné.")
                    .setPositiveButton("Ajouter", (d, w) -> chooseFolder())
                    .setNegativeButton("Fermer", null).show();
            return;
        }
        String[] names = new String[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            Uri uri = Uri.parse(folders.get(i));
            String id = android.provider.DocumentsContract.getTreeDocumentId(uri);
            names[i] = id == null ? folders.get(i) : id;
        }
        new android.app.AlertDialog.Builder(this).setTitle("Dossiers à scanner")
                .setItems(names, (d, index) -> {
                    String value = folders.get(index);
                    new android.app.AlertDialog.Builder(this)
                            .setTitle("Retirer ce dossier ?")
                            .setMessage(names[index] + "\n\nLes morceaux déjà enregistrés resteront disponibles jusqu'au prochain scan.")
                            .setNegativeButton("Annuler", (x, y) -> showFolders())
                            .setPositiveButton("Retirer", (x, y) -> {
                                Prefs.removeFolder(this, value);
                                Diagnostics.log("FOLDER", "Removed folder from future scans");
                                showFolders();
                            }).show();
                })
                .setPositiveButton("Ajouter", (d, w) -> chooseFolder())
                .setNegativeButton("Fermer", null).show();
    }

    private void exportLog() {
        Diagnostics.log("USER", "Export log requested");
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, "FuzzPlayer-diagnostic.txt");
        startActivityForResult(intent, REQ_EXPORT_LOG);
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_FOLDER);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT_LOG && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri destination = data.getData();
            executor.execute(() -> {
                try (FileInputStream input = new FileInputStream(Diagnostics.getLogFile(this));
                     OutputStream output = getContentResolver().openOutputStream(destination, "w")) {
                    if (output == null) throw new java.io.IOException("Cannot open selected destination");
                    byte[] buffer = new byte[8192];
                    int n;
                    while ((n = input.read(buffer)) != -1) output.write(buffer, 0, n);
                    runOnUiThread(() -> Toast.makeText(this, "Journal exporté", Toast.LENGTH_LONG).show());
                } catch (Exception e) {
                    Diagnostics.log("EXPORT", "Failed to export log", e);
                    runOnUiThread(() -> Toast.makeText(this, "Export impossible : " + e.getMessage(), Toast.LENGTH_LONG).show());
                }
            });
            return;
        }
        if (requestCode == REQ_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData(); int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags); } catch (Exception ignored) { }
            Prefs.addFolder(this, uri.toString());
            Toast.makeText(this, "Dossier ajouté. Lancez Scanner depuis Options pour actualiser la bibliothèque.", Toast.LENGTH_LONG).show();
        }
    }
    @Override protected void onRestart() {
        super.onRestart();
        loadSavedLibrary(); // Refresh displayed tags after returning from the editor.
    }

    @Override protected void onStart() {
        super.onStart();
        SessionToken token = new SessionToken(this, new ComponentName(this, PlaybackService.class));
        controllerFuture = new MediaController.Builder(this, token).buildAsync();
        final ListenableFuture<MediaController> future = controllerFuture;
        future.addListener(() -> {
            try {
                MediaController connected = future.get();
                if (controllerFuture == future && !isFinishing()) {
                    mediaController = connected;
                    connected.addListener(metadataListener);
                    refreshNowPlaying();
                    Diagnostics.log("CONTROLLER", "Connected");
                    Log.e("FUZZ_DIAG", "MediaController connecté");
                } else {
                    connected.release();
                }
            } catch (Exception e) {
                Log.e("FUZZ_DIAG", "Connexion MediaController impossible", e);
            }
        }, getMainExecutor());
    }

    @Override protected void onStop() {
        Diagnostics.log("ACTIVITY", "onStop");
        Log.e("FUZZ_DIAG", "MainActivity.onStop");
        if (controllerFuture != null) {
            if (mediaController != null) mediaController.removeListener(metadataListener);
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
            mediaController = null;
        }
        super.onStop();
    }

    @Override protected void onPause() {
        super.onPause();
        Diagnostics.log("ACTIVITY", "onPause");
        Log.e("FUZZ_DIAG", "MainActivity.onPause");
        meterHandler.removeCallbacks(meterTick);
    }

    @Override protected void onResume() {
        super.onResume();
        Diagnostics.log("ACTIVITY", "onResume");
        Log.e("FUZZ_DIAG", "MainActivity.onResume");
        meterHandler.removeCallbacks(meterTick);
        meterHandler.post(meterTick);
    }

    @Override public void onBackPressed() {
        View drawer = findViewById(R.id.sideDrawer);
        if (drawer != null && drawer.getVisibility() == View.VISIBLE) drawer.setVisibility(View.GONE);
        else super.onBackPressed();
    }

    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
