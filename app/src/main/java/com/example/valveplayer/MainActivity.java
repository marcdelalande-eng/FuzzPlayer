package com.example.valveplayer;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_FOLDER = 8;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<Track> tracks = new ArrayList<>();
    private AlbumAdapter adapter;
    private final List<AlbumAdapter.Album> albums = new ArrayList<>();
    private TextView count;
    private ListenableFuture<MediaController> controllerFuture;
    private MediaController mediaController;
    private VuMeterView vuMeter;
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
        super.onCreate(b); setContentView(R.layout.activity_main);
        count = findViewById(R.id.libraryCount);
        vuMeter = findViewById(R.id.vuMeter);
        findViewById(R.id.btnOptions).setOnClickListener(v -> showOptions());
        adapter = new AlbumAdapter(this, albums);
        ListView list = findViewById(R.id.listAlbums); list.setAdapter(adapter);
        list.setOnItemClickListener((parent, v, pos, id) -> openAlbum(pos));
        bind(R.id.btnPrevAlbum, PlaybackService.CMD_PREV_ALBUM); bind(R.id.btnPrev, PlaybackService.CMD_PREV); bind(R.id.btnPlay, PlaybackService.CMD_PLAY_PAUSE); bind(R.id.btnNext, PlaybackService.CMD_NEXT); bind(R.id.btnNextAlbum, PlaybackService.CMD_NEXT_ALBUM);
        if (Prefs.folders(this).isEmpty()) {
            count.setText("Choisissez un ou plusieurs dossiers de musique.");
            chooseFolder();
        } else scan();
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
    private void scan() {
        if (Prefs.folders(this).isEmpty()) {
            count.setText("Aucun dossier choisi. Sélectionnez un dossier de musique.");
            chooseFolder();
            return;
        }
        count.setText("Scan des dossiers sélectionnés et sous-dossiers…");
        executor.execute(() -> {
            List<Track> found = LibraryScanner.scan(this);
            runOnUiThread(() -> {
                tracks.clear(); tracks.addAll(found); albums.clear();
                for(int i=0; i<found.size();) {
                    Track first=found.get(i); int end=i+1;
                    while(end<found.size() && found.get(end).album.equals(first.album)) end++;
                    android.net.Uri cover=null;
                    for(int k=i;k<end;k++) if(found.get(k).coverUri!=null) {cover=found.get(k).coverUri;break;}
                    albums.add(new AlbumAdapter.Album(first.album,first.artist,cover,i,end-i));i=end;
                }
                adapter.notifyDataSetChanged();count.setText(albums.size()+" album(s) • "+found.size()+" titre(s)");
                if (found.isEmpty()) Toast.makeText(this, "Aucun fichier audio trouvé dans les dossiers sélectionnés.", Toast.LENGTH_LONG).show();
            });
        });
    }

    private void showOptions() {
        int pad = (int) (18 * getResources().getDisplayMetrics().density);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(pad, pad / 2, pad, pad, pad / 2);

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

        android.widget.Button carButton = new android.widget.Button(this);
        carButton.setText("Voiture / Bluetooth");
        content.addView(carButton);

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
        carButton.setOnClickListener(v -> {
            saveOptions(mode, shuffled.getId(), resume);
            dialog.dismiss();
            startActivity(new Intent(this, CarSettingsActivity.class));
        });
        dialog.show();
    }

    private void saveOptions(RadioGroup mode, int shuffleId, CheckBox resume) {
        Prefs.setShuffle(this, mode.getCheckedRadioButtonId() == shuffleId);
        Prefs.setResumeLast(this, resume.isChecked());
        send(PlaybackService.CMD_SET_SHUFFLE, null);
    }

    private void chooseFolder() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i, REQ_FOLDER);
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FOLDER && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData(); int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try { getContentResolver().takePersistableUriPermission(uri, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception ignored) { }
            Prefs.addFolder(this, uri.toString());
            scan();
            send(PlaybackService.CMD_RESCAN, null);
        }
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
        Log.e("FUZZ_DIAG", "MainActivity.onStop");
        if (controllerFuture != null) {
            MediaController.releaseFuture(controllerFuture);
            controllerFuture = null;
            mediaController = null;
        }
        super.onStop();
    }

    @Override protected void onPause() {
        super.onPause();
        Log.e("FUZZ_DIAG", "MainActivity.onPause");
        meterHandler.removeCallbacks(meterTick);
    }

    @Override protected void onResume() {
        super.onResume();
        Log.e("FUZZ_DIAG", "MainActivity.onResume");
        meterHandler.removeCallbacks(meterTick);
        meterHandler.post(meterTick);
    }

    @Override protected void onDestroy() { executor.shutdownNow(); super.onDestroy(); }
}
