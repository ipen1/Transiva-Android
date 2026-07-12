package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.transiva.app.customer.data.CustomerDashboardRepositoryImpl;
import com.transiva.app.customer.domain.DashboardState;
import com.transiva.app.customer.domain.Promo;
import com.transiva.app.customer.presentation.CustomerDashboardContract;
import com.transiva.app.customer.presentation.CustomerDashboardPresenter;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

/**
 * Thin UI layer. Business/data logic lives under com.transiva.app.customer.
 */
public class CustomerDashboardActivity extends Activity implements CustomerDashboardContract.View {

    private static final int REQ_LOCATION = 1201;

    private CustomerDashboardPresenter presenter;
    private LinearLayout content;
    private TextView locationText;
    private TextView balanceText;
    private TextView orderText;
    private LinearLayout promoContainer;
    private ProgressBar loading;

    private String username = "User";
    private int userId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        readSession();
        presenter = new CustomerDashboardPresenter(
                new CustomerDashboardRepositoryImpl(),
                this
        );
        setContentView(buildScreen());
        presenter.load(username, userId);
        loadLocation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (presenter != null) presenter.refresh(username, userId);
    }

    @Override
    protected void onDestroy() {
        if (presenter != null) presenter.destroy();
        super.onDestroy();
    }

    private void readSession() {
        try {
            SessionManager session = new SessionManager(this);
            username = first(session.getUsername(), session.getName(), "User");
            try {
                userId = Integer.parseInt(first(session.getId(), session.getUserId(), "0"));
            } catch (Exception ignored) {
                userId = 0;
            }
        } catch (Exception ignored) {
            username = "User";
            userId = 0;
        }
    }

    private View buildScreen() {
        FrameLayout page = new FrameLayout(this);
        page.setBackgroundColor(Color.parseColor("#F4F8FF"));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        page.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(18), dp(16), dp(24));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        buildHeader();
        buildBalanceCard();
        buildPromoSection();
        buildServiceGrid();
        buildOrderCard();

        shell.addView(buildPermanentBottomNavigation(),
                new LinearLayout.LayoutParams(-1, dp(72)));

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(48), dp(48));
        lp.gravity = Gravity.CENTER;
        page.addView(loading, lp);
        return page;
    }

    private void buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout nameColumn = new LinearLayout(this);
        nameColumn.setOrientation(LinearLayout.VERTICAL);
        row.addView(nameColumn, new LinearLayout.LayoutParams(0, -2, 1));

        nameColumn.addView(label("Selamat datang 👋", 13, "#64748B", false));
        nameColumn.addView(label(username, 23, "#0B3A78", true));

        locationText = label("Mengambil lokasi...", 12, "#0B3A78", true);
        locationText.setGravity(Gravity.CENTER);
        locationText.setCompoundDrawablesWithIntrinsicBounds(
                getDrawableResource("ic_location_pin"), 0, 0, 0);
        locationText.setCompoundDrawablePadding(dp(5));
        locationText.setPadding(dp(10), dp(9), dp(10), dp(9));
        locationText.setBackground(Shape.roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1));
        locationText.setOnClickListener(v -> loadLocation());
        row.addView(locationText, new LinearLayout.LayoutParams(-2, -2));
    }

    private void buildBalanceCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(17), dp(18), dp(17));
        card.setBackground(Shape.gradient("#086BFF", "#2EA2FF", dp(24)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(18), 0, dp(18));
        content.addView(card, lp);

        card.addView(label("Transiva Pay", 13, "#EAF4FF", true));
        balanceText = label("Memuat saldo...", 26, "#FFFFFF", true);
        card.addView(balanceText);

        TextView topup = action("＋ Isi Saldo", "#FFFFFF", "#0B7CFF");
        LinearLayout.LayoutParams topLp = new LinearLayout.LayoutParams(-1, dp(44));
        topLp.setMargins(0, dp(14), 0, 0);
        card.addView(topup, topLp);
        topup.setOnClickListener(v ->
                startActivity(new Intent(this, CustomerTopUpActivity.class)));
    }

    private void buildPromoSection() {
        TextView title = label("Promo Hari Ini", 18, "#0B3A78", true);
        title.setCompoundDrawablesWithIntrinsicBounds(getDrawableResource("ic_promo"), 0, 0, 0);
        title.setCompoundDrawablePadding(dp(8));
        content.addView(title);

        promoContainer = new LinearLayout(this);
        promoContainer.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, dp(18));
        content.addView(promoContainer, lp);
    }

    private void renderPromos(List<Promo> promos) {
        promoContainer.removeAllViews();
        if (promos == null || promos.isEmpty()) {
            promoContainer.addView(label("Belum ada promo hari ini.", 13, "#64748B", false));
            return;
        }

        for (int i = 0; i < promos.size(); i++) {
            Promo promo = promos.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(13), dp(14), dp(13));
            card.setBackground(Shape.roundStroke(
                    i == 0 ? "#FFF6D8" : "#EAF4FF",
                    i == 0 ? "#FFD66B" : "#B9DBFF",
                    dp(20), 1
            ));
            card.addView(label(promo.title, 15, "#0F3B72", true));
            TextView desc = label(promo.description, 12, "#52647A", false);
            desc.setPadding(0, dp(5), 0, dp(8));
            card.addView(desc);
            TextView code = label("Kode: " + promo.code, 12, "#0B7CFF", true);
            card.addView(code);

            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) cardLp.setMargins(dp(9), 0, 0, 0);
            promoContainer.addView(card, cardLp);
            if (i == 1) break;
        }
    }

    private void buildServiceGrid() {
        content.addView(label("Layanan Transiva", 18, "#0B3A78", true));

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, -2);
        gridLp.setMargins(0, dp(10), 0, dp(18));
        content.addView(grid, gridLp);

        grid.addView(serviceRow(
                service("TransRide", "ic_service_ride", TransRideActivity.class),
                service("TransCar", "ic_service_car", PassengerCarActivity.class),
                service("TransFood", "ic_service_food", TransFoodActivity.class)
        ));
        grid.addView(serviceRow(
                service("TransTour", "ic_service_tour", TranstourActivity.class),
                service("Laundry", "ic_service_laundry", TransLaundryActivity.class),
                service("Pickup", "ic_service_pickup", TransPickupActivity.class)
        ));
    }

    private View serviceRow(View... items) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < items.length; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(112), 1);
            if (i > 0) lp.setMargins(dp(8), 0, 0, 0);
            row.addView(items[i], lp);
        }
        LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1, -2);
        outer.setMargins(0, 0, 0, dp(8));
        row.setLayoutParams(outer);
        return row;
    }

    private View service(String title, String icon, Class<?> destination) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(Shape.roundStroke("#FFFFFF", "#D7E6F8", dp(20), 1));

        ImageView image = new ImageView(this);
        image.setImageResource(getDrawableResource(icon));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(image, new LinearLayout.LayoutParams(dp(46), dp(46)));

        TextView label = label(title, 12, "#0B3A78", true);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(7), 0, 0);
        card.addView(label);
        card.setOnClickListener(v -> startActivity(new Intent(this, destination)));
        return card;
    }

    private void buildOrderCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(Shape.roundStroke("#FFFFFF", "#D7E6F8", dp(22), 1));
        content.addView(card, new LinearLayout.LayoutParams(-1, -2));
        card.addView(label("Status Pesanan", 17, "#0B3A78", true));
        orderText = label("Memuat pesanan aktif...", 13, "#64748B", false);
        orderText.setPadding(0, dp(8), 0, 0);
        card.addView(orderText);
    }

    private View buildPermanentBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(5), dp(6), dp(5));
        nav.setBackgroundColor(Color.WHITE);

        nav.addView(navItem("Beranda", "ic_nav_home", null, true), navLp());
        nav.addView(navItem("Aktivitas", "ic_nav_activity", CustomerHistoryActivity.class, false), navLp());
        nav.addView(navItem("Pesan", "ic_nav_chat", CustomerChatActivity.class, false), navLp());
        nav.addView(navItem("Pay", "ic_nav_wallet", CustomerTopUpActivity.class, false), navLp());
        nav.addView(navItem("Akun", "ic_nav_profile", ProfileActivity.class, false), navLp());
        return nav;
    }

    private LinearLayout.LayoutParams navLp() {
        return new LinearLayout.LayoutParams(0, -1, 1);
    }

    private View navItem(String text, String icon, Class<?> target, boolean selected) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setClickable(true);

        ImageView iv = new ImageView(this);
        iv.setImageResource(getDrawableResource(icon));
        iv.setAlpha(selected ? 1f : .62f);
        item.addView(iv, new LinearLayout.LayoutParams(dp(25), dp(25)));

        TextView tv = label(text, 10, selected ? "#0B7CFF" : "#64748B", selected);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(3), 0, 0);
        item.addView(tv);

        if (target != null) {
            item.setOnClickListener(v -> startActivity(new Intent(this, target)));
        }
        return item;
    }

    private void loadLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            }, REQ_LOCATION);
            return;
        }

        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (manager == null) return;
            boolean gps = manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!gps && !network) {
                locationText.setText("Aktifkan GPS");
                locationText.setOnClickListener(v ->
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
                return;
            }
            String provider = gps ? LocationManager.GPS_PROVIDER : LocationManager.NETWORK_PROVIDER;
            Location cached = manager.getLastKnownLocation(provider);
            if (cached != null) resolveLocation(cached);
            manager.requestSingleUpdate(provider, new LocationListener() {
                @Override public void onLocationChanged(Location location) { resolveLocation(location); }
                @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
                @Override public void onProviderEnabled(String provider) {}
                @Override public void onProviderDisabled(String provider) {}
            }, Looper.getMainLooper());
        } catch (Exception e) {
            locationText.setText("Lokasi gagal");
        }
    }

    private void resolveLocation(Location location) {
        new Thread(() -> {
            String result = "Lokasi kamu";
            try {
                List<Address> list = new Geocoder(this, new Locale("id", "ID"))
                        .getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (list != null && !list.isEmpty()) {
                    Address a = list.get(0);
                    result = first(a.getSubLocality(), a.getLocality(), a.getSubAdminArea(), "Lokasi kamu");
                }
                new SessionManager(this).saveLastLocation(
                        String.valueOf(location.getLatitude()),
                        String.valueOf(location.getLongitude())
                );
            } catch (Exception ignored) {}
            String finalResult = result;
            runOnUiThread(() -> locationText.setText(finalResult));
        }).start();
    }

    @Override
    public void showLoading(boolean visible) {
        if (loading != null) loading.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    @Override
    public void showDashboard(DashboardState state) {
        balanceText.setText(rupiah(state.balance));
        orderText.setText(state.activeOrderText);
        renderPromos(state.promos);
    }

    @Override
    public void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private TextView label(String value, int sp, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private TextView action(String value, String bg, String fg) {
        TextView view = label(value, 14, fg, true);
        view.setGravity(Gravity.CENTER);
        view.setBackground(Shape.round(bg, dp(14)));
        return view;
    }

    private int getDrawableResource(String name) {
        return getResources().getIdentifier(name, "drawable", getPackageName());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private String rupiah(double amount) {
        return NumberFormat.getCurrencyInstance(new Locale("id", "ID")).format(amount);
    }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) {
                return value.trim();
            }
        }
        return "";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadLocation();
        } else if (requestCode == REQ_LOCATION) {
            locationText.setText("Izin lokasi ditolak");
        }
    }
}
