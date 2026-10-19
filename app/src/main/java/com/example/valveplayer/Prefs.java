package com.example.valveplayer;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.HashSet;
import java.util.Set;

public final class Prefs {
    private static final String NAME = "valve_prefs";
    private Prefs() {}
    public static SharedPreferences get(Context c) { return c.getSharedPreferences(NAME, Context.MODE_PRIVATE); }
    public static boolean isAutoCar(Context c) { return get(c).getBoolean("auto_car", false); }
    public static void setAutoCar(Context c, boolean v) { get(c).edit().putBoolean("auto_car", v).apply(); }
    public static boolean isCarMode(Context c) { return get(c).getBoolean("car_mode", false); }
    public static void setCarMode(Context c, boolean v) { get(c).edit().putBoolean("car_mode", v).apply(); }
    public static Set<String> carDevices(Context c) { return new HashSet<>(get(c).getStringSet("car_devices", new HashSet<>())); }
    public static void setCarDevices(Context c, Set<String> devices) { get(c).edit().putStringSet("car_devices", new HashSet<>(devices)).apply(); }
    public static Set<String> folders(Context c) { return new HashSet<>(get(c).getStringSet("folders", new HashSet<>())); }
    public static void addFolder(Context c, String uri) { Set<String> s = folders(c); s.add(uri); get(c).edit().putStringSet("folders", s).apply(); }
    public static boolean isShuffle(Context c) { return get(c).getBoolean("shuffle_mode", false); }
    public static void setShuffle(Context c, boolean v) { get(c).edit().putBoolean("shuffle_mode", v).apply(); }
    public static boolean isResumeLast(Context c) { return get(c).getBoolean("resume_last", true); }
    public static void setResumeLast(Context c, boolean v) { get(c).edit().putBoolean("resume_last", v).apply(); }
    public static String lastUri(Context c) { return get(c).getString("last_uri", null); }
    public static long lastPosition(Context c) { return get(c).getLong("last_position", 0); }
    public static void savePosition(Context c, String uri, long position) {
        get(c).edit().putString("last_uri", uri).putLong("last_position", Math.max(0, position)).apply();
    }
    public static boolean isPlaying(Context c) { return get(c).getBoolean("playing", false); }
    public static void setPlaying(Context c, boolean v) { get(c).edit().putBoolean("playing", v).apply(); }
}
