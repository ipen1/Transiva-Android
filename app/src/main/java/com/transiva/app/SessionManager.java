package com.transiva.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.util.Locale;

/**
 * SessionManager.java - Native Login Session Guard
 *
 * Tujuan:
 * - Native Android menjadi penjaga session utama.
 * - Service native hanya boleh aktif jika session benar-benar valid.
 * - localStorage WebView tidak boleh menyalakan service jika datanya kosong/rusak/logout.
 * - Logout membersihkan flag online agar BootReceiver/SyncService tidak hidup sendiri.
 */
public class SessionManager {

    private static final String PREF_NAME = "transiva_native_session";
    private static final String LEGACY_PREF_NAME = "transiva";

    private static final long MAX_SESSION_AGE_MS = 1000L * 60L * 60L * 24L * 30L; // 30 hari

    private final Context appContext;
    private final SharedPreferences prefs;
    private final SharedPreferences legacyPrefs;

    public SessionManager(Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        legacyPrefs = appContext.getSharedPreferences(LEGACY_PREF_NAME, Context.MODE_PRIVATE);
        migrateLegacyFlagsIfNeeded();
    }

    /**
     * Simpan session hanya jika data minimal valid.
     * Return true jika session tersimpan, false jika ditolak.
     */
    public boolean saveSession(String json) {
        try {
            JSONObject obj = new JSONObject(json == null ? "{}" : json);
            JSONObject clean = normalizeSession(obj);

            if (!isSessionObjectValid(clean)) {
                markLoggedOut("invalid_session_payload");
                return false;
            }

            SharedPreferences.Editor e = prefs.edit();

            e.putString("raw_session", clean.toString());
            e.putString("id", clean.optString("id", ""));
            e.putString("user_id", clean.optString("user_id", clean.optString("id", "")));
            e.putString("username", clean.optString("username", ""));
            e.putString("name", clean.optString("name", ""));
            e.putString("role", clean.optString("role", "customer"));
            e.putString("phone", clean.optString("phone", ""));
            e.putString("token", clean.optString("token", ""));
            e.putString("restaurant_id", clean.optString("restaurant_id", ""));
            e.putString("balance", clean.optString("balance", "0"));
            e.putString("driver_type", clean.optString("driver_type", "bike"));
            e.putString("photo", clean.optString("photo", ""));
            e.putString("driver_photo", clean.optString("driver_photo", ""));

            e.putBoolean("native_logged_in", true);
            e.putString("native_session_state", "active");
            e.putString("native_session_message", "Session aktif");
            e.putLong("saved_at", System.currentTimeMillis());
            e.putLong("last_seen_at", System.currentTimeMillis());
            e.putLong("logout_at", 0L);

            e.apply();

            syncLegacyOnlineFlags();
            return true;

        } catch (Exception e) {
            markLoggedOut("save_session_error");
            return false;
        }
    }

    public JSONObject getSessionJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", isLoggedIn());
            obj.put("logged_in", isLoggedIn());
            obj.put("id", getId());
            obj.put("user_id", getUserId());
            obj.put("username", getUsername());
            obj.put("name", getName());
            obj.put("role", getRole());
            obj.put("phone", getPhone());
            obj.put("token", getToken());
            obj.put("restaurant_id", getRestaurantId());
            obj.put("balance", getBalance());
            obj.put("driver_type", getDriverType());
            obj.put("photo", get("photo"));
            obj.put("driver_photo", get("driver_photo"));
            obj.put("saved_at", prefs.getLong("saved_at", 0L));
            obj.put("last_seen_at", prefs.getLong("last_seen_at", 0L));
            obj.put("session_state", prefs.getString("native_session_state", "unknown"));
            obj.put("session_message", prefs.getString("native_session_message", ""));
            obj.put("raw_session", prefs.getString("raw_session", "{}"));
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public String getSessionString() {
        return getSessionJson().toString();
    }

    /**
     * Guard utama. Service/native fitur berat wajib memakai ini.
     */
    public boolean isLoggedIn() {
        try {
            if (!prefs.getBoolean("native_logged_in", false)) return false;

            long savedAt = prefs.getLong("saved_at", 0L);
            if (savedAt <= 0L) return false;

            long age = System.currentTimeMillis() - savedAt;
            if (age < 0L || age > MAX_SESSION_AGE_MS) {
                markLoggedOut("session_expired");
                return false;
            }

            String id = safe(getId());
            String username = safe(getUsername());
            String role = normalizeRole(getRole());

            if (id.isEmpty() && username.isEmpty()) return false;
            if (role.isEmpty()) return false;
            if (!isKnownRole(role)) return false;

            return true;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Guard khusus untuk service background. Lebih ketat dari isLoggedIn().
     */
    public boolean canRunNativeServices() {
        try {
            if (!isLoggedIn()) return false;

            String role = normalizeRole(getRole());

            // Saat ini service native penting terutama driver/merchant/admin.
            return role.equals("driver") || role.equals("merchant") || role.equals("admin") || role.equals("wisata");

        } catch (Exception e) {
            return false;
        }
    }

    public boolean canRunDriverLocation() {
        try {
            return isLoggedIn() && normalizeRole(getRole()).equals("driver");
        } catch (Exception e) {
            return false;
        }
    }

    public void touchSession() {
        try {
            if (isLoggedIn()) {
                prefs.edit()
                        .putLong("last_seen_at", System.currentTimeMillis())
                        .putString("native_session_state", "active")
                        .putString("native_session_message", "Session aktif")
                        .apply();
            }
        } catch (Exception ignored) {}
    }

    public void clearSession() {
        markLoggedOut("manual_logout");
    }

    public void markLoggedOut(String reason) {
        try {
            String keepFcm = prefs.getString("fcm_token", "");
            long keepFcmAt = prefs.getLong("fcm_token_saved_at", 0L);

            SharedPreferences.Editor e = prefs.edit().clear();
            e.putBoolean("native_logged_in", false);
            e.putString("native_session_state", "logged_out");
            e.putString("native_session_message", safe(reason));
            e.putLong("logout_at", System.currentTimeMillis());

            if (!keepFcm.isEmpty()) {
                e.putString("fcm_token", keepFcm);
                e.putLong("fcm_token_saved_at", keepFcmAt);
            }

            e.apply();

            // Bersihkan flag lama yang sering membuat service hidup lagi setelah boot.
            legacyPrefs.edit()
                    .putBoolean("driver_online", false)
                    .putBoolean("merchant_online", false)
                    .putString("driver_online", "0")
                    .putString("merchant_online", "0")
                    .remove("background_sync_running")
                    .apply();

        } catch (Exception ignored) {}
    }

    private void syncLegacyOnlineFlags() {
        try {
            String role = normalizeRole(getRole());
            boolean driver = role.equals("driver");
            boolean merchant = role.equals("merchant") || role.equals("wisata");

            legacyPrefs.edit()
                    .putBoolean("driver_online", driver)
                    .putBoolean("merchant_online", merchant)
                    .putString("driver_online", driver ? "1" : "0")
                    .putString("merchant_online", merchant ? "1" : "0")
                    .apply();

        } catch (Exception ignored) {}
    }

    private void migrateLegacyFlagsIfNeeded() {
        try {
            if (prefs.contains("native_logged_in")) return;

            // Jangan percaya flag lama sebagai login. Default harus logout.
            prefs.edit()
                    .putBoolean("native_logged_in", false)
                    .putString("native_session_state", "fresh_install_or_migrated")
                    .putString("native_session_message", "Menunggu login native")
                    .apply();

            legacyPrefs.edit()
                    .putBoolean("driver_online", false)
                    .putBoolean("merchant_online", false)
                    .putString("driver_online", "0")
                    .putString("merchant_online", "0")
                    .apply();

        } catch (Exception ignored) {}
    }

    private JSONObject normalizeSession(JSONObject obj) throws Exception {
        JSONObject out = new JSONObject(obj == null ? "{}" : obj.toString());

        String id = firstNonEmpty(
                out.optString("id", ""),
                out.optString("user_id", ""),
                out.optString("uid", "")
        );

        String username = firstNonEmpty(
                out.optString("username", ""),
                out.optString("user_name", ""),
                out.optString("name", "")
        );

        String name = firstNonEmpty(
                out.optString("name", ""),
                out.optString("full_name", ""),
                username
        );

        String role = normalizeRole(firstNonEmpty(
                out.optString("role", ""),
                out.optString("user_role", ""),
                "customer"
        ));

        String driverType = firstNonEmpty(out.optString("driver_type", ""), "bike").toLowerCase(Locale.US);
        if (!driverType.equals("car")) driverType = "bike";

        out.put("id", id);
        out.put("user_id", firstNonEmpty(out.optString("user_id", ""), id));
        out.put("username", username);
        out.put("name", name);
        out.put("role", role);
        out.put("driver_type", driverType);

        if (!out.has("balance")) out.put("balance", "0");
        if (!out.has("token")) out.put("token", "");
        if (!out.has("restaurant_id")) out.put("restaurant_id", "");
        if (!out.has("phone")) out.put("phone", "");
        if (!out.has("photo")) out.put("photo", out.optString("driver_photo", ""));
        if (!out.has("driver_photo")) out.put("driver_photo", out.optString("photo", ""));

        return out;
    }

    private boolean isSessionObjectValid(JSONObject obj) {
        try {
            String id = safe(obj.optString("id", ""));
            String username = safe(obj.optString("username", ""));
            String role = normalizeRole(obj.optString("role", ""));

            if (id.isEmpty() && username.isEmpty()) return false;
            return isKnownRole(role);

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isKnownRole(String role) {
        String r = normalizeRole(role);
        return r.equals("customer") ||
                r.equals("driver") ||
                r.equals("merchant") ||
                r.equals("admin") ||
                r.equals("wisata");
    }

    private String normalizeRole(String role) {
        if (role == null) return "";

        String r = role.trim().toLowerCase(Locale.US);

        if (r.equals("user") || r.equals("pelanggan") || r.equals("costumer") || r.equals("customer")) return "customer";
        if (r.equals("driver") || r.equals("kurir") || r.equals("ojek") || r.equals("rider")) return "driver";
        if (r.equals("merchant") || r.equals("merchen") || r.equals("resto") || r.equals("restaurant") || r.equals("penjual")) return "merchant";
        if (r.equals("admin") || r.equals("administrator") || r.equals("owner") || r.equals("superadmin")) return "admin";
        if (r.equals("wisata") || r.equals("wisataowner") || r.equals("wisata_owner") || r.equals("owner_wisata")) return "wisata";

        return r;
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String v = safe(value).trim();
            if (!v.isEmpty() && !v.equalsIgnoreCase("null") && !v.equalsIgnoreCase("undefined")) return v;
        }
        return "";
    }

    public String getId() { return prefs.getString("id", ""); }
    public String getUserId() { return prefs.getString("user_id", ""); }
    public String getUsername() { return prefs.getString("username", ""); }
    public String getName() { return prefs.getString("name", ""); }
    public String getRole() { return normalizeRole(prefs.getString("role", "")); }
    public String getPhone() { return prefs.getString("phone", ""); }
    public String getToken() { return prefs.getString("token", ""); }
    public String getRestaurantId() { return prefs.getString("restaurant_id", ""); }
    public String getBalance() { return prefs.getString("balance", "0"); }
    public String getDriverType() { return prefs.getString("driver_type", "bike"); }

    public void saveFcmToken(String token) {
        prefs.edit()
                .putString("fcm_token", safe(token))
                .putLong("fcm_token_saved_at", System.currentTimeMillis())
                .apply();
    }

    public String getFcmToken() {
        return prefs.getString("fcm_token", "");
    }

    public void saveLastLocation(String latitude, String longitude) {
        prefs.edit()
                .putString("last_latitude", safe(latitude))
                .putString("last_longitude", safe(longitude))
                .putLong("last_location_at", System.currentTimeMillis())
                .apply();
    }

    public JSONObject getLastLocationJson() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("success", true);
            obj.put("latitude", prefs.getString("last_latitude", ""));
            obj.put("longitude", prefs.getString("last_longitude", ""));
            obj.put("saved_at", prefs.getLong("last_location_at", 0L));
            return obj;
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    public String getLastLocationString() {
        return getLastLocationJson().toString();
    }

    public void put(String key, String value) {
        if (key == null || key.trim().isEmpty()) return;
        prefs.edit().putString(key, safe(value)).apply();
    }

    public String get(String key) {
        if (key == null || key.trim().isEmpty()) return "";
        return prefs.getString(key, "");
    }

    public void remove(String key) {
        if (key == null || key.trim().isEmpty()) return;
        prefs.edit().remove(key).apply();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
