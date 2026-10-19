package com.example.valveplayer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class PlayerWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) updateSmall(context, manager, id);
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] smallIds = manager.getAppWidgetIds(new ComponentName(context, PlayerWidgetProvider.class));
        for (int id : smallIds) updateSmall(context, manager, id);
        int[] largeIds = manager.getAppWidgetIds(new ComponentName(context, LargePlayerWidgetProvider.class));
        for (int id : largeIds) updateLarge(context, manager, id);
    }

    private static void updateSmall(Context c, AppWidgetManager m, int id) {
        int layout = Prefs.isCarMode(c) ? R.layout.widget_player_car : R.layout.widget_player;
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        bindControls(c, v);
        m.updateAppWidget(id, v);
    }

    static void updateLarge(Context c, AppWidgetManager m, int id) {
        int layout = Prefs.isCarMode(c) ? R.layout.widget_player_large_car : R.layout.widget_player_large;
        RemoteViews v = new RemoteViews(c.getPackageName(), layout);
        bindControls(c, v);
        String artist = Prefs.get(c).getString("widget_artist", "Aucun interprète");
        String album = Prefs.get(c).getString("widget_album", "Choisissez un album");
        String title = Prefs.get(c).getString("widget_title", "Aucune piste sélectionnée");
        // Trois textes natifs : édition directe dans le Layout Editor Android Studio.
        v.setTextViewText(R.id.wArtist, "" + sanitize(artist));
        v.setTextViewText(R.id.wAlbum, "" + sanitize(album));
        v.setTextViewText(R.id.wTitle, "" + sanitize(title));
        Intent open = new Intent(c, MainActivity.class);
        PendingIntent launch = PendingIntent.getActivity(c, 700, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(R.id.wMetadata, launch);
        m.updateAppWidget(id, v);
    }

    private static String sanitize(String value) {
        return value == null || value.isBlank() ? "—" : value.replace('\n', ' ').replace('\r', ' ');
    }

    private static void bindControls(Context c, RemoteViews v) {
        bind(c, v, R.id.wPrevAlbum, PlaybackService.CMD_PREV_ALBUM, 1);
        bind(c, v, R.id.wPrev, PlaybackService.CMD_PREV, 2);
        bind(c, v, R.id.wPlay, PlaybackService.CMD_PLAY_PAUSE, 3);
        bind(c, v, R.id.wNext, PlaybackService.CMD_NEXT, 4);
        bind(c, v, R.id.wNextAlbum, PlaybackService.CMD_NEXT_ALBUM, 5);
    }

    private static void bind(Context c, RemoteViews v, int view, String cmd, int req) {
        Intent i = new Intent(c, PlaybackService.class).setAction(PlaybackService.ACTION_COMMAND).putExtra(PlaybackService.EXTRA_COMMAND, cmd);
        PendingIntent p = PendingIntent.getService(c, req, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        v.setOnClickPendingIntent(view, p);
    }
}
