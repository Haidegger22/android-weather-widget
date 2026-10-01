package com.ivanhoe.weather;

import android.content.Context;
import android.content.SharedPreferences;

/** Небольшое хранилище: настройки и последний полученный прогноз. */
public class Prefs {

    private static final String NAME = "weather_prefs";
    private static final String KEY_CACHE = "cache";
    private static final String KEY_CITY = "city";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";
    private static final String KEY_UPDATED = "updated";

    public static final String DEFAULT_CITY = "Москва";
    public static final double DEFAULT_LAT = 55.7558;   // центр Москвы
    public static final double DEFAULT_LON = 37.6173;

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences(NAME, Context.MODE_PRIVATE);
    }

    public static String city(Context c) {
        return sp(c).getString(KEY_CITY, DEFAULT_CITY);
    }

    public static double lat(Context c) {
        return Double.longBitsToDouble(sp(c).getLong(KEY_LAT, Double.doubleToLongBits(DEFAULT_LAT)));
    }

    public static double lon(Context c) {
        return Double.longBitsToDouble(sp(c).getLong(KEY_LON, Double.doubleToLongBits(DEFAULT_LON)));
    }

    public static void setPlace(Context c, String city, double lat, double lon) {
        sp(c).edit()
                .putString(KEY_CITY, city)
                .putLong(KEY_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_LON, Double.doubleToLongBits(lon))
                .apply();
    }

    public static String cache(Context c) {
        return sp(c).getString(KEY_CACHE, null);
    }

    public static void setCache(Context c, String json) {
        sp(c).edit().putString(KEY_CACHE, json).putLong(KEY_UPDATED, System.currentTimeMillis()).apply();
    }

    public static long updated(Context c) {
        return sp(c).getLong(KEY_UPDATED, 0);
    }
}
