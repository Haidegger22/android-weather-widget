package com.ivanhoe.weather;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Выбор города: поиск по справочнику Open-Meteo (любой город России)
 * плюс готовый список крупных городов, чтобы можно было выбрать без сети.
 */
public class CityActivity extends Activity {

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final List<Place> places = new ArrayList<>();
    private ArrayAdapter<String> adapter;
    private ListView list;
    private TextView status;
    private EditText query;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_city);

        list = (ListView) findViewById(R.id.lv_results);
        status = (TextView) findViewById(R.id.tv_status);
        query = (EditText) findViewById(R.id.et_query);
        Button search = (Button) findViewById(R.id.btn_search);

        list.setAdapter(new ArrayAdapter<String>(this, R.layout.row_city, new ArrayList<String>()) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView) super.getView(position, convertView, parent);
                v.setText(getItem(position));
                return v;
            }
        });
        adapter = (ArrayAdapter<String>) list.getAdapter();

        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                choose(places.get(position));
            }
        });

        search.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                find(query.getText().toString().trim());
            }
        });

        query.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() >= 3) {
                    find(s.toString().trim());
                } else if (s.length() == 0) {
                    showPopular();
                }
            }
        });

        showPopular();
    }

    /** Крупные города — чтобы выбрать сразу, без поиска и сети. */
    private void showPopular() {
        places.clear();
        add("Москва", 55.7558, 37.6173);
        add("Санкт-Петербург", 59.9386, 30.3141);
        add("Новосибирск", 55.0084, 82.9357);
        add("Екатеринбург", 56.8389, 60.6057);
        add("Казань", 55.7963, 49.1088);
        add("Нижний Новгород", 56.3269, 44.0059);
        add("Челябинск", 55.1644, 61.4368);
        add("Самара", 53.1959, 50.1002);
        add("Омск", 54.9885, 73.3242);
        add("Ростов-на-Дону", 47.2225, 39.7188);
        add("Уфа", 54.7388, 55.9721);
        add("Красноярск", 56.0153, 92.8932);
        add("Воронеж", 51.6720, 39.1843);
        add("Пермь", 58.0105, 56.2502);
        add("Волгоград", 48.7080, 44.5133);
        add("Краснодар", 45.0355, 38.9753);
        add("Саратов", 51.5336, 46.0343);
        add("Тюмень", 57.1522, 65.5272);
        add("Тольятти", 53.5303, 49.3461);
        add("Ижевск", 56.8527, 53.2115);
        add("Барнаул", 53.3548, 83.7698);
        add("Ульяновск", 54.3142, 48.4031);
        add("Иркутск", 52.2870, 104.3050);
        add("Хабаровск", 48.4827, 135.0838);
        add("Ярославль", 57.6261, 39.8845);
        add("Владивосток", 43.1155, 131.8855);
        add("Махачкала", 42.9764, 47.5024);
        add("Томск", 56.4977, 84.9744);
        add("Оренбург", 51.7682, 55.0969);
        add("Кемерово", 55.3547, 86.0873);
        refresh("Крупные города — тапни, чтобы выбрать. Можно искать любой город России.");
    }

    private void add(String name, double lat, double lon) {
        places.add(new Place(name, "", lat, lon));
    }

    /** Поиск города в справочнике Open-Meteo. */
    private void find(final String text) {
        if (text.length() < 2) {
            return;
        }
        status.setText("Ищу «" + text + "»…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<Place> found = searchOnline(text);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (found.isEmpty()) {
                            status.setText("Ничего не нашлось. Проверьте написание или включите ВПН.");
                            return;
                        }
                        places.clear();
                        places.addAll(found);
                        refresh("Найдено: " + found.size() + " — тапни нужный город");
                    }
                });
            }
        }).start();
    }

    private List<Place> searchOnline(String text) {
        List<Place> out = new ArrayList<>();
        HttpURLConnection c = null;
        try {
            String url = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + URLEncoder.encode(text, "UTF-8")
                    + "&count=20&language=ru&format=json&countryCode=RU";
            c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(12000);
            c.setReadTimeout(12000);
            c.setRequestProperty("User-Agent", "WeatherWidget/1.0");
            if (c.getResponseCode() != 200) {
                return out;
            }
            StringBuilder sb = new StringBuilder();
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line);
            }
            r.close();
            JSONObject root = new JSONObject(sb.toString());
            JSONArray arr = root.optJSONArray("results");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String name = o.optString("name", "");
                    String region = o.optString("admin1", "");
                    if (name.isEmpty()) {
                        continue;
                    }
                    out.add(new Place(name, region, o.optDouble("latitude"), o.optDouble("longitude")));
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (c != null) {
                c.disconnect();
            }
        }
        return out;
    }

    private void refresh(String message) {
        List<String> titles = new ArrayList<>();
        for (Place p : places) {
            titles.add(p.region == null || p.region.isEmpty() ? p.name : p.name + ", " + p.region);
        }
        adapter.clear();
        adapter.addAll(titles);
        adapter.notifyDataSetChanged();
        status.setText(message);
    }

    private void choose(Place place) {
        Prefs.setPlace(this, place.name, place.lat, place.lon);
        Toast.makeText(this, "Город выбран: " + place.name, Toast.LENGTH_SHORT).show();
        // просим виджет обновиться сразу
        Intent refresh = new Intent(this, WeatherWidget.class);
        refresh.setAction(WeatherWidget.ACTION_REFRESH);
        sendBroadcast(refresh);
        finish();
    }

    private static class Place {
        final String name;
        final String region;
        final double lat;
        final double lon;

        Place(String name, String region, double lat, double lon) {
            this.name = name;
            this.region = region;
            this.lat = lat;
            this.lon = lon;
        }
    }
}
