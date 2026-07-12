package com.transiva.app.customer.data;

import android.net.Uri;

import com.transiva.app.customer.domain.CustomerDashboardRepository;
import com.transiva.app.customer.domain.DashboardState;
import com.transiva.app.customer.domain.Promo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class CustomerDashboardRepositoryImpl implements CustomerDashboardRepository {

    private static final String BASE_URL = "https://transiva.my.id/";
    private static final int TIMEOUT = 15000;

    @Override
    public DashboardState load(String username, int userId) throws Exception {
        double balance = loadBalance(username);
        String order = loadActiveOrder(username, userId);
        List<Promo> promos = loadPromos();
        return new DashboardState(balance, order, promos);
    }

    private double loadBalance(String username) throws Exception {
        JSONObject json = get(BASE_URL + "server/getBalance.php?username="
                + Uri.encode(username) + "&_=" + System.currentTimeMillis());
        return json.optBoolean("success", false) ? json.optDouble("balance", 0) : 0;
    }

    private String loadActiveOrder(String username, int userId) {
        try {
            JSONObject json = get(BASE_URL + "server/customer_get_active_orders.php?user_id="
                    + userId + "&username=" + Uri.encode(username)
                    + "&_=" + System.currentTimeMillis());
            JSONArray orders = json.optJSONArray("orders");
            if (!json.optBoolean("success", false) || orders == null || orders.length() == 0) {
                return "Belum ada pesanan aktif";
            }
            JSONObject order = orders.optJSONObject(0);
            if (order == null) return "Belum ada pesanan aktif";

            String service = first(order.optString("service_name"),
                    order.optString("order_type"), "Pesanan");
            String status = first(order.optString("status"), "pending")
                    .replace("_", " ");
            String driver = first(order.optString("driver"),
                    order.optString("driver_username"), "");
            return service + " • " + status
                    + (driver.isEmpty() ? "" : "\nDriver: " + driver);
        } catch (Exception ignored) {
            return "Status pesanan belum dapat diperbarui";
        }
    }

    private List<Promo> loadPromos() {
        // Safe default while the backend promo endpoint is being prepared.
        // Replace this list with GET server/customer_promos.php when available.
        List<Promo> promos = new ArrayList<>();
        promos.add(new Promo(
                "Hemat Perjalanan",
                "Diskon 20% TransRide dan TransCar hari ini.",
                "JALAN20"
        ));
        promos.add(new Promo(
                "Gratis Antar",
                "Potongan ongkir TransFood untuk minimum belanja Rp50.000.",
                "FOODHEMAT"
        ));
        return promos;
    }

    private JSONObject get(String endpoint) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/json");
            connection.setUseCaches(false);

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 400
                    ? connection.getInputStream() : connection.getErrorStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            StringBuilder body = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) body.append(line);
            reader.close();
            if (code < 200 || code >= 400) {
                throw new IllegalStateException("HTTP " + code);
            }
            return new JSONObject(body.toString());
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }
}
