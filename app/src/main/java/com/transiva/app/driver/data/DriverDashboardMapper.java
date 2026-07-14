package com.transiva.app.driver.data;

import com.transiva.app.driver.domain.DriverDashboardState;
import com.transiva.app.driver.domain.DriverOrder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public final class DriverDashboardMapper {

    private DriverDashboardMapper() {}

    public static DriverDashboardState map(JSONObject root) {
        JSONObject driver = root.optJSONObject("driver");
        JSONObject wallet = root.optJSONObject("wallet");
        JSONObject performance = root.optJSONObject("performance");

        if (driver == null) driver = new JSONObject();
        if (wallet == null) wallet = new JSONObject();
        if (performance == null) performance = new JSONObject();

        DriverOrder active = mapOrder(root.optJSONObject("active_order"));
        List<DriverOrder> offers = new ArrayList<>();
        JSONArray array = root.optJSONArray("offers");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                DriverOrder order = mapOrder(array.optJSONObject(i));
                if (order != null) offers.add(order);
            }
        }

        return new DriverDashboardState(
                driver.optString("username", ""),
                first(driver.optString("name"), driver.optString("username"), "Driver"),
                driver.optString("driver_type", "bike"),
                driver.optBoolean("is_online", false),
                driver.optBoolean("verified", false),
                wallet.optLong("balance", 0),
                wallet.optLong("pending_deposit", 0),
                wallet.optLong("pending_withdraw", 0),
                performance.optLong("today_earning", 0),
                performance.optInt("today_trips", 0),
                performance.optDouble("rating", 0),
                active,
                offers,
                root.optLong("server_time_millis", System.currentTimeMillis())
        );
    }

    public static DriverOrder mapOrder(JSONObject order) {
        if (order == null || order.length() == 0) return null;
        return new DriverOrder(
                first(order.optString("id"), order.optString("order_id")),
                first(order.optString("source"), order.optString("_transiva_table"), "orders"),
                first(order.optString("service_name"), order.optString("service_type"),
                        order.optString("order_type"), "Transiva"),
                order.optString("status", ""),
                first(order.optString("pickup_address"), "-"),
                first(order.optString("destination_address"),
                        order.optString("delivery_address"), "-"),
                order.optLong("driver_earning",
                        order.optLong("price", order.optLong("total_price", 0))),
                order.optString("pickup_distance_text",
                        order.optString("distance_km", "")),
                order.optInt("remaining_seconds", -1),
                order
        );
    }

    private static String first(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (value != null) {
                String clean = value.trim();
                if (!clean.isEmpty() && !"null".equalsIgnoreCase(clean)) return clean;
            }
        }
        return "";
    }
}
