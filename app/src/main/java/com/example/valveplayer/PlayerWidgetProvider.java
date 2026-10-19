package com.example.valveplayer;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
        v.setImageViewBitmap(R.id.wVuMeter, makeVuBitmap(Prefs.isPlaying(c), System.nanoTime()));
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

    /** Update only the needle; avoid rebuilding the metadata/controls every 2 seconds. */
    static void updateVu(Context c, boolean playing) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, LargePlayerWidgetProvider.class));
        if (ids.length == 0) return;
        RemoteViews v = new RemoteViews(c.getPackageName(),
                Prefs.isCarMode(c) ? R.layout.widget_player_large_car : R.layout.widget_player_large);
        v.setImageViewBitmap(R.id.wVuMeter, makeVuBitmap(playing, System.nanoTime()));
        for (int id : ids) m.partiallyUpdateAppWidget(id, v);
    }

    private static Bitmap makeVuBitmap(boolean playing, long seed) {
        final int size = 128;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.rgb(216, 195, 151));
        canvas.drawCircle(64, 64, 61, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(5);
        p.setColor(Color.rgb(107, 78, 39));
        canvas.drawCircle(64, 64, 60, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(55, 42, 27));
        p.setTextSize(23);
        p.setFakeBoldText(true);
        p.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("VU", 64, 39, p);
        for (int i = 0; i <= 8; i++) {
            double a = Math.toRadians(205 + i * 16.25);
            p.setColor(i >= 7 ? Color.rgb(172, 48, 30) : Color.rgb(84, 64, 38));
            p.setStrokeWidth(3);
            canvas.drawLine(64 + (float)Math.cos(a)*40, 86 + (float)Math.sin(a)*40,
                    64 + (float)Math.cos(a)*49, 86 + (float)Math.sin(a)*49, p);
        }
        // Decorative animation, not a measurement of decoded audio volume.
        double level = playing ? 0.18 + ((seed >>> 10) & 1023) / 1023.0 * 0.77 : 0;
        double angle = Math.toRadians(205 + 130 * level);
        p.setColor(Color.rgb(185, 45, 34));
        p.setStrokeWidth(4);
        canvas.drawLine(64, 86, 64 + (float)Math.cos(angle)*43,
                86 + (float)Math.sin(angle)*43, p);
        p.setColor(Color.rgb(45, 32, 22));
        canvas.drawCircle(64, 86, 5, p);
        return bitmap;
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
