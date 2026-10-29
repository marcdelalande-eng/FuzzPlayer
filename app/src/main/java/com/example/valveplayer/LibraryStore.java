package com.example.valveplayer;

import android.content.Context;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Persistent snapshot of the last explicitly scanned library. */
public final class LibraryStore {
    private static final String FILE_NAME = "music-library.json";
    private LibraryStore() {}

    public static boolean exists(Context context) {
        return new File(context.getFilesDir(), FILE_NAME).exists();
    }

    public static synchronized List<Track> load(Context context) {
        List<Track> tracks = new ArrayList<>();
        AtomicFile file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
        if (!file.getBaseFile().exists()) return tracks;
        try (FileInputStream input = file.openRead()) {
            byte[] bytes = input.readAllBytes();
            JSONArray array = new JSONArray(new String(bytes, StandardCharsets.UTF_8));
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                String cover = entry.optString("cover", "");
                tracks.add(new Track(
                    Uri.parse(entry.getString("uri")),
                    entry.optString("title", "Sans titre"),
                    entry.optString("artist", "Artiste inconnu"),
                    entry.optString("album", "Album inconnu"),
                    entry.optLong("duration", 0),
                    entry.optInt("trackNo", 0),
                    cover.isEmpty() ? null : Uri.parse(cover)
                ));
            }
        } catch (Exception e) {
            Diagnostics.log("LIBRARY", "Unable to load saved library", e);
        }
        return tracks;
    }

    public static synchronized boolean save(Context context, List<Track> tracks) {
        JSONArray data = new JSONArray();
        try {
            for (Track t : tracks) {
                JSONObject entry = new JSONObject();
                entry.put("uri", t.uri.toString());
                entry.put("title", t.title);
                entry.put("artist", t.artist);
                entry.put("album", t.album);
                entry.put("duration", t.duration);
                entry.put("trackNo", t.trackNo);
                entry.put("cover", t.coverUri == null ? "" : t.coverUri.toString());
                data.put(entry);
            }
            AtomicFile file = new AtomicFile(new File(context.getFilesDir(), FILE_NAME));
            FileOutputStream output = null;
            try {
                output = file.startWrite();
                output.write(data.toString().getBytes(StandardCharsets.UTF_8));
                file.finishWrite(output);
                return true;
            } catch (Exception e) {
                if (output != null) file.failWrite(output);
                throw e;
            }
        } catch (Exception e) {
            Diagnostics.log("LIBRARY", "Unable to save library", e);
            return false;
        }
    }
}
