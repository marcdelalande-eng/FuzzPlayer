package com.example.valveplayer;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** User-triggered MusicBrainz searches (one request per second, never automatic). */
public final class MusicBrainzLookup {
    private static long lastCall;
    private MusicBrainzLookup() {}

    public static final class Release {
        public final String id, title, artist, display;
        Release(String id, String title, String artist, String date) {
            this.id=id; this.title=title; this.artist=artist;
            this.display=title + " — " + artist + (date.isEmpty() ? "" : " (" + date + ")");
        }
    }
    public static final class Song {
        public final String title, artist;
        public final int number;
        Song(String title, String artist, int number) {
            this.title=title; this.artist=artist; this.number=number;
        }
    }

    private static synchronized JSONObject request(String address) throws Exception {
        long delay = 1100 - (System.currentTimeMillis() - lastCall);
        if (delay > 0) Thread.sleep(delay);
        lastCall = System.currentTimeMillis();
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("User-Agent", "FuzzPlayer/1.1.0 (https://github.com/marcdelalande-eng/FuzzPlayer)");
        connection.setRequestProperty("Accept", "application/json");
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new java.io.IOException("MusicBrainz HTTP " + status);
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (out.size() + n > 2 * 1024 * 1024)
                        throw new java.io.IOException("Réponse MusicBrainz trop volumineuse");
                    out.write(buffer, 0, n);
                }
                return new JSONObject(out.toString(StandardCharsets.UTF_8));
            }
        } finally { connection.disconnect(); }
    }

    public static List<Release> search(String album) throws Exception {
        String query = "release:" + "\"" + album + "\"";
        String url = "https://musicbrainz.org/ws/2/release/?query="
                + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&limit=8&fmt=json";
        JSONArray values = request(url).optJSONArray("releases");
        List<Release> results = new ArrayList<>();
        if (values == null) return results;
        for (int i=0; i<values.length(); i++) {
            JSONObject release = values.optJSONObject(i);
            if (release == null) continue;
            String id = release.optString("id", "");
            if (id.isEmpty()) continue;
            results.add(new Release(id, release.optString("title", "Sans titre"),
                    artistCredit(release.optJSONArray("artist-credit")), release.optString("date", "")));
        }
        return results;
    }

    public static List<Song> tracks(String releaseId) throws Exception {
        if (!releaseId.matches("[a-fA-F0-9-]{36}"))
            throw new java.io.IOException("Identifiant MusicBrainz incorrect");
        JSONObject release = request("https://musicbrainz.org/ws/2/release/"
                + releaseId + "?inc=recordings+artist-credits&fmt=json");
        List<Song> songs = new ArrayList<>();
        JSONArray media = release.optJSONArray("media");
        if (media == null) return songs;
        for (int i=0; i<media.length(); i++) {
            JSONObject medium = media.optJSONObject(i);
            if (medium == null) continue;
            JSONArray rows = medium.optJSONArray("tracks");
            if (rows == null) continue;
            for (int j=0; j<rows.length(); j++) {
                JSONObject track = rows.optJSONObject(j);
                if (track == null) continue;
                JSONObject recording = track.optJSONObject("recording");
                String title = track.optString("title", "");
                if (title.isEmpty() && recording != null) title = recording.optString("title", "");
                String artist = artistCredit(track.optJSONArray("artist-credit"));
                if (artist.isEmpty() && recording != null)
                    artist = artistCredit(recording.optJSONArray("artist-credit"));
                songs.add(new Song(title, artist, track.optInt("number", j+1)));
            }
        }
        return songs;
    }

    private static String artistCredit(JSONArray credits) {
        if (credits == null) return "";
        StringBuilder b = new StringBuilder();
        for (int i=0; i<credits.length(); i++) {
            Object entry = credits.opt(i);
            if (entry instanceof JSONObject) {
                JSONObject credit = (JSONObject) entry;
                JSONObject artist = credit.optJSONObject("artist");
                b.append(credit.optString("name", artist == null ? "" : artist.optString("name", "")));
                b.append(credit.optString("joinphrase", ""));
            } else if (entry instanceof String) b.append(entry);
        }
        return b.toString().trim();
    }
}
