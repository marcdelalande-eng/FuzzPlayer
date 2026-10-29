package com.example.valveplayer;

import android.content.Intent;
import android.os.SystemClock;
import android.view.KeyEvent;
import androidx.media3.common.util.UnstableApi;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.PlaybackException;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlaybackService extends MediaSessionService {
    public static final String ACTION_COMMAND = "com.example.valveplayer.COMMAND";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_URI = "uri";
    public static final String CMD_PLAY_PAUSE = "play_pause";
    public static final String CMD_PREV = "prev";
    public static final String CMD_NEXT = "next";
    public static final String CMD_PREV_ALBUM = "prev_album";
    public static final String CMD_NEXT_ALBUM = "next_album";
    public static final String CMD_PLAY_URI = "play_uri";
    public static final String CMD_SET_SHUFFLE = "set_shuffle";
    public static final String CMD_RESCAN = "rescan";

    private ExoPlayer player;
    private MediaSession mediaSession;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private List<Track> tracks = new ArrayList<>();
    private boolean loading;
    private final Runnable widgetVuTick = new Runnable() {
        @Override public void run() {
            if (player != null && player.isPlaying()) {
                PlayerWidgetProvider.updateVu(PlaybackService.this, true);
                main.postDelayed(this, 2000);
            }
        }
    };
    private final Runnable positionSaver = new Runnable() {
        @Override public void run() {
            savePlaybackPosition();
            main.postDelayed(this, 5000);
        }
    };
    private static final long DOUBLE_PRESS_MS = 550;
    private int pendingDirection = 0;
    private long lastPress = 0;
    private final Runnable dispatchSingle = () -> {
        int dir = pendingDirection; pendingDirection = 0;
        if (dir != 0) { if (dir > 0) player.seekToNextMediaItem(); else player.seekToPreviousMediaItem(); }
    };

    @Override public void onCreate() {
        super.onCreate();
        Diagnostics.install(this);
        Diagnostics.log("SERVICE", "onCreate");
        player = new ExoPlayer.Builder(this).build();
        player.setShuffleModeEnabled(Prefs.isShuffle(this));
        main.postDelayed(positionSaver, 5000);
        mediaSession = new MediaSession.Builder(this, player).setCallback(new MediaSession.Callback() {
            @Override @UnstableApi public boolean onMediaButtonEvent(MediaSession session, MediaSession.ControllerInfo controller, Intent intent) {
                KeyEvent key = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class);
                if (key == null) return false;
                int direction = switch (key.getKeyCode()) {
                    case KeyEvent.KEYCODE_MEDIA_NEXT, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> 1;
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> -1;
                    default -> 0;
                };
                if (direction == 0) return false;
                if (key.getAction() != KeyEvent.ACTION_DOWN || key.getRepeatCount() != 0) return true;
                long now = SystemClock.uptimeMillis();
                if (pendingDirection != 0 && now - lastPress <= DOUBLE_PRESS_MS && pendingDirection == direction) {
                    main.removeCallbacks(dispatchSingle); pendingDirection = 0;
                    ensureLibrary(() -> { jumpAlbum(direction); PlayerWidgetProvider.updateAll(PlaybackService.this); });
                } else {
                    if (pendingDirection != 0) { main.removeCallbacks(dispatchSingle); dispatchSingle.run(); }
                    pendingDirection = direction; lastPress = now;
                    main.postDelayed(dispatchSingle, DOUBLE_PRESS_MS);
                }
                return true;
            }
        }).build();
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean isPlaying) {
                Diagnostics.log("PLAYER", "isPlaying=" + isPlaying + ", state=" + player.getPlaybackState());
                Log.e("FUZZ_DIAG", "isPlaying=" + isPlaying + ", state=" + player.getPlaybackState());
                Prefs.setPlaying(PlaybackService.this, isPlaying);
                main.removeCallbacks(widgetVuTick);
                PlayerWidgetProvider.updateVu(PlaybackService.this, isPlaying);
                if (isPlaying) main.postDelayed(widgetVuTick, 2000);
                savePlaybackPosition();
            }
            @Override public void onPlayerError(PlaybackException error) {
                Diagnostics.log("PLAYER", "Media3 error", error);
                Log.e("FUZZ_DIAG", "Erreur Media3", error);
            }
            @Override public void onMediaItemTransition(MediaItem item, int reason) {
                saveWidgetMetadata();
                savePlaybackPosition();
            }
            @Override public void onMediaMetadataChanged(MediaMetadata metadata) {
                saveWidgetMetadata();
            }
        });
        ensureLibrary(null);
    }

    @Override public MediaSession onGetSession(MediaSession.ControllerInfo controllerInfo) { return mediaSession; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        Diagnostics.log("SERVICE", "onStartCommand startId=" + startId + ", action=" + (intent == null ? "null" : intent.getAction()));
        Log.e("FUZZ_DIAG", "Service démarré, startId=" + startId
                + ", action=" + (intent == null ? "null" : intent.getAction()));
        if (intent != null && ACTION_COMMAND.equals(intent.getAction())) {
            String command = intent.getStringExtra(EXTRA_COMMAND);
            String uri = intent.getStringExtra(EXTRA_URI);
            if (CMD_RESCAN.equals(command)) reloadLibrary();
            else ensureLibrary(() -> execute(command, uri));
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void reloadLibrary() {
        if (loading) { main.postDelayed(this::reloadLibrary, 300); return; }
        loading = true;
        String oldUri = null;
        int index = player.getCurrentMediaItemIndex();
        if (index >= 0 && index < tracks.size()) oldUri = tracks.get(index).uri.toString();
        final String currentUri = oldUri;
        final long oldPosition = Math.max(0, player.getCurrentPosition());
        final boolean wasPlaying = player.getPlayWhenReady();
        executor.execute(() -> {
            List<Track> loaded = LibraryStore.load(this);
            Diagnostics.log("LIBRARY", "Loaded saved snapshot: " + loaded.size() + " tracks");
            main.post(() -> {
                tracks = loaded;
                List<MediaItem> items = new ArrayList<>();
                int resume = -1;
                for (int i = 0; i < loaded.size(); i++) {
                    Track t = loaded.get(i);
                    MediaMetadata metadata = new MediaMetadata.Builder()
                            .setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build();
                    items.add(new MediaItem.Builder().setUri(t.uri).setMediaMetadata(metadata).build());
                    if (currentUri != null && currentUri.equals(t.uri.toString())) resume = i;
                }
                if (items.isEmpty()) {
                    player.clearMediaItems();
                } else {
                    player.setMediaItems(items, resume >= 0 ? resume : 0, resume >= 0 ? oldPosition : 0);
                    player.prepare();
                    if (wasPlaying) player.play();
                }
                loading = false;
                PlayerWidgetProvider.updateAll(this);
            });
        });
    }

    private void ensureLibrary(Runnable done) {
        if (!tracks.isEmpty()) { if (done != null) done.run(); return; }
        if (loading) { if (done != null) main.postDelayed(() -> ensureLibrary(done), 150); return; }
        loading = true;
        executor.execute(() -> {
            List<Track> loaded = LibraryStore.load(this);
            main.post(() -> {
                tracks = loaded; loading = false;
                if (player.getMediaItemCount() == 0 && !tracks.isEmpty()) {
                    List<MediaItem> items = new ArrayList<>();
                    for (Track t : tracks) {
                        MediaMetadata meta = new MediaMetadata.Builder().setTitle(t.title).setArtist(t.artist).setAlbumTitle(t.album).build();
                        items.add(new MediaItem.Builder().setUri(t.uri).setMediaMetadata(meta).build());
                    }
                    // Capture saved progress BEFORE setMediaItems triggers a media transition callback.
                    String last = Prefs.isResumeLast(this) ? Prefs.lastUri(this) : null;
                    long position = Prefs.lastPosition(this);
                    int resumeIndex = -1;
                    if (last != null) {
                        for (int i = 0; i < tracks.size(); i++) {
                            if (last.equals(tracks.get(i).uri.toString())) {
                                resumeIndex = i;
                                break;
                            }
                        }
                    }
                    player.setMediaItems(items, resumeIndex >= 0 ? resumeIndex : 0,
                            resumeIndex >= 0 ? position : 0L);
                    player.prepare();
                }
                if (done != null) done.run();
            });
        });
    }

    private void execute(String command, String uri) {
        if (command == null || tracks.isEmpty()) return;
        switch (command) {
            case CMD_PLAY_PAUSE -> { if (player.isPlaying()) player.pause(); else player.play(); }
            case CMD_PREV -> player.seekToPreviousMediaItem();
            case CMD_NEXT -> player.seekToNextMediaItem();
            case CMD_PREV_ALBUM -> jumpAlbum(-1);
            case CMD_NEXT_ALBUM -> jumpAlbum(1);
            case CMD_PLAY_URI -> playUri(uri);
            case CMD_SET_SHUFFLE -> player.setShuffleModeEnabled(Prefs.isShuffle(this));
        }
        PlayerWidgetProvider.updateAll(this);
    }

    private void playUri(String uri) {
        if (uri == null) return;
        for (int i = 0; i < tracks.size(); i++) if (tracks.get(i).uri.toString().equals(uri)) { player.seekTo(i, 0); player.play(); return; }
    }

    private void jumpAlbum(int dir) {
        if (tracks.isEmpty()) return;
        int current = Math.max(0, player.getCurrentMediaItemIndex());
        String album = tracks.get(current).album;
        if (dir > 0) {
            for (int i = current + 1; i < tracks.size(); i++) if (!tracks.get(i).album.equals(album)) { player.seekTo(i, 0); if (player.getPlayWhenReady()) player.play(); return; }
        } else {
            int first = current; while (first > 0 && tracks.get(first - 1).album.equals(album)) first--;
            if (first == 0) { player.seekTo(0, 0); return; }
            String prev = tracks.get(first - 1).album; int target = first - 1;
            while (target > 0 && tracks.get(target - 1).album.equals(prev)) target--;
            player.seekTo(target, 0); if (player.getPlayWhenReady()) player.play();
        }
    }

    private void savePlaybackPosition() {
        if (player == null || tracks.isEmpty()) return;
        int index = player.getCurrentMediaItemIndex();
        if (index >= 0 && index < tracks.size()) {
            Prefs.savePosition(this, tracks.get(index).uri.toString(), player.getCurrentPosition());
        }
    }

    private void saveWidgetMetadata() {
        MediaMetadata meta = player.getMediaMetadata();
        Prefs.get(this).edit()
            .putString("widget_artist", meta.artist == null ? "Artiste inconnu" : meta.artist.toString())
            .putString("widget_album", meta.albumTitle == null ? "Album inconnu" : meta.albumTitle.toString())
            .putString("widget_title", meta.title == null ? "Titre inconnu" : meta.title.toString())
            .apply();
        PlayerWidgetProvider.updateAll(this);
    }

    @Override public void onUpdateNotification(MediaSession session, boolean startInForegroundRequired) {
        Log.e("FUZZ_DIAG", "Notification Media3: foregroundRequired=" + startInForegroundRequired
                + ", isPlaying=" + player.isPlaying());
        super.onUpdateNotification(session, startInForegroundRequired);
    }

    @Override public void onTaskRemoved(Intent rootIntent) {
        Diagnostics.log("SERVICE", "onTaskRemoved");
        Log.e("FUZZ_DIAG", "Service : onTaskRemoved");
        super.onTaskRemoved(rootIntent);
    }

    @Override public void onDestroy() {
        Diagnostics.log("SERVICE", "onDestroy");
        Log.e("FUZZ_DIAG", "PlaybackService.onDestroy appelé");
        main.removeCallbacks(dispatchSingle);
        main.removeCallbacks(positionSaver);
        main.removeCallbacks(widgetVuTick);
        savePlaybackPosition();
        Prefs.setPlaying(this, false);
        mediaSession.release(); player.release(); executor.shutdownNow(); super.onDestroy();
    }
}
