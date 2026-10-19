package com.example.valveplayer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class LibraryScanner {
    private LibraryScanner() {}

    public static List<Track> scan(Context context) {
        List<Track> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        scanMediaStore(context, result, seen, scanCovers(context));
        for (String tree : Prefs.folders(context)) {
            try {
                DocumentFile root = DocumentFile.fromTreeUri(context, Uri.parse(tree));
                if (root != null && root.exists()) scanFolder(root, result, seen, 0);
            } catch (Exception ignored) { }
        }
        result.sort(Comparator.comparing((Track t) -> safe(t.album), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(t -> t.trackNo)
                .thenComparing(t -> safe(t.title), String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static Map<String, Uri> scanCovers(Context context) {
        Map<String, Uri> covers = new HashMap<>();
        Uri base = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.RELATIVE_PATH, MediaStore.Images.Media.DISPLAY_NAME};
        try (Cursor c = context.getContentResolver().query(base, projection, null, null, null)) {
            if (c != null) while (c.moveToNext()) {
                String name = c.getString(2);
                if (!isCover(name)) continue;
                String path = c.getString(1);
                if (path != null) covers.putIfAbsent(path, ContentUris.withAppendedId(base, c.getLong(0)));
            }
        } catch (Exception ignored) { }
        return covers;
    }

    private static boolean isCover(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.matches("(folder|cover|front|album|artwork|albumart)\\.(jpg|jpeg|png|webp)");
    }

    private static void scanMediaStore(Context context, List<Track> out, Set<String> seen, Map<String, Uri> covers) {
        Uri collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
        String[] projection = {
                MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.RELATIVE_PATH
        };
        try (Cursor c = context.getContentResolver().query(collection, projection, null, null, null)) {
            if (c == null) return;
            int id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int ti = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int ar = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int al = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int du = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
            int tr = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK);
            int dn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
            int mi = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE);
            int rp = c.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH);
            while (c.moveToNext()) {
                String display = c.getString(dn); String mime = c.getString(mi);
                if (!supported(display, mime)) continue;
                Uri uri = ContentUris.withAppendedId(collection, c.getLong(id));
                if (!seen.add(uri.toString())) continue;
                String title = value(c.getString(ti), stripExt(display));
                String artist = value(c.getString(ar), "Artiste inconnu");
                String album = value(c.getString(al), "Album inconnu");
                out.add(new Track(uri, title, artist, album, c.getLong(du), c.getInt(tr), covers.get(c.getString(rp))));
            }
        } catch (SecurityException ignored) { }
    }

    private static void scanFolder(DocumentFile folder, List<Track> out, Set<String> seen, int depth) {
        if (depth > 18) return;
        DocumentFile[] entries = folder.listFiles();
        Uri cover = null;
        for (DocumentFile f : entries) if (f.isFile() && isCover(f.getName())) { cover = f.getUri(); break; }
        for (DocumentFile f : entries) {
            if (f.isDirectory()) scanFolder(f, out, seen, depth + 1);
            else if (f.isFile() && supported(f.getName(), f.getType())) {
                Uri uri = f.getUri(); if (!seen.add(uri.toString())) continue;
                String album = folder.getName() == null ? "Dossier" : folder.getName();
                out.add(new Track(uri, stripExt(f.getName()), "Artiste inconnu", album, 0, 0, cover));
            }
        }
    }

    private static boolean supported(String name, String mime) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".flac") || n.endsWith(".aac") || n.endsWith(".ogg") || n.endsWith(".m4a")) return true;
        if (mime == null) return false;
        String m = mime.toLowerCase(Locale.ROOT);
        return m.contains("mpeg") || m.contains("wav") || m.contains("flac") || m.contains("aac") || m.contains("ogg") || m.contains("vorbis") || m.contains("opus") || m.contains("mp4");
    }
    private static String stripExt(String s) { if (s == null) return "Sans titre"; int p = s.lastIndexOf('.'); return p > 0 ? s.substring(0, p) : s; }
    private static String value(String s, String fallback) { return s == null || s.trim().isEmpty() ? fallback : s; }
    private static String safe(String s) { return s == null ? "" : s; }
}
