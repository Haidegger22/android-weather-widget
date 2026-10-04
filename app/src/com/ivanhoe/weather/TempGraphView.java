package com.ivanhoe.weather;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** График температур по дням: две линии (дневной максимум и ночной минимум), подписи и даты под точками. */
public class TempGraphView extends View {

    private static final TimeZone MSK = TimeZone.getTimeZone("Europe/Moscow");

    private List<Weather.Day> days = new ArrayList<>();
    private final Paint high = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint low = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotH = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint date = new Paint(Paint.ANTI_ALIAS_FLAG);

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

        // подписи дат под точками: 04.10, 05.10 и так далее
        date.setColor(0xFF9FB2CC);
        date.setTextSize(sp(c, 11f));
        date.setTextAlign(Paint.Align.CENTER);
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

        float padX = dp(getContext(), 30f);
        float padTop = dp(getContext(), 26f);
        float padBottom = dp(getContext(), 46f);   // внизу оставляем место для дат
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

        // границы: подписи не вылезают за вид и не заезжают на строку с датами
        float topLimit = dp(getContext(), 14f);
        float bottomLimit = getHeight() - dp(getContext(), 48f);

        SimpleDateFormat df = new SimpleDateFormat("dd.MM", Locale.getDefault());
        df.setTimeZone(MSK);
        float dateY = getHeight() - dp(getContext(), 12f);

        for (int i = 0; i < n; i++) {
            canvas.drawCircle(xs[i], yh[i], dp(getContext(), 3f), dotH);
            canvas.drawCircle(xs[i], yl[i], dp(getContext(), 3f), dotL);

            // дневной максимум — всегда выше своей точки
            label.setColor(0xFFFFFFFF);
            float yTop = Math.max(yh[i] - dp(getContext(), 9f), topLimit);
            canvas.drawText(fmt(days.get(i).max), xs[i], yTop, label);

            // ночной минимум — всегда ниже своей точки
            label.setColor(0xFFB8C6D9);
            float yBot = Math.min(yl[i] + dp(getContext(), 17f), bottomLimit);
            canvas.drawText(fmt(days.get(i).min), xs[i], yBot, label);

            // дата в формате 04.10; сегодняшняя — светлее: сразу видно, где начало оси
            date.setColor(i == 0 ? 0xFFE8F1FF : 0xFF9FB2CC);
            canvas.drawText(df.format(new Date(days.get(i).time)), xs[i], dateY, date);
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
