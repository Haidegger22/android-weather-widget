package com.ivanhoe.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/** График температур по дням: две линии (дневной максимум и ночной минимум) с подписями. */
public class TempGraphView extends View {

    private List<Weather.Day> days = new ArrayList<>();
    private final Paint high = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint low = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotH = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

    public TempGraphView(Context c) {
        super(c);
        init(c);
    }

    public TempGraphView(Context c, AttributeSet a) {
        super(c, a);
        init(c);
    }

    private void init(Context c) {
        high.setStyle(Paint.Style.STROKE);
        high.setStrokeWidth(dp(c, 1.6f));
        high.setColor(0xFFE8F1FF);

        low.setStyle(Paint.Style.STROKE);
        low.setStrokeWidth(dp(c, 1.6f));
        low.setColor(0xFF8FA6C4);

        dotH.setStyle(Paint.Style.FILL);
        dotH.setColor(0xFFE8F1FF);
        dotL.setStyle(Paint.Style.FILL);
        dotL.setColor(0xFF8FA6C4);

        label.setColor(0xFFFFFFFF);
        label.setTextSize(sp(c, 12f));
        label.setTextAlign(Paint.Align.CENTER);
    }

    public void setDays(List<Weather.Day> value) {
        days = value == null ? new ArrayList<Weather.Day>() : value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (days.size() < 2) {
            return;
        }

        double min = Double.MAX_VALUE;
        double max = -Double.MAX_VALUE;
        for (Weather.Day d : days) {
            min = Math.min(min, d.min);
            max = Math.max(max, d.max);
        }
        double span = Math.max(1d, max - min);
        min -= span * 0.25;
        max += span * 0.25;

        float padX = dp(getContext(), 26f);
        float padTop = dp(getContext(), 22f);
        float padBottom = dp(getContext(), 22f);
        float w = getWidth() - padX * 2;
        float h = getHeight() - padTop - padBottom;
        int n = days.size();

        float[] xs = new float[n];
        float[] yh = new float[n];
        float[] yl = new float[n];
        for (int i = 0; i < n; i++) {
            xs[i] = padX + w * i / (n - 1);
            yh[i] = padTop + h * (float) (1 - (days.get(i).max - min) / (max - min));
            yl[i] = padTop + h * (float) (1 - (days.get(i).min - min) / (max - min));
        }

        Path ph = new Path();
        Path pl = new Path();
        for (int i = 0; i < n; i++) {
            if (i == 0) {
                ph.moveTo(xs[i], yh[i]);
                pl.moveTo(xs[i], yl[i]);
            } else {
                ph.lineTo(xs[i], yh[i]);
                pl.lineTo(xs[i], yl[i]);
            }
        }
        canvas.drawPath(ph, high);
        canvas.drawPath(pl, low);

        for (int i = 0; i < n; i++) {
            canvas.drawCircle(xs[i], yh[i], dp(getContext(), 3f), dotH);
            canvas.drawCircle(xs[i], yl[i], dp(getContext(), 3f), dotL);

            label.setColor(0xFFFFFFFF);
            canvas.drawText(fmt(days.get(i).max), xs[i], yh[i] - dp(getContext(), 8f), label);
            label.setColor(0xFFB8C6D9);
            canvas.drawText(fmt(days.get(i).min), xs[i], yl[i] + dp(getContext(), 16f), label);
        }
    }

    private String fmt(double v) {
        int t = (int) Math.round(v);
        return (t > 0 ? "+" : "") + t + "°";
    }

    private float dp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    private float sp(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, c.getResources().getDisplayMetrics());
    }
}
