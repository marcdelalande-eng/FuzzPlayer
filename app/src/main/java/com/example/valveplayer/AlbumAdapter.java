package com.example.valveplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AlbumAdapter extends BaseAdapter {
    public static final class Album {
        public final String name, artist;
        public final Uri cover;
        public final int firstTrack, count;
        public Album(String name, String artist, Uri cover, int firstTrack, int count) {
            this.name=name; this.artist=artist; this.cover=cover; this.firstTrack=firstTrack; this.count=count;
        }
    }

    private static final class Holder {
        ImageView image;
        TextView title, sub;
    }

    // CORRECTIF : les pochettes étaient décodées sur le thread UI à chaque affichage de ligne
    // (défilement saccadé, risque de mémoire). Elles sont maintenant décodées en arrière-plan,
    // à la taille réelle de la vignette, et mises en cache.
    private static final ExecutorService LOADER = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "FuzzPlayer-covers");
        t.setDaemon(true);
        return t;
    });
    private final LruCache<String, Bitmap> cache = new LruCache<>(48);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Context context;
    private final List<Album> albums;

    public AlbumAdapter(Context context, List<Album> albums) {this.context=context;this.albums=albums;}
    @Override public int getCount(){return albums.size();}
    @Override public Album getItem(int position){return albums.get(position);}
    @Override public long getItemId(int position){return position;}
    private int dp(int v){return (int)(v*context.getResources().getDisplayMetrics().density+.5f);}

    @Override public View getView(int position, View recycled, ViewGroup parent) {
        Album a = getItem(position);
        Holder h;
        View root;
        if (recycled == null) {
            LinearLayout row = new LinearLayout(context);
            row.setPadding(dp(12),dp(8),dp(12),dp(8)); row.setGravity(Gravity.CENTER_VERTICAL);
            h = new Holder();
            h.image = new ImageView(context); h.image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            row.addView(h.image, new LinearLayout.LayoutParams(dp(72), dp(72)));
            LinearLayout labels = new LinearLayout(context);
            labels.setOrientation(LinearLayout.VERTICAL); labels.setPadding(dp(14),0,0,0);
            h.title = new TextView(context); h.title.setTextSize(18); h.title.setTextColor(0xFFE8C67A); h.title.setMaxLines(2);
            h.sub = new TextView(context); h.sub.setTextColor(0xFFCCC6B5); h.sub.setTextSize(13);
            labels.addView(h.title); labels.addView(h.sub);
            row.addView(labels);
            row.setTag(h);
            root = row;
        } else {
            root = recycled;
            h = (Holder) root.getTag();
        }
        h.title.setText(a.name);
        h.sub.setText(a.artist + " • " + a.count + " titre(s)");
        bindCover(h.image, a.cover);
        return root;
    }

    private void bindCover(ImageView image, Uri cover) {
        final String key = cover == null ? null : cover.toString();
        image.setTag(key);
        image.setImageResource(android.R.drawable.ic_menu_gallery);
        if (key == null) return;
        Bitmap cached = cache.get(key);
        if (cached != null) { image.setImageBitmap(cached); return; }
        final int target = dp(72);
        LOADER.execute(() -> {
            Bitmap bitmap = decode(cover, target);
            if (bitmap == null) return;
            cache.put(key, bitmap);
            // La ligne a pu être recyclée pour un autre album entre-temps : on vérifie la clé.
            main.post(() -> { if (key.equals(image.getTag())) image.setImageBitmap(bitmap); });
        });
    }

    private Bitmap decode(Uri uri, int target) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                BitmapFactory.decodeStream(in, null, bounds);
            }
            int sample = 1;
            while (bounds.outWidth / (sample * 2) >= target && bounds.outHeight / (sample * 2) >= target) sample *= 2;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) return null;
                return BitmapFactory.decodeStream(in, null, options);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
