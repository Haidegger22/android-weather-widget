package com.ivanhoe.weather;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.provider.AlarmClock;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Виджет: слева часы (живые), справа погода. Тап по времени — часы, тап по погоде — подробности. */
public class WeatherWidget extends AppWidgetProvider {

    public static final String ACTION_REFRESH = "com.ivanhoe.weather.REFRESH";
    private static final TimeZone MSK = TimeZone.getTimeZone("Europe/Moscow");

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            mgr.updateAppWidget(id, buildViews(ctx, OpenMeteoService.loadCached(ctx)));
        }
        refreshAsync(ctx, mgr, ids);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) {
            AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WeatherWidget.class));
            refreshAsync(ctx, mgr, ids);
        }
    }

    /** Обновляем в фоне, чтобы не тормозить домашний экран. */
    private void refreshAsync(final Context ctx, final AppWidgetManager mgr, final int[] ids) {
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Weather w = OpenMeteoService.fetch(ctx);
                    for (int id : ids) {
                        mgr.updateAppWidget(id, buildViews(ctx, w));
                    }
                } finally {
                    pr.finish();
                }
            }
        }).start();
    }

    static RemoteViews buildViews(Context ctx, Weather w) {
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);

        // левый блок: время (TextClock тикает сам), под ним дата и город
        SimpleDateFormat dayFmt = new SimpleDateFormat("EEE, d MMM", new Locale("ru", "RU"));
        String dateText = dayFmt.format(new Date());
        dateText = dateText.substring(0, 1).toUpperCase(new Locale("ru", "RU")) + dateText.substring(1);
        v.setTextViewText(R.id.tv_date, dateText + ", " + Prefs.city(ctx));

        // правый блок: иконка, температура, днём/ночью
        if (w != null && !Double.isNaN(w.temp)) {
            int t = (int) Math.round(w.temp);
            v.setTextViewText(R.id.tv_temp, (t > 0 ? "+" : "") + t + "°");
            v.setImageViewResource(R.id.iv_icon, Weather.iconFor(w.code, w.isDay));
            String dayLine = formatTemp(w.dayMax);
            String nightLine = formatTemp(w.dayMin);
            v.setTextViewText(R.id.tv_day, ctx.getString(R.string.day_label) + " " + dayLine);
            v.setTextViewText(R.id.tv_night, ctx.getString(R.string.night_label) + " " + nightLine);
        } else {
            v.setTextViewText(R.id.tv_temp, "--°");
            v.setImageViewResource(R.id.iv_icon, R.drawable.ic_cloud);
            v.setTextViewText(R.id.tv_day, ctx.getString(R.string.loading));
            v.setTextViewText(R.id.tv_night, "");
        }

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE;

        // тап по времени — родные часы телефона (на Huawei это их приложение часов)
        v.setOnClickPendingIntent(R.id.tv_time, clockIntent(ctx, flags));

        // тап по погоде — подробное окно
        v.setOnClickPendingIntent(R.id.right_group, detailIntent(ctx, flags, 2));

        // тап по дате или стрелке — обновить
        Intent refresh = new Intent(ctx, WeatherWidget.class);
        refresh.setAction(ACTION_REFRESH);
        PendingIntent piRefresh = PendingIntent.getBroadcast(ctx, 3, refresh, flags);
        v.setOnClickPendingIntent(R.id.iv_refresh, piRefresh);
        v.setOnClickPendingIntent(R.id.tv_date, piRefresh);

        return v;
    }

    private static PendingIntent detailIntent(Context ctx, int flags, int requestCode) {
        Intent open = new Intent(ctx, DetailActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(ctx, requestCode, open, flags);
    }

    /**
     * Часы телефона. Ищем приложение часов среди установленных: по имени пакета,
     * классу и подписи на рабочем столе («Часы», «Будильник», Clock). Если не нашли —
     * пробуем штатный системный вызов «показать будильники».
     */
    private static PendingIntent clockIntent(Context ctx, int flags) {
        PackageManager pm = ctx.getPackageManager();
        Locale ru = new Locale("ru", "RU");
        Intent launchable = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Intent found = null;
        Intent preferred = null;

        try {
            for (ResolveInfo ri : pm.queryIntentActivities(launchable, 0)) {
                if (ri.activityInfo == null || ri.activityInfo.packageName == null) {
                    continue;
                }
                String pkg = ri.activityInfo.packageName.toLowerCase(Locale.ROOT);
                String cls = ri.activityInfo.name == null ? "" : ri.activityInfo.name.toLowerCase(Locale.ROOT);
                String label = "";
                try {
                    label = String.valueOf(ri.loadLabel(pm)).toLowerCase(ru);
                } catch (Exception ignored) {
                }
                boolean looksLikeClock = pkg.contains("deskclock") || pkg.contains("clock")
                        || cls.contains("deskclock") || cls.contains("clock")
                        || label.contains("час") || label.contains("clock") || label.contains("будильник");
                if (!looksLikeClock) {
                    continue;
                }
                Intent open = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setComponent(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (found == null) {
                    found = open;
                }
                if (pkg.contains("deskclock") || pkg.contains("huawei") || cls.contains("deskclock")) {
                    preferred = open;
                    break;
                }
            }
        } catch (Exception ignored) {
        }

        Intent chosen = preferred != null ? preferred : found;
        if (chosen == null) {
            Intent[] direct = new Intent[]{
                    new Intent(AlarmClock.ACTION_SHOW_ALARMS),
                    new Intent(AlarmClock.ACTION_SHOW_ALARMS).setPackage("com.huawei.deskclock"),
                    new Intent(AlarmClock.ACTION_SHOW_ALARMS).setPackage("com.android.deskclock"),
            };
            for (Intent candidate : direct) {
                candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (pm.resolveActivity(candidate, 0) != null) {
                    chosen = candidate;
                    break;
                }
            }
        }
        if (chosen == null) {
            chosen = new Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return PendingIntent.getActivity(ctx, 1, chosen, flags);
    }

    private static String formatTemp(double value) {
        if (Double.isNaN(value)) {
            return "—";
        }
        int t = (int) Math.round(value);
        return (t > 0 ? "+" : "") + t + "°";
    }
}
