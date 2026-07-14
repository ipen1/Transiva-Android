package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.transiva.app.driver.data.DriverDashboardRepositoryImpl;
import com.transiva.app.driver.domain.DriverDashboardState;
import com.transiva.app.driver.domain.DriverOrder;
import com.transiva.app.driver.presentation.DriverDashboardContract;
import com.transiva.app.driver.presentation.DriverDashboardPresenter;
import com.transiva.app.driver.ui.DriverBottomNavigation;

import java.text.NumberFormat;
import java.util.Locale;

public class DriverDashboardActivity extends Activity
        implements DriverDashboardContract.View {

    private static final int REQ_LOCATION = 8702;
    private static final long REFRESH_MS = 5000L;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private SessionManager session;
    private DriverDashboardPresenter presenter;
    private DriverDashboardState currentState;

    private FrameLayout page;
    private LinearLayout shell;
    private LinearLayout content;
    private LinearLayout activeBox;
    private LinearLayout offerBox;
    private LinearLayout homeSections;
    private LinearLayout orderSections;

    private TextView nameText;
    private TextView verificationText;
    private TextView balanceText;
    private TextView earningText;
    private TextView tripText;
    private TextView ratingText;
    private TextView onlineLabel;
    private TextView readinessText;
    private TextView lastUpdateText;

    private Switch onlineSwitch;
    private ProgressBar loading;
    private boolean suppressSwitch;

    private final Runnable refreshRunnable = new Runnable() {
        @Override public void run() {
            if (presenter != null) presenter.load(false);
            handler.postDelayed(this, REFRESH_MS);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        session = new SessionManager(this);
        if (!validSession()) return;

        presenter = new DriverDashboardPresenter(
                new DriverDashboardRepositoryImpl(session),
                this
        );

        setContentView(buildScreen());
        presenter.load(true);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!validSession()) return;
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, REFRESH_MS);
        if (presenter != null) presenter.load(false);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (presenter != null) presenter.destroy();
        super.onDestroy();
    }

    private boolean validSession() {
        boolean valid = session != null
                && session.isLoggedIn()
                && "driver".equals(session.normalizeRole(session.getRole()))
                && !clean(session.getToken()).isEmpty();

        if (!valid) {
            if (session != null) session.forceLogout("invalid_driver_session");
            DriverServiceController.stop(this);
            Intent intent = new Intent(this, LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
            return false;
        }
        return true;
    }

    private View buildScreen() {
        page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F7FAFF"));

        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        page.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(14), dp(14), dp(20));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        buildHeader();

        homeSections = new LinearLayout(this);
        homeSections.setOrientation(LinearLayout.VERTICAL);
        content.addView(homeSections);

        buildReadiness();
        buildWalletAndPerformance();

        orderSections = new LinearLayout(this);
        orderSections.setOrientation(LinearLayout.VERTICAL);
        content.addView(orderSections);

        buildOrderSections();

        shell.addView(
                DriverBottomNavigation.build(this,
                        new DriverBottomNavigation.HomeAction() {
                            @Override public void showHome() {
                                homeSections.setVisibility(View.VISIBLE);
                                orderSections.setVisibility(View.VISIBLE);
                                scroll.smoothScrollTo(0, 0);
                            }

                            @Override public void showOrders() {
                                homeSections.setVisibility(View.GONE);
                                orderSections.setVisibility(View.VISIBLE);
                                scroll.smoothScrollTo(0, 0);
                            }
                        }),
                new LinearLayout.LayoutParams(-1, dp(66))
        );

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp =
                new FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER);
        page.addView(loading, lp);

        return page;
    }

    private void buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));

        left.addView(text("Selamat bekerja 👋", 12, "#64748B", false));

        nameText = text(
                first(session.getName(), session.getUsername(), "Driver"),
                23,
                "#0B3A78",
                true
        );
        add(left, nameText, 0, dp(1), 0, 0);

        verificationText = text("Memeriksa akun…", 10, "#D97706", true);
        verificationText.setPadding(dp(8), dp(4), dp(8), dp(4));
        add(left, verificationText, 0, dp(5), 0, 0);

        lastUpdateText = text("Belum diperbarui", 10, "#64748B", false);
        lastUpdateText.setGravity(Gravity.END);
        row.addView(lastUpdateText, new LinearLayout.LayoutParams(-2, -2));

        content.addView(row);
    }

    private void buildReadiness() {
        LinearLayout card = card();
        card.addView(text("Status Driver", 18, "#0B3A78", true));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);

        onlineLabel = text("OFFLINE", 15, "#EF4444", true);
        row.addView(onlineLabel, new LinearLayout.LayoutParams(0, -2, 1));

        onlineSwitch = new Switch(this);
        onlineSwitch.setOnCheckedChangeListener((button, checked) -> {
            if (suppressSwitch) return;
            if (checked && !ensureLocationReady()) {
                setSwitch(false);
                return;
            }
            presenter.setOnline(
                    checked,
                    normalizeDriverType(session.getDriverType())
            );
        });
        row.addView(onlineSwitch);
        add(card, row, 0, dp(10), 0, 0);

        readinessText = text(
                "Izin lokasi dan GPS akan diperiksa sebelum online.",
                12,
                "#64748B",
                false
        );
        add(card, readinessText, 0, dp(8), 0, 0);

        add(homeSections, card, 0, dp(16), 0, 0);
    }

    private void buildWalletAndPerformance() {
        LinearLayout wallet = new LinearLayout(this);
        wallet.setOrientation(LinearLayout.VERTICAL);
        wallet.setPadding(dp(17), dp(15), dp(17), dp(15));
        wallet.setBackground(gradient("#086BFF", "#2EA2FF", dp(22)));

        wallet.addView(text("Saldo Driver", 13, "#EAF4FF", true));
        balanceText = text("Rp 0", 27, "#FFFFFF", true);
        add(wallet, balanceText, 0, dp(3), 0, 0);

        LinearLayout moneyActions = new LinearLayout(this);
        Button deposit = whiteButton("Deposit");
        deposit.setOnClickListener(v ->
                startActivity(new Intent(this, DriverTopUpActivity.class)));
        moneyActions.addView(deposit, new LinearLayout.LayoutParams(0, dp(44), 1));

        Button withdraw = whiteButton("Withdraw");
        withdraw.setOnClickListener(v ->
                startActivity(new Intent(this, DriverWithdrawActivity.class)));
        LinearLayout.LayoutParams wlp = new LinearLayout.LayoutParams(0, dp(44), 1);
        wlp.setMargins(dp(8), 0, 0, 0);
        moneyActions.addView(withdraw, wlp);
        add(wallet, moneyActions, 0, dp(12), 0, 0);

        add(homeSections, wallet, 0, dp(12), 0, 0);

        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);

        earningText = stat(stats, "Rp 0", "Hari ini");
        tripText = stat(stats, "0", "Trip");
        ratingText = stat(stats, "0.0", "Rating");

        add(homeSections, stats, 0, dp(10), 0, 0);
    }

    private TextView stat(LinearLayout parent, String value, String label) {
        LinearLayout box = card();
        box.setGravity(Gravity.CENTER);
        TextView number = text(value, 16, "#0B3A78", true);
        box.addView(number);
        box.addView(text(label, 10, "#64748B", false));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        if (parent.getChildCount() > 0) lp.setMargins(dp(7), 0, 0, 0);
        parent.addView(box, lp);
        return number;
    }

    private void buildOrderSections() {
        orderSections.addView(section("Order Aktif"));
        activeBox = new LinearLayout(this);
        activeBox.setOrientation(LinearLayout.VERTICAL);
        orderSections.addView(activeBox);

        orderSections.addView(section("Tawaran Order"));
        offerBox = new LinearLayout(this);
        offerBox.setOrientation(LinearLayout.VERTICAL);
        orderSections.addView(offerBox);
    }

    @Override public void showLoading(boolean visible) {
        loading.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override public void showDashboard(DriverDashboardState state) {
        currentState = state;

        nameText.setText(first(state.displayName, state.username, "Driver"));
        verificationText.setText(
                state.verified ? "✓ Terverifikasi" : "• Belum Terverifikasi");
        verificationText.setTextColor(Color.parseColor(
                state.verified ? "#0E9F4B" : "#D97706"));
        verificationText.setBackground(round(
                state.verified ? "#EAFBF1" : "#FFF7E6", dp(12)));

        balanceText.setText(rupiah(state.balance));
        earningText.setText(rupiah(state.todayEarning));
        tripText.setText(String.valueOf(state.todayTrips));
        ratingText.setText(String.format(Locale.US, "%.1f", state.rating));

        onlineLabel.setText(state.online ? "ONLINE" : "OFFLINE");
        onlineLabel.setTextColor(Color.parseColor(
                state.online ? "#16A34A" : "#EF4444"));
        setSwitch(state.online);

        session.put("driver_server_online", state.online ? "1" : "0");

        if (state.online) {
            DriverServiceController.start(this);
        } else {
            DriverServiceController.stop(this);
        }

        readinessText.setText(
                state.online
                        ? "Online • lokasi driver dijaga oleh foreground service."
                        : "Offline • lokasi tidak dikirim dan order tidak ditawarkan."
        );

        lastUpdateText.setText("Baru diperbarui");

        activeBox.removeAllViews();
        if (state.activeOrder == null) {
            session.remove("current_order_id");
            activeBox.addView(emptyCard("Belum ada order aktif."));
        } else {
            session.put("current_order_id", state.activeOrder.id);
            activeBox.addView(orderCard(state.activeOrder, true));
        }

        offerBox.removeAllViews();
        if (!state.online) {
            offerBox.addView(emptyCard(
                    "Driver OFFLINE.\nAktifkan ONLINE untuk menerima order."));
        } else if (state.offers.isEmpty()) {
            offerBox.addView(emptyCard("Belum ada tawaran order."));
        } else {
            for (DriverOrder offer : state.offers) {
                offerBox.addView(orderCard(offer, false));
            }
        }
    }

    @Override public void showActionLoading(String action, boolean visible) {
        showLoading(visible);
        onlineSwitch.setEnabled(!visible);
    }

    @Override public void showMessage(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override public void showSessionExpired() {
        session.forceLogout("session_expired");
        DriverServiceController.stop(this);
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }

    @Override public void openTrip(DriverOrder order) {
        try {
            session.put("current_order_id", order.id);
            Intent intent = new Intent(this, DriverTripActivity.class);
            intent.putExtra("order_json", order.raw.toString());
            intent.putExtra("order_table", order.source);
            intent.putExtra("driver", session.getUsername());
            intent.putExtra("driver_type",
                    normalizeDriverType(session.getDriverType()));
            startActivity(intent);
        } catch (Exception error) {
            showMessage("Tidak dapat membuka halaman trip.");
        }
    }

    private View orderCard(DriverOrder order, boolean active) {
        LinearLayout card = card();
        card.addView(text(
                (active ? "Order Aktif" : "Tawaran") + " #" + order.id,
                17,
                "#0B3A78",
                true
        ));
        add(card, text(order.serviceName, 14, "#0B7CFF", true),
                0, dp(5), 0, 0);
        add(card, text("Pickup:\n" + order.pickupAddress,
                13, "#334155", false), 0, dp(8), 0, 0);
        add(card, text("Tujuan:\n" + order.destinationAddress,
                13, "#334155", false), 0, dp(6), 0, 0);

        String meta = "Pendapatan " + rupiah(order.driverEarning);
        if (!clean(order.pickupDistanceText).isEmpty()) {
            meta += " • " + order.pickupDistanceText;
        }
        if (!active && order.remainingSeconds >= 0) {
            meta += " • " + order.remainingSeconds + " detik";
        }
        add(card, text(meta, 13, "#0F172A", true),
                0, dp(8), 0, 0);

        Button action = primaryButton(
                active ? "Lanjutkan Trip" : "Ambil Order");
        if (active) {
            action.setOnClickListener(v -> openTrip(order));
        } else {
            action.setOnClickListener(v ->
                    presenter.acceptOrder(order.id));
        }
        add(card, action, 0, dp(12), 0, 0);

        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, dp(12));
        card.setLayoutParams(lp);
        return card;
    }

    private boolean ensureLocationReady() {
        if (!hasLocationPermission()) {
            if (Build.VERSION.SDK_INT >= 23) {
                requestPermissions(new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, REQ_LOCATION);
            }
            showMessage("Izinkan lokasi agar driver dapat online.");
            return false;
        }

        LocationManager manager =
                (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean enabled = false;
        try {
            enabled = manager != null
                    && (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                    || manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));
        } catch (Exception ignored) {}

        if (!enabled) {
            new AlertDialog.Builder(this)
                    .setTitle("Aktifkan lokasi")
                    .setMessage("GPS/lokasi wajib aktif sebelum driver online.")
                    .setPositiveButton("Buka Pengaturan", (dialog, which) ->
                            startActivity(new Intent(
                                    Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton("Batal", null)
                    .show();
            return false;
        }
        return true;
    }

    @Override public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (hasLocationPermission()) {
                showMessage("Izin lokasi diberikan. Aktifkan ONLINE kembali.");
            } else {
                setSwitch(false);
                showMessage("Driver tidak dapat online tanpa izin lokasi.");
            }
        }
    }

    private boolean hasLocationPermission() {
        if (Build.VERSION.SDK_INT < 23) return true;
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void setSwitch(boolean checked) {
        suppressSwitch = true;
        onlineSwitch.setChecked(checked);
        suppressSwitch = false;
    }

    private String normalizeDriverType(String value) {
        String clean = clean(value).toLowerCase(Locale.US);
        return clean.equals("car") || clean.equals("mobil") ? "car" : "bike";
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(15), dp(14), dp(15), dp(14));
        card.setBackground(roundStroke(
                "#FFFFFF", "#D7E6F8", dp(21), 1));
        return card;
    }

    private TextView section(String value) {
        TextView text = text(value, 20, "#0B3A78", true);
        text.setPadding(0, dp(17), 0, dp(5));
        return text;
    }

    private TextView emptyCard(String value) {
        TextView text = text(value, 14, "#334155", false);
        text.setPadding(dp(15), dp(18), dp(15), dp(18));
        text.setBackground(roundStroke(
                "#FFFFFF", "#D7E6F8", dp(21), 1));
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(7), 0, dp(12));
        text.setLayoutParams(lp);
        return text;
    }

    private TextView text(
            String value, int sp, String color, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(Color.parseColor(color));
        if (bold) text.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return text;
    }

    private Button primaryButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round("#0B7CFF", dp(14)));
        return button;
    }

    private Button whiteButton(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(Color.parseColor("#0B7CFF"));
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(round("#FFFFFF", dp(13)));
        return button;
    }

    private GradientDrawable round(String fill, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(Color.parseColor(fill));
        shape.setCornerRadius(radius);
        return shape;
    }

    private GradientDrawable roundStroke(
            String fill, String stroke, int radius, int width) {
        GradientDrawable shape = round(fill, radius);
        shape.setStroke(dp(width), Color.parseColor(stroke));
        return shape;
    }

    private GradientDrawable gradient(
            String start, String end, int radius) {
        GradientDrawable shape = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{
                        Color.parseColor(start),
                        Color.parseColor(end)
                }
        );
        shape.setCornerRadius(radius);
        return shape;
    }

    private void add(
            LinearLayout parent,
            View child,
            int left,
            int top,
            int right,
            int bottom
    ) {
        LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(left, top, right, bottom);
        parent.addView(child, lp);
    }

    private int dp(int value) {
        return Math.round(
                value * getResources().getDisplayMetrics().density);
    }

    private String rupiah(long value) {
        return NumberFormat.getCurrencyInstance(
                new Locale("id", "ID"))
                .format(value)
                .replace(",00", "");
    }

    private String first(String... values) {
        if (values == null) return "";
        for (String value : values) {
            String clean = clean(value);
            if (!clean.isEmpty()
                    && !"null".equalsIgnoreCase(clean)
                    && !"undefined".equalsIgnoreCase(clean)) {
                return clean;
            }
        }
        return "";
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
