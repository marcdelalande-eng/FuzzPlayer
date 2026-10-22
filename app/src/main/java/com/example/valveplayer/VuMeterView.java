package com.example.valveplayer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;
import java.util.Random;

/** A 60s/70s-inspired decorative amplifier gauge. The needle is not true audio metering. */
public class VuMeterView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private Typeface logoFont;
    private float level;
    private boolean playing;

    public VuMeterView(Context context) { super(context); init(context); }
    public VuMeterView(Context context, AttributeSet attrs) { super(context, attrs); init(context); }
    public VuMeterView(Context context, AttributeSet attrs, int style) { super(context, attrs, style); init(context); }

    private void init(Context context) {
        // Android's built-in cursive face: no external font file or license needed.
        logoFont = Typeface.create("cursive", Typeface.BOLD_ITALIC);
    }

    public void setPlaying(boolean active) {
        playing = active;
        float target = active ? 0.16f + random.nextFloat() * 0.79f : 0f;
        level += (target - level) * 0.31f;
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (getWidth() == 0 || getHeight() == 0) return;

        // Design coordinates scale uniformly to avoid cropped needles on narrow phones.
        float scale = Math.min(getWidth() / 340f, getHeight() / 146f);
        canvas.save();
        canvas.translate((getWidth() - 340f * scale) / 2f,
                (getHeight() - 146f * scale) / 2f);
        canvas.scale(scale, scale);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(25, 22, 19));
        canvas.drawRoundRect(new RectF(1, 1, 339, 145), 16, 16, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(3);
        p.setColor(Color.rgb(182, 139, 63));
        canvas.drawRoundRect(new RectF(5, 5, 335, 141), 14, 14, p);

        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(231, 213, 172));
        canvas.drawRoundRect(new RectF(14, 14, 326, 132), 11, 11, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(Color.rgb(90, 65, 32));
        canvas.drawRoundRect(new RectF(14, 14, 326, 132), 11, 11, p);

        text.setTextAlign(Paint.Align.CENTER);
        text.setTypeface(logoFont);
        text.setFakeBoldText(false);
        text.setTextSize(29);
        text.setColor(Color.rgb(63, 40, 20));
        canvas.drawText("Fuzz Player", 170, 48, text);

        text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setTextSize(9);
        text.setColor(Color.rgb(103, 77, 43));
        canvas.drawText("VOLUME UNIT", 170, 62, text);

        final float cx = 170f, cy = 154f, radius = 83f;
        for (int i = 0; i <= 10; i++) {
            double a = Math.toRadians(205 + 13 * i);
            float x1 = cx + (float) Math.cos(a) * 67;
            float y1 = cy + (float) Math.sin(a) * 67;
            float x2 = cx + (float) Math.cos(a) * 79;
            float y2 = cy + (float) Math.sin(a) * 79;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(i % 5 == 0 ? 3 : 2);
            p.setColor(i >= 8 ? Color.rgb(178, 48, 31) : Color.rgb(75, 51, 28));
            canvas.drawLine(x1, y1, x2, y2, p);
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2);
        p.setColor(Color.rgb(147, 112, 54));
        canvas.drawArc(new RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                205, 130, false, p);

        double angle = Math.toRadians(205 + 130 * level);
        p.setColor(Color.rgb(177, 34, 29));
        p.setStrokeWidth(3.5f);
        canvas.drawLine(cx, cy, cx + (float) Math.cos(angle) * 74,
                cy + (float) Math.sin(angle) * 74, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(47, 35, 25));
        canvas.drawCircle(cx, cy, 7, p);

        p.setColor(playing ? Color.rgb(226, 66, 35) : Color.rgb(102, 55, 40));
        canvas.drawCircle(305, 34, 6, p);
        canvas.restore();
    }
}
