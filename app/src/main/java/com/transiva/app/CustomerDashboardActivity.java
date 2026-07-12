package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.HorizontalScrollView;
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

public class CustomerDashboardActivity extends Activity implements CustomerDashboardContract.View {

    private static final int REQ_LOCATION = 1201;

    private CustomerDashboardPresenter presenter;
    private LinearLayout content;
    private TextView locationText;
    private TextView balanceText;
    private TextView orderText;
    private LinearLayout promoTrack;
    private ProgressBar loading;

    private String username = "User";
    private int userId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#0B7CFF"));
        getWindow().setNavigationBarColor(Color.parseColor("#071426"));
        readSession();
        presenter = new CustomerDashboardPresenter(new CustomerDashboardRepositoryImpl(), this);
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
        page.setBackgroundColor(Color.parseColor("#F7FAFF"));

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        page.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(32));
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));

        buildHeader();
        buildWalletCard();
        buildPromoSection();
        buildServiceSection();
        buildOrderSection();
        buildRecommendationSection();

        shell.addView(buildBottomNavigation(), new LinearLayout.LayoutParams(-1, dp(76)));

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(48), dp(48));
        lp.gravity = Gravity.CENTER;
        page.addView(loading, lp);
        return page;
    }

    private void buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        content.addView(row, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        left.addView(text("Selamat datang 👋", 15, "#64748B", false));

        TextView name = text(username.toLowerCase(Locale.getDefault()), 28, "#0B3A78", true);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(-1, -2);
        nameLp.setMargins(0, dp(2), 0, dp(8));
        left.addView(name, nameLp);

        TextView city = text("Toboli, Palu  ›", 14, "#24476E", false);
        city.setCompoundDrawablesWithIntrinsicBounds(drawable("ic_location_pin"), 0, 0, 0);
        city.setCompoundDrawablePadding(dp(5));
        left.addView(city);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Shape.roundStroke("#FFFFFF", "#E2EBF6", dp(24), 1));
        card.setElevation(dp(4));
        card.setOnClickListener(v -> loadLocation());

        ImageView pin = new ImageView(this);
        pin.setImageResource(drawable("ic_location_pin"));
        pin.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(pin, new LinearLayout.LayoutParams(dp(58), dp(58)));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setPadding(dp(10), 0, 0, 0);
        copy.addView(text("Lokasi saya", 12, "#718096", false));
        locationText = text("Memuat...", 16, "#0B3A78", true);
        locationText.setSingleLine(true);
        copy.addView(locationText);
        card.addView(copy, new LinearLayout.LayoutParams(dp(112), -2));

        row.addView(card, new LinearLayout.LayoutParams(dp(184), dp(96)));
    }

    private void buildWalletCard() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(Shape.gradient("#075EF4", "#22A4FF", dp(28)));
        frame.setElevation(dp(5));
        LinearLayout.LayoutParams frameLp = new LinearLayout.LayoutParams(-1, dp(236));
        frameLp.setMargins(0, dp(22), 0, dp(24));
        content.addView(frame, frameLp);

        ImageView art = new ImageView(this);
        art.setImageResource(drawable("img_wallet_transiva"));
        art.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams artLp = new FrameLayout.LayoutParams(dp(190), dp(190));
        artLp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        frame.addView(art, artLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(16));
        frame.addView(card, new FrameLayout.LayoutParams(-1, -1));

        card.addView(text("Transiva Pay", 18, "#FFFFFF", true));
        TextView sub = text("Saldo Anda", 14, "#EAF4FF", false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(8), 0, 0);
        card.addView(sub, subLp);

        balanceText = text("Memuat saldo...", 31, "#FFFFFF", true);
        balanceText.setSingleLine(true);
        LinearLayout.LayoutParams balLp = new LinearLayout.LayoutParams(-1, -2);
        balLp.setMargins(0, dp(2), 0, dp(12));
        card.addView(balanceText, balLp);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        actions.addView(walletAction("＋", "Isi Saldo", () -> startActivity(new Intent(this, CustomerTopUpActivity.class))));
        actions.addView(walletAction("⇄", "Transfer", () -> Toast.makeText(this, "Fitur transfer segera tersedia", Toast.LENGTH_SHORT).show()));
        actions.addView(walletAction("◷", "Riwayat", () -> startActivity(new Intent(this, CustomerHistoryActivity.class))));
    }

    private View walletAction(String symbol, String label, Runnable action) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        item.setPadding(0, 0, dp(24), 0);
        TextView button = text(symbol, 28, "#0B7CFF", false);
        button.setGravity(Gravity.CENTER);
        button.setBackground(Shape.round("#FFFFFF", dp(17)));
        item.addView(button, new LinearLayout.LayoutParams(dp(58), dp(58)));
        TextView caption = text(label, 12, "#FFFFFF", true);
        LinearLayout.LayoutParams capLp = new LinearLayout.LayoutParams(-2, -2);
        capLp.setMargins(0, dp(6), 0, 0);
        item.addView(caption, capLp);
        item.setOnClickListener(v -> action.run());
        return item;
    }

    private void buildPromoSection() {
        content.addView(sectionHeader("Promo Hari Ini", "Lihat semua  ›"));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        promoTrack = new LinearLayout(this);
        promoTrack.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(promoTrack, new HorizontalScrollView.LayoutParams(-2, -2));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(-1, dp(176));
        scrollLp.setMargins(0, dp(10), 0, dp(8));
        content.addView(scroll, scrollLp);

        LinearLayout dots = new LinearLayout(this);
        dots.setGravity(Gravity.CENTER);
        for (int i = 0; i < 4; i++) {
            View dot = new View(this);
            dot.setBackground(Shape.round(i == 0 ? "#0B7CFF" : "#CBD5E1", dp(5)));
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(9), dp(9));
            dotLp.setMargins(dp(4), 0, dp(4), 0);
            dots.addView(dot, dotLp);
        }
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(-1, dp(18));
        dotsLp.setMargins(0, 0, 0, dp(20));
        content.addView(dots, dotsLp);
    }

    private void renderPromos(List<Promo> promos) {
        promoTrack.removeAllViews();
        if (promos == null || promos.isEmpty()) {
            promoTrack.addView(promoBanner(new Promo("DISKON 20%", "TransRide & TransCar\nHemat perjalananmu hari ini!", "JALAN20"), 0));
            return;
        }
        int count = Math.min(promos.size(), 4);
        for (int i = 0; i < count; i++) promoTrack.addView(promoBanner(promos.get(i), i));
    }

    private View promoBanner(Promo promo, int index) {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Shape.gradient(index % 2 == 0 ? "#0759E8" : "#006EDC", index % 2 == 0 ? "#099FE8" : "#18B5FF", dp(22)));
        card.setElevation(dp(3));

        ImageView image = new ImageView(this);
        image.setImageResource(drawable("img_promo_vehicle"));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(dp(200), -1);
        imgLp.gravity = Gravity.END;
        card.addView(image, imgLp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(12), dp(14));
        card.addView(box, new FrameLayout.LayoutParams(-1, -1));
        box.addView(text(promo.title, 27, "#FFFFFF", true));
        TextView desc = text(promo.description, 15, "#FFFFFF", false);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(dp(220), -2);
        descLp.setMargins(0, dp(4), 0, dp(10));
        box.addView(desc, descLp);
        TextView code = text("Kode: " + promo.code, 13, "#FFFFFF", true);
        code.setPadding(dp(10), dp(6), dp(10), dp(6));
        code.setBackground(Shape.roundStroke("#0A6FEA", "#FFFFFF", dp(10), 1));
        box.addView(code, new LinearLayout.LayoutParams(-2, -2));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(342), dp(166));
        lp.setMargins(0, 0, dp(12), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private void buildServiceSection() {
        content.addView(sectionHeader("Layanan Transiva", ""));
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams gridLp = new LinearLayout.LayoutParams(-1, -2);
        gridLp.setMargins(0, dp(10), 0, dp(22));
        content.addView(grid, gridLp);

        grid.addView(serviceRow(
                service("TransRide", "ic_service_ride", TransRideActivity.class),
                service("TransCar", "ic_service_car", PassengerCarActivity.class),
                service("TransFood", "ic_service_food", TransFoodActivity.class),
                service("TransTour", "ic_service_tour", TranstourActivity.class)
        ));
        grid.addView(serviceRow(
                service("Laundry", "ic_service_laundry", TransLaundryActivity.class),
                service("Pickup", "ic_service_pickup", TransPickupActivity.class),
                serviceAction("TransMart", "ic_service_mart", () -> Toast.makeText(this, "TransMart segera tersedia", Toast.LENGTH_SHORT).show()),
                serviceAction("Lainnya", "ic_service_more", () -> Toast.makeText(this, "Layanan lainnya segera tersedia", Toast.LENGTH_SHORT).show())
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
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(0, 0, 0, dp(9));
        row.setLayoutParams(rowLp);
        return row;
    }

    private View service(String title, String icon, Class<?> destination) {
        return serviceAction(title, icon, () -> startActivity(new Intent(this, destination)));
    }

    private View serviceAction(String title, String icon, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(6), dp(8), dp(6), dp(8));
        card.setBackground(Shape.roundStroke("#FFFFFF", "#EDF2F7", dp(20), 1));
        card.setElevation(dp(2));
        ImageView image = new ImageView(this);
        image.setImageResource(drawable(icon));
        image.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        card.addView(image, new LinearLayout.LayoutParams(dp(53), dp(53)));
        TextView label = text(title, 11, "#0B3A78", true);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
        labelLp.setMargins(0, dp(7), 0, 0);
        card.addView(label, labelLp);
        card.setOnClickListener(v -> action.run());
        return card;
    }

    private void buildOrderSection() {
        FrameLayout card = new FrameLayout(this);
        card.setBackground(Shape.roundStroke("#FFFFFF", "#EDF2F7", dp(22), 1));
        card.setElevation(dp(2));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, dp(124));
        cardLp.setMargins(0, 0, 0, dp(24));
        content.addView(card, cardLp);

        ImageView illustration = new ImageView(this);
        illustration.setImageResource(drawable("img_order_empty"));
        illustration.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams artLp = new FrameLayout.LayoutParams(dp(150), -1);
        artLp.gravity = Gravity.END;
        card.addView(illustration, artLp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(16), dp(160), dp(14));
        card.addView(box, new FrameLayout.LayoutParams(-1, -1));
        box.addView(text("Status Pesanan", 18, "#0B3A78", true));
        orderText = text("Belum ada pesanan aktif", 14, "#718096", false);
        LinearLayout.LayoutParams orderLp = new LinearLayout.LayoutParams(-1, -2);
        orderLp.setMargins(0, dp(8), 0, 0);
        box.addView(orderText, orderLp);
        TextView hint = text("Yuk, pesan layanan Transiva sekarang!", 12, "#8AA0B8", false);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(4), 0, 0);
        box.addView(hint, hintLp);
    }

    private void buildRecommendationSection() {
        content.addView(sectionHeader("Rekomendasi untukmu", "Lihat semua  ›"));
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(-2, -2));
        row.addView(recommendation("Diskon hingga\n50%", "#E83A20", "Burger favorit"));
        row.addView(recommendation("Wisata Populer\nPalu", "#2196F3", "Jelajahi sekarang"));
        row.addView(recommendation("Cashback\nRp15.000", "#6D42D8", "TransFood"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(130));
        lp.setMargins(0, dp(10), 0, 0);
        content.addView(scroll, lp);
    }

    private View recommendation(String title, String color, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(Shape.round(color, dp(20)));
        card.addView(text(title, 18, "#FFFFFF", true));
        TextView sub = text(subtitle, 12, "#FFFFFF", false);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(-1, -2);
        subLp.setMargins(0, dp(8), 0, 0);
        card.addView(sub, subLp);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(190), dp(118));
        lp.setMargins(0, 0, dp(12), 0);
        card.setLayoutParams(lp);
        return card;
    }

    private LinearLayout sectionHeader(String title, String action) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(text(title, 20, "#0B3A78", true), new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null && !action.isEmpty()) header.addView(text(action, 13, "#0B7CFF", true));
        return header;
    }

    private View buildBottomNavigation() {
        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setGravity(Gravity.CENTER);
        nav.setPadding(dp(6), dp(5), dp(6), dp(5));
        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(10));
        nav.addView(navItem("Beranda", "ic_nav_home", null, true), navLp());
        nav.addView(navItem("Aktivitas", "ic_nav_activity", CustomerHistoryActivity.class, false), navLp());
        nav.addView(navItem("Pesan", "ic_nav_chat", CustomerChatActivity.class, false), navLp());
        nav.addView(navItem("Pay", "ic_nav_wallet", CustomerTopUpActivity.class, false), navLp());
        nav.addView(navItem("Akun", "ic_nav_profile", ProfileActivity.class, false), navLp());
        return nav;
    }

    private LinearLayout.LayoutParams navLp() { return new LinearLayout.LayoutParams(0, -1, 1); }

    private View navItem(String label, String icon, Class<?> target, boolean active) {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        ImageView image = new ImageView(this);
        image.setImageResource(drawable(icon));
        image.setAlpha(active ? 1f : .62f);
        item.addView(image, new LinearLayout.LayoutParams(dp(27), dp(27)));
        TextView title = text(label, 10, active ? "#0B7CFF" : "#64748B", active);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(4), 0, 0);
        item.addView(title, titleLp);
        if (target != null) item.setOnClickListener(v -> startActivity(new Intent(this, target)));
        return item;
    }

    private void loadLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
            return;
        }
        try {
            LocationManager manager = (LocationManager) getSystemService(LOCATION_SERVICE);
            if (manager == null) return;
            boolean gps = manager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            boolean network = manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
            if (!gps && !network) {
                locationText.setText("Aktifkan GPS");
                locationText.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
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
        } catch (Exception error) {
            locationText.setText("Lokasi gagal");
        }
    }

    private void resolveLocation(Location location) {
        new Thread(() -> {
            String result = "Lokasi saya";
            try {
                List<Address> list = new Geocoder(this, new Locale("id", "ID")).getFromLocation(location.getLatitude(), location.getLongitude(), 1);
                if (list != null && !list.isEmpty()) {
                    Address address = list.get(0);
                    result = first(address.getSubLocality(), address.getLocality(), address.getSubAdminArea(), "Lokasi saya");
                }
                new SessionManager(this).saveLastLocation(String.valueOf(location.getLatitude()), String.valueOf(location.getLongitude()));
            } catch (Exception ignored) {}
            String finalResult = result;
            runOnUiThread(() -> locationText.setText(finalResult));
        }).start();
    }

    @Override public void showLoading(boolean visible) { if (loading != null) loading.setVisibility(visible ? View.VISIBLE : View.GONE); }
    @Override public void showDashboard(DashboardState state) { balanceText.setText(rupiah(state.balance)); orderText.setText(state.activeOrderText); renderPromos(state.promos); }
    @Override public void showError(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }

    private TextView text(String value, int sp, String color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(Color.parseColor(color));
        view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private int drawable(String name) { return getResources().getIdentifier(name, "drawable", getPackageName()); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private String rupiah(double amount) { return NumberFormat.getCurrencyInstance(new Locale("id", "ID")).format(amount); }

    private String first(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim())) return value.trim();
        }
        return "";
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadLocation();
        else if (requestCode == REQ_LOCATION) locationText.setText("Izin ditolak");
    }
}
