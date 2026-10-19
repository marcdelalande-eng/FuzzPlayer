package com.example.valveplayer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.util.List;

public final class AlbumAdapter extends BaseAdapter {
    public static final class Album {
        public final String name, artist;
        public final Uri cover;
        public final int firstTrack, count;
        public Album(String name, String artist, Uri cover, int firstTrack, int count) {
            this.name=name; this.artist=artist; this.cover=cover; this.firstTrack=firstTrack; this.count=count;
        }
    }
    private final Context context;
    private final List<Album> albums;
    public AlbumAdapter(Context context, List<Album> albums) {this.context=context;this.albums=albums;}
    @Override public int getCount(){return albums.size();}
    @Override public Album getItem(int position){return albums.get(position);}
    @Override public long getItemId(int position){return position;}
    private int dp(int v){return (int)(v*context.getResources().getDisplayMetrics().density+.5f);}
    @Override public View getView(int position, View recycled, ViewGroup parent) {
        Album a=getItem(position);
        LinearLayout root=new LinearLayout(context); root.setPadding(dp(12),dp(8),dp(12),dp(8)); root.setGravity(Gravity.CENTER_VERTICAL);
        ImageView image=new ImageView(context); image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(image,new LinearLayout.LayoutParams(dp(72),dp(72)));
        image.setImageResource(android.R.drawable.ic_menu_gallery);
        if(a.cover!=null) try(InputStream stream=context.getContentResolver().openInputStream(a.cover)) {
            BitmapFactory.Options options=new BitmapFactory.Options(); options.inSampleSize=4; Bitmap bitmap=BitmapFactory.decodeStream(stream,null,options); if(bitmap!=null) image.setImageBitmap(bitmap);
        } catch(Exception ignored) { }
        LinearLayout labels=new LinearLayout(context);labels.setOrientation(LinearLayout.VERTICAL);labels.setPadding(dp(14),0,0,0);
        TextView title=new TextView(context);title.setText(a.name);title.setTextSize(18);title.setTextColor(0xFFE8C67A);title.setMaxLines(2);
        TextView sub=new TextView(context);sub.setText(a.artist+" • "+a.count+" titre(s)");sub.setTextColor(0xFFCCC6B5);sub.setTextSize(13);
        labels.addView(title);labels.addView(sub);root.addView(labels);
        return root;
    }
}
