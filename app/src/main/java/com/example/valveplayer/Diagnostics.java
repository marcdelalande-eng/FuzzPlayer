package com.example.valveplayer;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Small persistent app log. Survives process crashes and is exportable through DocumentsUI. */
public final class Diagnostics {
    private static final Object LOCK = new Object();
    private static final String FILENAME = "fuzzplayer-diagnostic.txt";
    private static final long MAX_BYTES = 512 * 1024;
    private static volatile boolean installed;
    private static volatile long lastBeat;
    private static volatile boolean anrReported;
    private static Context app;

    private Diagnostics() {}

    public static void install(Context context) {
        if (installed) return;
        synchronized (LOCK) {
            if (installed) return;
            app = context.getApplicationContext();
            installed = true;
        }
        log("DIAG", "Process launched; device=" + android.os.Build.MODEL
                + ", Android=" + android.os.Build.VERSION.RELEASE);
        Thread.UncaughtExceptionHandler original = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            log("CRASH", "Uncaught exception on " + thread.getName(), error);
            if (original != null) original.uncaughtException(thread, error);
        });
        Handler handler = new Handler(Looper.getMainLooper());
        lastBeat = SystemClock.uptimeMillis();
        Runnable beat = new Runnable() {
            @Override public void run() {
                lastBeat = SystemClock.uptimeMillis();
                anrReported = false;
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(beat);
        Thread watchdog = new Thread(() -> {
            while (true) {
                try { Thread.sleep(1500); } catch (InterruptedException e) { return; }
                long delay = SystemClock.uptimeMillis() - lastBeat;
                if (delay > 5000 && !anrReported) {
                    anrReported = true;
                    StackTraceElement[] trace = Looper.getMainLooper().getThread().getStackTrace();
                    StringBuilder b = new StringBuilder("Main thread blocked for " + delay + " ms");
                    for (StackTraceElement line : trace) b.append("\n  at ").append(line);
                    log("FREEZE", b.toString());
                }
            }
        }, "FuzzPlayer-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    public static void log(String tag, String message) { log(tag, message, null); }

    public static void log(String tag, String message, Throwable error) {
        Log.e("FUZZ_DIAG", tag + ": " + message, error);
        Context c = app;
        if (c == null) return;
        String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.FRANCE).format(new Date());
        StringBuilder data = new StringBuilder(time).append(" [").append(tag).append("] ")
                .append(message).append('\n');
        if (error != null) {
            StringWriter sw = new StringWriter();
            error.printStackTrace(new PrintWriter(sw));
            data.append(sw).append('\n');
        }
        synchronized (LOCK) {
            try {
                File file = new File(c.getFilesDir(), FILENAME);
                if (file.exists() && file.length() > MAX_BYTES) {
                    // Keep file bounded so exports stay quick.
                    new FileOutputStream(file, false).close();
                }
                try (FileOutputStream out = new FileOutputStream(file, true)) {
                    out.write(data.toString().getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ignored) { }
        }
    }

    public static File getLogFile(Context context) {
        return new File(context.getFilesDir(), FILENAME);
    }
}
