package com.fitterlotto.tvlauncher;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Current weather without an API key: approximate location from the public IP
 * (ipwho.is), forecast from Open-Meteo.
 */
final class WeatherClient {

    static final class Weather {
        final String city;
        final int temp;
        final int high;
        final int low;
        final String unit;
        final String condition;
        final String symbol;

        Weather(String city, int temp, int high, int low, String unit, String condition, String symbol) {
            this.city = city;
            this.temp = temp;
            this.high = high;
            this.low = low;
            this.unit = unit;
            this.condition = condition;
            this.symbol = symbol;
        }
    }

    interface Callback {
        void onWeather(Weather weather); // null on failure
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    void fetch(final Callback callback) {
        executor.execute(() -> {
            Weather result = null;
            try {
                JSONObject loc = getJson("https://ipwho.is/");
                if (!loc.optBoolean("success", true)) throw new IllegalStateException("no location");
                double lat = loc.getDouble("latitude");
                double lon = loc.getDouble("longitude");
                String city = loc.optString("city", "");
                String country = loc.optString("country_code", Locale.getDefault().getCountry());
                boolean fahrenheit = usesFahrenheit(country);

                String url = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,weather_code,is_day"
                                + "&daily=temperature_2m_max,temperature_2m_min"
                                + "&forecast_days=1&timezone=auto&temperature_unit=%s",
                        lat, lon, fahrenheit ? "fahrenheit" : "celsius");
                JSONObject wx = getJson(url);
                JSONObject current = wx.getJSONObject("current");
                JSONObject daily = wx.getJSONObject("daily");
                int code = current.getInt("weather_code");
                boolean day = current.optInt("is_day", 1) == 1;
                result = new Weather(
                        city,
                        (int) Math.round(current.getDouble("temperature_2m")),
                        (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                        (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                        fahrenheit ? "F" : "C",
                        describe(code),
                        symbol(code, day));
            } catch (Exception ignored) {
                // Offline or service unavailable: the header shows a placeholder.
            }
            final Weather done = result;
            main.post(() -> callback.onWeather(done));
        });
    }

    void shutdown() { executor.shutdownNow(); }

    private static boolean usesFahrenheit(String country) {
        return "US".equalsIgnoreCase(country) || "LR".equalsIgnoreCase(country)
                || "MM".equalsIgnoreCase(country) || "BS".equalsIgnoreCase(country)
                || "KY".equalsIgnoreCase(country) || "PW".equalsIgnoreCase(country);
    }

    private static JSONObject getJson(String address) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", "TVHome-Launcher/1.0");
        try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) sb.append(line);
            return new JSONObject(sb.toString());
        } finally {
            conn.disconnect();
        }
    }

    /** WMO weather interpretation codes, as used by Open-Meteo. */
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "—";
    }

    static String symbol(int code, boolean day) {
        if (code == 0) return day ? "☀" : "☾";
        if (code <= 2) return day ? "⛅" : "☁";
        if (code == 3 || code == 45 || code == 48) return "☁";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "❄";
        if (code >= 95) return "⚡";
        return "☂";
    }
}
