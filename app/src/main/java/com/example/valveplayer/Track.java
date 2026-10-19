package com.example.valveplayer;

import android.net.Uri;

public final class Track {
    public final Uri uri;
    public final String title;
    public final String artist;
    public final String album;
    public final long duration;
    public final int trackNo;
    public final Uri coverUri;

    public Track(Uri uri, String title, String artist, String album, long duration, int trackNo) {
        this(uri, title, artist, album, duration, trackNo, null);
    }

    public Track(Uri uri, String title, String artist, String album, long duration, int trackNo, Uri coverUri) {
        this.coverUri = coverUri;
        this.uri = uri; this.title = title; this.artist = artist; this.album = album; this.duration = duration; this.trackNo = trackNo;
    }
}
