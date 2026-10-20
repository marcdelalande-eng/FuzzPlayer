package com.example.valveplayer;

import android.content.Context;
import android.net.Uri;
import androidx.documentfile.provider.DocumentFile;
import java.util.ArrayList;
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

    private static boolean isCover(String name) {
        if (name == null) return false;
        String n = name.toLowerCase(Locale.ROOT);
        return n.matches("(folder|cover|front|album|artwork|albumart)\\.(jpg|jpeg|png|webp)");
    }

    private static void scanFolder(DocumentFile folder, List<Track> out, Set<String> seen, int depth) {
        if (depth > 64) return; // Safety limit against malformed folder providers.
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
    private static String safe(String s) { return s == null ? "" : s; }
}
