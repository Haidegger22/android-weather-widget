package com.ivanhoe.weather;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Подробное окно в стиле Яндекс.Погоды:
 * крупная температура по центру, лента по часам (включая восход и закат),
 * ниже — дни с графиком максимумов и минимумов на 7 дней.
 */
public class DetailActivity extends Activity {

    private static final TimeZone MSK = TimeZone.getTimeZone("Europe/Moscow");
    private static final int HOURS_AHEAD = 24;          // сколько часов показывать в ленте
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Locale ru = new Locale("ru", "RU");
    private String shownCity = "";

    @Override
    protected void onResume() {
        super.onResume();
        // если город поменяли в настройках — сразу перечитываем прогноз
        if (!Prefs.city(this).equals(shownCity)) {
            show(OpenMeteoService.loadCached(this));
            refresh();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        ImageView menu = (ImageView) findViewById(R.id.btn_menu);
        menu.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                PopupMenu popup = new PopupMenu(DetailActivity.this, v);
                popup.getMenu().add(0, 1, 0, "Обновить");
                popup.getMenu().add(0, 2, 1, "Выбрать город");
                popup.getMenu().add(0, 3, 2, "Сейчас: " + Prefs.city(DetailActivity.this));
                popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == 1) {
                            refresh();
                            return true;
                        }
                        if (item.getItemId() == 2) {
                            startActivity(new Intent(DetailActivity.this, CityActivity.class));
                            return true;
                        }
                        return false;
                    }
                });
                popup.show();
            }
        });

        show(OpenMeteoService.loadCached(this));
        refresh();
    }

    /** Тянем свежий прогноз в фоне и перерисовываем. */
    private void refresh() {
        TextView updated = (TextView) findViewById(R.id.tv_updated);
        updated.setText(getString(R.string.updated) + ": …");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final Weather w = OpenMeteoService.fetch(DetailActivity.this);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        show(w);
                    }
                });
            }
        }).start();
    }

    private void show(Weather w) {
        if (w == null) {
            ((TextView) findViewById(R.id.tv_desc)).setText(R.string.no_data);
            return;
        }

        ((TextView) findViewById(R.id.tv_city)).setText(w.city);
        shownCity = w.city;

        // крупное число и «°» отдельно — как в присланном варианте
        String big = Double.isNaN(w.temp) ? "—" : String.valueOf((int) Math.round(w.temp));
        ((TextView) findViewById(R.id.tv_big_temp)).setText(big);
        ((TextView) findViewById(R.id.tv_range)).setText(rangeText(w));
        ((TextView) findViewById(R.id.tv_desc)).setText(Weather.describe(w.code));

        StringBuilder det = new StringBuilder();
        if (!Double.isNaN(w.feels)) {
            det.append(getString(R.string.feels)).append(": ").append(temp(w.feels));
        }
        if (!Double.isNaN(w.humidity)) {
            det.append("   ").append(getString(R.string.humidity)).append(": ")
                    .append((int) Math.round(w.humidity)).append("%");
        }
        if (!Double.isNaN(w.wind)) {
            det.append("   ").append(getString(R.string.wind)).append(": ")
                    .append(Math.round(w.wind)).append(" м/с");
        }
        ((TextView) findViewById(R.id.tv_details)).setText(det.toString());

        SimpleDateFormat f = new SimpleDateFormat("HH:mm", Locale.US);
        f.setTimeZone(MSK);
        ((TextView) findViewById(R.id.tv_updated))
                .setText(getString(R.string.updated) + ": " + f.format(new Date(w.updated)));

        fillHourStrip(w);
        fillDayColumns(w);
        ((TempGraphView) findViewById(R.id.graph)).setDays(w.days);
    }

    /** «12 / 5°» — дневной максимум и ночной минимум на сегодня. */
    private String rangeText(Weather w) {
        if (Double.isNaN(w.dayMax) || Double.isNaN(w.dayMin)) {
            return "";
        }
        return ((int) Math.round(w.dayMax)) + " / " + ((int) Math.round(w.dayMin)) + "°";
    }

    /** Лента по часам: время, иконка, температура; добавляем восход и закат. */
    private void fillHourStrip(Weather w) {
        LinearLayout box = (LinearLayout) findViewById(R.id.ll_hours);
        box.removeAllViews();
        if (w.hours.isEmpty()) {
            return;
        }

        LayoutInflater inf = LayoutInflater.from(this);
        SimpleDateFormat hourFmt = new SimpleDateFormat("HH:mm", Locale.US);
        hourFmt.setTimeZone(MSK);
        SimpleDateFormat keyFmt = new SimpleDateFormat("yyyyMMddHH", Locale.US);
        keyFmt.setTimeZone(MSK);
        long now = System.currentTimeMillis();

        long sunrise = !w.days.isEmpty() ? w.days.get(0).sunrise : 0;
        long sunset = !w.days.isEmpty() ? w.days.get(0).sunset : 0;
        boolean sunriseShown = sunrise == 0 || sunrise < now;
        boolean sunsetShown = sunset == 0 || sunset < now;

        int shown = 0;
        for (Weather.Hour h : w.hours) {
            if (h.time + 3600000L < now) {
                continue;                       // прошедшие часы пропускаем
            }
            if (!sunriseShown && sunrise < h.time) {
                addSpecial(box, inf, hourFmt, sunrise, R.drawable.ic_sunrise, "Восход солнца");
                sunriseShown = true;
            }
            if (!sunsetShown && sunset < h.time) {
                addSpecial(box, inf, hourFmt, sunset, R.drawable.ic_sunset, "Закат солнца");
                sunsetShown = true;
            }

            View col = inf.inflate(R.layout.col_hour, box, false);
            ((TextView) col.findViewById(R.id.tv_col_time)).setText(hourFmt.format(new Date(h.time)));
            ((ImageView) col.findViewById(R.id.iv_col_icon))
                    .setImageResource(Weather.iconFor(h.code, isDaytime(h.time)));
            ((TextView) col.findViewById(R.id.tv_col_temp)).setText(temp(h.temp));
            box.addView(col);

            if (++shown >= HOURS_AHEAD) {
                break;
            }
        }
    }

    private void addSpecial(LinearLayout box, LayoutInflater inf, SimpleDateFormat fmt,
                            long time, int icon, String note) {
        View col = inf.inflate(R.layout.col_hour, box, false);
        ((TextView) col.findViewById(R.id.tv_col_time)).setText(fmt.format(new Date(time)));
        ((ImageView) col.findViewById(R.id.iv_col_icon)).setImageResource(icon);
        ((TextView) col.findViewById(R.id.tv_col_temp)).setText("");
        ((TextView) col.findViewById(R.id.tv_col_note)).setText(note);
        box.addView(col);
    }

    /** Колонки дней: «Сегодня 30/09», иконка и описание. */
    private void fillDayColumns(Weather w) {
        LinearLayout box = (LinearLayout) findViewById(R.id.ll_days);
        box.removeAllViews();
        LayoutInflater inf = LayoutInflater.from(this);
        SimpleDateFormat dateFmt = new SimpleDateFormat("dd.MM", Locale.US);
        dateFmt.setTimeZone(MSK);

        for (Weather.Day d : w.days) {
            View col = inf.inflate(R.layout.col_day, box, false);
            ((TextView) col.findViewById(R.id.tv_d_col_name)).setText(dayTitle(d.time));
            ((TextView) col.findViewById(R.id.tv_d_col_date)).setText(dateFmt.format(new Date(d.time)));
            ((ImageView) col.findViewById(R.id.iv_d_col_icon)).setImageResource(Weather.iconFor(d.code, true));
            ((TextView) col.findViewById(R.id.tv_d_col_desc)).setText(Weather.describe(d.code));
            box.addView(col);
        }
    }

    private String dayTitle(long time) {
        Calendar c = Calendar.getInstance(MSK);
        int today = c.get(Calendar.DAY_OF_YEAR);
        int year = c.get(Calendar.YEAR);
        c.setTimeInMillis(time);
        int that = c.get(Calendar.DAY_OF_YEAR);
        int thatYear = c.get(Calendar.YEAR);
        if (today == that && year == thatYear) {
            return "Сегодня";
        }
        if (thatYear == year && that == today + 1) {
            return "Завтра";
        }
        SimpleDateFormat f = new SimpleDateFormat("EEE", ru);
        f.setTimeZone(MSK);
        String s = f.format(new Date(time));
        return s.substring(0, 1).toUpperCase(ru) + s.substring(1);
    }

    private boolean isDaytime(long time) {
        Calendar c = Calendar.getInstance(MSK);
        c.setTimeInMillis(time);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return h >= 6 && h < 21;
    }

    private String temp(double value) {
        if (Double.isNaN(value)) {
            return "—";
        }
        int t = (int) Math.round(value);
        return (t > 0 ? "+" : "") + t + "°";
    }
}
