package com.example.valveplayer;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** MP3 ID3v2.3/v2.4 editing. Keeps audio and untouched ID3 frames (including cover art). */
public final class Id3TagWriter {
    private static final long MAX_TAG = 16L * 1024 * 1024;
    private Id3TagWriter() {}

    public static void write(Context context, Track track, String title, String artist,
                             String album, int trackNumber) throws Exception {
        String path = track.uri.getLastPathSegment();
        if (path == null || !path.toLowerCase(java.util.Locale.ROOT).endsWith(".mp3"))
            throw new IOException("Écriture disponible uniquement pour les fichiers MP3");
        File backup = File.createTempFile("fuzz-tag-", ".mp3", context.getCacheDir());
        try {
            try (InputStream in = context.getContentResolver().openInputStream(track.uri);
                 OutputStream out = new FileOutputStream(backup)) {
                if (in == null) throw new IOException("Fichier illisible");
                copy(in, out);
            }
            long oldStart = 0;
            int version = 3;
            byte[] preserved = new byte[0];
            try (RandomAccessFile source = new RandomAccessFile(backup, "r")) {
                if (source.length() < 4) throw new IOException("MP3 vide");
                byte[] header = new byte[10];
                if (source.length() >= 10) source.readFully(header);
                if (header[0] == 'I' && header[1] == 'D' && header[2] == '3') {
                    version = header[3] & 255;
                    if (version != 3 && version != 4)
                        throw new IOException("ID3 version " + version + " non prise en charge");
                    if ((header[5] & 0xC0) != 0 || (header[5] & 0x40) != 0)
                        throw new IOException("Tag ID3 compressé / étendu non pris en charge");
                    long size = synchsafe(header, 6);
                    if (size > MAX_TAG || size + 10 > source.length())
                        throw new IOException("En-tête ID3 invalide");
                    oldStart = size + 10 + ((version == 4 && (header[5] & 0x10) != 0) ? 10 : 0);
                    ByteArrayOutputStream existing = new ByteArrayOutputStream();
                    int pos = 10;
                    Set<String> replacements = new HashSet<>(Arrays.asList("TIT2", "TPE1", "TALB", "TRCK"));
                    while (pos + 10 <= 10 + size) {
                        source.seek(pos);
                        byte[] frame = new byte[10];
                        source.readFully(frame);
                        if (frame[0] == 0) break;
                        String key = new String(frame, 0, 4, StandardCharsets.ISO_8859_1);
                        if (!key.matches("[A-Z0-9]{4}"))
                            throw new IOException("Trame ID3 inconnue");
                        int frameSize = version == 4 ? synchsafe(frame, 4) : bigEndian(frame, 4);
                        if (frameSize < 0 || pos + 10L + frameSize > 10 + size)
                            throw new IOException("Trame ID3 endommagée");
                        if (!replacements.contains(key)) {
                            existing.write(frame);
                            byte[] data = new byte[8192];
                            int remaining = frameSize;
                            while (remaining > 0) {
                                int n = Math.min(remaining, data.length);
                                source.readFully(data, 0, n);
                                existing.write(data, 0, n);
                                remaining -= n;
                            }
                        }
                        pos += 10 + frameSize;
                    }
                    preserved = existing.toByteArray();
                }
            }

            ByteArrayOutputStream frames = new ByteArrayOutputStream();
            frames.write(preserved);
            addFrame(frames, "TIT2", title, version);
            addFrame(frames, "TPE1", artist, version);
            addFrame(frames, "TALB", album, version);
            if (trackNumber > 0) addFrame(frames, "TRCK", Integer.toString(trackNumber), version);
            if (frames.size() > MAX_TAG) throw new IOException("Métadonnées trop volumineuses");

            byte[] tagHeader = new byte[]{'I','D','3', (byte)version, 0, 0, 0, 0, 0, 0};
            byte[] sizeBytes = encodeSynchsafe(frames.size());
            System.arraycopy(sizeBytes, 0, tagHeader, 6, 4);

            // Save a full original copy first; attempt restoration if the content provider fails.
            try {
                writeDocument(context, track.uri, backup, oldStart, tagHeader, frames.toByteArray());
            } catch (Exception failure) {
                try (ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(track.uri, "rwt");
                     InputStream in = new FileInputStream(backup);
                     OutputStream out = new FileOutputStream(fd.getFileDescriptor())) {
                    copy(in, out);
                } catch (Exception restore) {
                    failure.addSuppressed(restore);
                }
                throw failure;
            }
        } finally {
            if (!backup.delete()) backup.deleteOnExit();
        }
    }

    private static void writeDocument(Context context, Uri uri, File original, long skip,
                                      byte[] header, byte[] frames) throws Exception {
        try (ParcelFileDescriptor fd = context.getContentResolver().openFileDescriptor(uri, "rwt")) {
            if (fd == null) throw new IOException("Accès en écriture refusé. Re-sélectionnez le dossier.");
            try (OutputStream out = new FileOutputStream(fd.getFileDescriptor());
                 InputStream in = new FileInputStream(original)) {
                out.write(header);
                out.write(frames);
                long remaining = skip;
                while (remaining > 0) {
                    long moved = in.skip(remaining);
                    if (moved <= 0) throw new IOException("Fichier source tronqué");
                    remaining -= moved;
                }
                copy(in, out);
                out.flush();
            }
        }
    }

    private static void addFrame(ByteArrayOutputStream out, String id, String value, int version) throws IOException {
        if (value == null || value.isEmpty()) return;
        byte[] text;
        int encoding;
        if (version == 3) {
            text = value.getBytes(StandardCharsets.UTF_16);
            encoding = 1;
        } else {
            text = value.getBytes(StandardCharsets.UTF_8);
            encoding = 3;
        }
        int frameSize = text.length + 1;
        out.write(id.getBytes(StandardCharsets.ISO_8859_1));
        if (version == 4) out.write(encodeSynchsafe(frameSize));
        else out.write(new byte[]{(byte)(frameSize >>> 24), (byte)(frameSize >>> 16),
                                  (byte)(frameSize >>> 8), (byte)frameSize});
        out.write(0); out.write(0);
        out.write(encoding);
        out.write(text);
    }
    private static int synchsafe(byte[] bytes, int offset) {
        return ((bytes[offset] & 127) << 21) | ((bytes[offset + 1] & 127) << 14)
                | ((bytes[offset + 2] & 127) << 7) | (bytes[offset + 3] & 127);
    }
    private static int bigEndian(byte[] b, int off) {
        return ((b[off] & 255) << 24) | ((b[off+1] & 255) << 16)
                | ((b[off+2] & 255) << 8) | (b[off+3] & 255);
    }
    private static byte[] encodeSynchsafe(int n) {
        return new byte[]{(byte)((n >>> 21) & 127), (byte)((n >>> 14) & 127),
                (byte)((n >>> 7) & 127), (byte)(n & 127)};
    }
    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[65536];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
    }
}
