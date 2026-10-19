package com.example.valveplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

/** Decorative analog VU animation: motion indicates playback, not decoded audio amplitude. */
public class VuMeterView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private float level = 0f;
    private boolean playing = false;

    public VuMeterView(Context context) { super(context); }
    public VuMeterView(Context context, AttributeSet attrs) { super(context, attrs); }
    public VuMeterView(Context context, AttributeSet attrs, int style) { super(context, attrs, style); }

    public void setPlaying(boolean active) {
        playing = active;
        float target = active ? 0.15f + random.nextFloat() * 0.8f : 0f;
        level += (target - level) * 0.35f;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        float scale = Math.min(w / 320f, h / 110f);
        canvas.save();
        canvas.translate((w - 320f * scale) / 2f, (h - 110f * scale) / 2f);
        canvas.scale(scale, scale);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(233, 221, 191));
        canvas.drawRoundRect(2, 2, 318, 108, 14, 14, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(Color.rgb(83, 63, 32));
        canvas.drawRoundRect(2, 2, 318, 108, 14, 14, p);
        float cx = 160, cy = 116, radius = 91;
        p.setStrokeWidth(2);
        for (int i = 0; i <= 10; i++) {
            double a = Math.toRadians(200 + i * 14);
            float x1 = cx + (float)Math.cos(a) * (radius - 11);
            float y1 = cy + (float)Math.sin(a) * (radius - 11);
            float x2 = cx + (float)Math.cos(a) * radius;
            float y2 = cy + (float)Math.sin(a) * radius;
            p.setColor(i >= 8 ? Color.rgb(175, 42, 27) : Color.rgb(68, 50, 31));
            canvas.drawLine(x1, y1, x2, y2, p);
        }
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(75, 53, 29));
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(18);
        p.setFakeBoldText(true);
        canvas.drawText("FUZZ  VU", 160, 30, p);
        double angle = Math.toRadians(200 + 140 * level);
        p.setColor(Color.rgb(180, 40, 28));
        p.setStrokeWidth(3);
        canvas.drawLine(cx, cy, cx + (float)Math.cos(angle) * 77,
                cy + (float)Math.sin(angle) * 77, p);
        p.setColor(Color.rgb(40, 30, 19));
        canvas.drawCircle(cx, cy, 7, p);
        canvas.restore();
    }
}
