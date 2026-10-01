package com.ivanhoe.weather;

import java.util.ArrayList;
import java.util.List;

/** Данные о погоде: текущая, по часам (каждый час) и по дням (7 дней). */
public class Weather {

    public String city = "Москва";
    public double temp;
    public double feels;
    public double wind;
    public double humidity;
    public int code;
    public boolean isDay = true;
    public long updated;

    public double dayMax = Double.NaN;
    public double dayMin = Double.NaN;

    public List<Hour> hours = new ArrayList<>();
    public List<Day> days = new ArrayList<>();

    public static class Hour {
        public long time;      // время точки, мс
        public double temp;
        public int code;
    }

    public static class Day {
        public long time;      // начало дня, мс
        public double max;
        public double min;
        public int code;
        public long sunrise;   // мс
        public long sunset;    // мс
    }

    /** Полное описание погоды по коду WMO (по-русски). */
    public static String describe(int code) {
        switch (code) {
            case 0: return "Ясно";
            case 1: return "Преимущественно ясно";
            case 2: return "Переменная облачность";
            case 3: return "Пасмурно";
            case 45: return "Туман";
            case 48: return "Изморозь, туман";
            case 51: return "Слабая морось";
            case 53: return "Морось";
            case 55: return "Сильная морось";
            case 56: case 57: return "Ледяная морось";
            case 61: return "Небольшой дождь";
            case 63: return "Дождь";
            case 65: return "Сильный дождь";
            case 66: case 67: return "Ледяной дождь";
            case 71: return "Небольшой снег";
            case 73: return "Снег";
            case 75: return "Сильный снег";
            case 77: return "Снежная крупа";
            case 80: return "Небольшой ливень";
            case 81: return "Ливень";
            case 82: return "Сильный ливень";
            case 85: case 86: return "Снегопад";
            case 95: return "Гроза";
            case 96: case 99: return "Гроза с градом";
            default: return "—";
        }
    }

    /** Короткое описание для почасовки. */
    public static String shortDescribe(int code) {
        switch (code) {
            case 0: return "ясно";
            case 1: return "малооблачно";
            case 2: return "облачно";
            case 3: return "пасмурно";
            case 45: case 48: return "туман";
            case 51: case 53: case 55: return "морось";
            case 56: case 57: return "лед. морось";
            case 61: return "неб. дождь";
            case 63: return "дождь";
            case 65: return "сильн. дождь";
            case 66: case 67: return "лед. дождь";
            case 71: return "неб. снег";
            case 73: return "снег";
            case 75: return "сильн. снег";
            case 77: return "снеж. крупа";
            case 80: case 81: return "ливень";
            case 82: return "сильн. ливень";
            case 85: case 86: return "снегопад";
            case 95: case 96: case 99: return "гроза";
            default: return "—";
        }
    }

    /** Какая иконка подходит для кода погоды (день/ночь). */
    public static int iconFor(int code, boolean isDay) {
        switch (code) {
            case 0:
                return isDay ? R.drawable.ic_sun : R.drawable.ic_moon;
            case 1:
            case 2:
                return isDay ? R.drawable.ic_sun_cloud : R.drawable.ic_moon;
            case 3:
                return R.drawable.ic_cloud;
            case 45:
            case 48:
                return R.drawable.ic_fog;
            case 51: case 53: case 55: case 56: case 57:
            case 61: case 63: case 65: case 66: case 67:
            case 80: case 81: case 82:
                return R.drawable.ic_rain;
            case 71: case 73: case 75: case 77: case 85: case 86:
                return R.drawable.ic_snow;
            case 95: case 96: case 99:
                return R.drawable.ic_thunder;
            default:
                return isDay ? R.drawable.ic_sun : R.drawable.ic_moon;
        }
    }
}
