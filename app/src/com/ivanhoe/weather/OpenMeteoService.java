package com.ivanhoe.weather;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Погода с Open-Meteo (бесплатно, без ключа).
 * Даёт почасовой прогноз на каждый час и 7 дней вперёд.
 */
public class OpenMeteoService {

    private static final String TAG = "WeatherOM";
    private static final TimeZone MSK = TimeZone.getTimeZone("Europe/Moscow");

    /** Скачивает прогноз и кладёт его в кэш. Возвращает разобранные данные или null. */
    public static Weather fetch(Context ctx) {
        String url = buildUrl(Prefs.lat(ctx), Prefs.lon(ctx));
        try {
            String json = httpGet(url);
            if (json == null) {
                return loadCached(ctx);
            }
            Weather w = parse(json);
            if (w != null) {
                w.city = Prefs.city(ctx);
                Prefs.setCache(ctx, json);
            }
            return w;
        } catch (Exception e) {
            Log.w(TAG, "не удалось получить прогноз: " + e);
            return loadCached(ctx);
        }
    }

    /** Разбор последнего сохранённого ответа (работает без сети). */
    public static Weather loadCached(Context ctx) {
        String json = Prefs.cache(ctx);
        if (json == null) {
            return null;
        }
        Weather w = parse(json);
        if (w != null) {
            w.city = Prefs.city(ctx);
        }
        return w;
    }

    private static String buildUrl(double lat, double lon) {
        return "https://api.open-meteo.com/v1/forecast"
                + "?latitude=" + lat
                + "&longitude=" + lon
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day"
                + "&hourly=temperature_2m,weather_code"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
                + "&timezone=Europe%2FMoscow&forecast_days=7";
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "WeatherWidget/1.0");
            if (c.getResponseCode() != 200) {
                Log.w(TAG, "код ответа " + c.getResponseCode());
                return null;
            }
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            r.close();
            return sb.toString();
        } catch (Exception e) {
            Log.w(TAG, "сеть: " + e);
            return null;
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
    }

    private static Weather parse(String json) {
        try {
            JSONObject root = new JSONObject(json);
            Weather w = new Weather();
            w.updated = System.currentTimeMillis();

            JSONObject cur = root.optJSONObject("current");
            if (cur != null) {
                w.temp = cur.optDouble("temperature_2m", Double.NaN);
                w.feels = cur.optDouble("apparent_temperature", Double.NaN);
                w.humidity = cur.optDouble("relative_humidity_2m", Double.NaN);
                w.wind = cur.optDouble("wind_speed_10m", Double.NaN);
                w.code = cur.optInt("weather_code", 0);
                w.isDay = cur.optInt("is_day", 1) == 1;
            }

            SimpleDateFormat hourFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            hourFmt.setTimeZone(MSK);
            SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            dayFmt.setTimeZone(MSK);

            JSONObject hourly = root.optJSONObject("hourly");
            if (hourly != null) {
                JSONArray times = hourly.optJSONArray("time");
                JSONArray temps = hourly.optJSONArray("temperature_2m");
                JSONArray codes = hourly.optJSONArray("weather_code");
                if (times != null && temps != null) {
                    for (int i = 0; i < times.length(); i++) {
                        Date d = hourFmt.parse(times.getString(i));
                        if (d == null) {
                            continue;
                        }
                        Weather.Hour h = new Weather.Hour();
                        h.time = d.getTime();
                        h.temp = temps.optDouble(i, Double.NaN);
                        h.code = codes != null ? codes.optInt(i, 0) : 0;
                        w.hours.add(h);
                    }
                }
            }

            JSONObject daily = root.optJSONObject("daily");
            if (daily != null) {
                JSONArray times = daily.optJSONArray("time");
                JSONArray maxs = daily.optJSONArray("temperature_2m_max");
                JSONArray mins = daily.optJSONArray("temperature_2m_min");
                JSONArray codes = daily.optJSONArray("weather_code");
                JSONArray rises = daily.optJSONArray("sunrise");
                JSONArray sets = daily.optJSONArray("sunset");
                if (times != null && maxs != null && mins != null) {
                    for (int i = 0; i < times.length(); i++) {
                        Date d = dayFmt.parse(times.getString(i));
                        if (d == null) {
                            continue;
                        }
                        Weather.Day day = new Weather.Day();
                        day.time = d.getTime();
                        day.max = maxs.optDouble(i, Double.NaN);
                        day.min = mins.optDouble(i, Double.NaN);
                        day.code = codes != null ? codes.optInt(i, 0) : 0;
                        if (rises != null && i < rises.length()) {
                            Date r = hourFmt.parse(rises.getString(i));
                            if (r != null) {
                                day.sunrise = r.getTime();
                            }
                        }
                        if (sets != null && i < sets.length()) {
                            Date s = hourFmt.parse(sets.getString(i));
                            if (s != null) {
                                day.sunset = s.getTime();
                            }
                        }
                        w.days.add(day);
                    }
                }
            }

            if (!w.days.isEmpty()) {
                w.dayMax = w.days.get(0).max;
                w.dayMin = w.days.get(0).min;
            }
            return w;
        } catch (Exception e) {
            Log.w(TAG, "разбор ответа: " + e);
            return null;
        }
    }
}
