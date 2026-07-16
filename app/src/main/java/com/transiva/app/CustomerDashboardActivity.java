package com.transiva.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
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

import org.json.JSONObject;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class CustomerDashboardActivity extends Activity
        implements CustomerDashboardContract.View {

    private static final int REQ_LOCATION = 1201;
    private static final long PROMO_INTERVAL_MS = 4500L;
    private static final int PROMO_CARD_WIDTH_DP = 275;
    private static final int PROMO_CARD_GAP_DP = 9;

    private final Handler uiHandler =
            new Handler(Looper.getMainLooper());

    private CustomerDashboardPresenter presenter;

    private LinearLayout content;
    private TextView locationText;
    private TextView balanceText;
    private TextView orderText;
    private TextView orderHint;
    private TextView verificationText;

    private LinearLayout promoSection;
    private TextView promoHeader;
    private TextView promoEmptyText;
    private HorizontalScrollView promoScroll;
    private LinearLayout promoTrack;
    private LinearLayout promoDots;

    private ProgressBar loading;
    private RecommendationSectionController recommendationController;

    private int promoCount;
    private int activePromoIndex;

    private String username = "User";
    private int userId;

    private final Runnable promoAutoRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (
                            promoCount <= 1
                                    || promoScroll == null
                                    || promoScroll.getVisibility()
                                    != View.VISIBLE
                    ) {
                        return;
                    }

                    activePromoIndex =
                            (activePromoIndex + 1) % promoCount;

                    int target =
                            activePromoIndex
                                    * (
                                    dp(PROMO_CARD_WIDTH_DP)
                                            + dp(PROMO_CARD_GAP_DP)
                            );

                    promoScroll.smoothScrollTo(target, 0);
                    updatePromoDots(activePromoIndex);

                    uiHandler.postDelayed(
                            this,
                            PROMO_INTERVAL_MS
                    );
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().setStatusBarColor(
                Color.parseColor("#0B7CFF")
        );

        getWindow().setNavigationBarColor(
                Color.parseColor("#071426")
        );

        readSession();

        presenter =
                new CustomerDashboardPresenter(
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

        SessionValidationClient.validate(this);

        if (presenter != null) {
            presenter.refresh(username, userId);
        }

        startPromoAutoSlide();

        if (recommendationController != null) {
            recommendationController.refresh();
        }
    }

    @Override
    protected void onPause() {
        stopPromoAutoSlide();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        stopPromoAutoSlide();

        if (presenter != null) {
            presenter.destroy();
        }

        super.onDestroy();
    }

    private void readSession() {
        try {
            SessionManager session =
                    new SessionManager(this);

            username = first(
                    session.getUsername(),
                    session.getName(),
                    "User"
            );

            try {
                userId = Integer.parseInt(
                        first(
                                session.getId(),
                                session.getUserId(),
                                "0"
                        )
                );
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
        page.setBackgroundColor(
                Color.parseColor("#F7FAFF")
        );

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);

        page.addView(
                shell,
                new FrameLayout.LayoutParams(-1, -1)
        );

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);

        shell.addView(
                scroll,
                new LinearLayout.LayoutParams(-1, 0, 1)
        );

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(
                dp(14),
                dp(12),
                dp(14),
                dp(20)
        );

        scroll.addView(
                content,
                new ScrollView.LayoutParams(-1, -2)
        );

        buildHeader();
        buildWalletCard();
        buildPromoSection();
        buildServiceSection();
        buildOrderSection();
        buildRecommendationSection();

        shell.addView(
                buildBottomNavigation(),
                new LinearLayout.LayoutParams(-1, dp(64))
        );

        loading = new ProgressBar(this);
        loading.setVisibility(View.GONE);

        FrameLayout.LayoutParams loadingLp =
                new FrameLayout.LayoutParams(
                        dp(42),
                        dp(42)
                );

        loadingLp.gravity = Gravity.CENTER;
        page.addView(loading, loadingLp);

        return page;
    }

    private void buildHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        content.addView(
                row,
                new LinearLayout.LayoutParams(-1, -2)
        );

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);

        row.addView(
                left,
                new LinearLayout.LayoutParams(0, -2, 1)
        );

        left.addView(
                text(
                        "Selamat datang 👋",
                        12,
                        "#64748B",
                        false
                )
        );

        TextView name = text(
                username.toLowerCase(Locale.getDefault()),
                23,
                "#0B3A78",
                true
        );

        LinearLayout.LayoutParams nameLp =
                new LinearLayout.LayoutParams(-1, -2);

        nameLp.setMargins(
                0,
                dp(1),
                0,
                dp(4)
        );

        left.addView(name, nameLp);

        boolean verified = isVerifiedUser();

        verificationText = text(
                verified
                        ? "✓ Terverifikasi"
                        : "• Belum Terverifikasi",
                10,
                verified
                        ? "#0E9F4B"
                        : "#D97706",
                true
        );

        verificationText.setPadding(
                dp(8),
                dp(4),
                dp(8),
                dp(4)
        );

        verificationText.setBackground(
                Shape.round(
                        verified
                                ? "#EAFBF1"
                                : "#FFF7E6",
                        dp(12)
                )
        );

        left.addView(
                verificationText,
                new LinearLayout.LayoutParams(-2, -2)
        );

        LinearLayout locationCard =
                new LinearLayout(this);

        locationCard.setOrientation(
                LinearLayout.HORIZONTAL
        );

        locationCard.setGravity(
                Gravity.CENTER_VERTICAL
        );

        locationCard.setPadding(
                dp(7),
                dp(6),
                dp(8),
                dp(6)
        );

        locationCard.setBackground(
                Shape.roundStroke(
                        "#FFFFFF",
                        "#DFEAF6",
                        dp(16),
                        1
                )
        );

        locationCard.setElevation(dp(2));
        locationCard.setOnClickListener(
                view -> loadLocation()
        );

        ImageView pin = new ImageView(this);
        pin.setImageResource(
                drawable("ic_location_pin")
        );

        pin.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        locationCard.addView(
                pin,
                new LinearLayout.LayoutParams(
                        dp(28),
                        dp(28)
                )
        );

        locationText = text(
                "Memuat...",
                10,
                "#0B3A78",
                true
        );

        locationText.setSingleLine(true);
        locationText.setPadding(
                dp(4),
                0,
                0,
                0
        );

        locationCard.addView(
                locationText,
                new LinearLayout.LayoutParams(
                        dp(70),
                        -2
                )
        );

        row.addView(
                locationCard,
                new LinearLayout.LayoutParams(
                        dp(110),
                        dp(48)
                )
        );
    }

    private boolean isVerifiedUser() {
        try {
            SharedPreferences preferences =
                    getSharedPreferences(
                            "transiva_native_session",
                            MODE_PRIVATE
                    );

            JSONObject user = new JSONObject(
                    preferences.getString(
                            "raw_user",
                            "{}"
                    )
            );

            return user.optInt(
                    "email_verified",
                    0
            ) == 1
                    || user.optInt(
                    "verified_by_admin",
                    0
            ) == 1
                    || user.optBoolean(
                    "verified",
                    false
            )
                    || user.optBoolean(
                    "is_verified",
                    false
            );

        } catch (Exception ignored) {
            return false;
        }
    }

    private void buildWalletCard() {
        FrameLayout frame = new FrameLayout(this);

        frame.setBackground(
                Shape.gradient(
                        "#075EF4",
                        "#22A4FF",
                        dp(22)
                )
        );

        frame.setElevation(dp(3));

        LinearLayout.LayoutParams frameLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(128)
                );

        frameLp.setMargins(
                0,
                dp(14),
                0,
                dp(16)
        );

        content.addView(frame, frameLp);

        ImageView art = new ImageView(this);
        art.setImageResource(
                drawable("img_wallet_transiva")
        );

        art.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        FrameLayout.LayoutParams artLp =
                new FrameLayout.LayoutParams(
                        dp(112),
                        dp(112)
                );

        artLp.gravity =
                Gravity.END | Gravity.CENTER_VERTICAL;

        frame.addView(art, artLp);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);

        card.setPadding(
                dp(16),
                dp(13),
                dp(16),
                dp(10)
        );

        frame.addView(
                card,
                new FrameLayout.LayoutParams(-1, -1)
        );

        card.addView(
                text(
                        "Transiva Pay",
                        15,
                        "#FFFFFF",
                        true
                )
        );

        card.addView(
                text(
                        "Saldo Anda",
                        11,
                        "#EAF4FF",
                        false
                )
        );

        balanceText = text(
                "Memuat saldo...",
                25,
                "#FFFFFF",
                true
        );

        balanceText.setSingleLine(true);

        LinearLayout.LayoutParams balanceLp =
                new LinearLayout.LayoutParams(-1, -2);

        balanceLp.setMargins(
                0,
                dp(1),
                0,
                0
        );

        card.addView(balanceText, balanceLp);

    }

    private void openBalanceTransactions() {
        String[] candidates = {
                "com.transiva.app.CustomerBalanceHistoryActivity",
                "com.transiva.app.BalanceTransactionHistoryActivity",
                "com.transiva.app.CustomerTransactionHistoryActivity"
        };

        for (String className : candidates) {
            try {
                startActivity(
                        new Intent(
                                this,
                                Class.forName(className)
                        )
                );
                return;

            } catch (Exception ignored) {
            }
        }

        Toast.makeText(
                this,
                "Riwayat transaksi saldo sedang disiapkan",
                Toast.LENGTH_SHORT
        ).show();
    }

    /**
     * Promo section memakai tinggi WRAP_CONTENT.
     *
     * Saat tidak ada promo:
     * - banner disembunyikan;
     * - dots disembunyikan;
     * - hanya teks kecil "Belum ada promo hari ini";
     * - Layanan Transiva langsung naik ke bawahnya.
     */
    private void buildPromoSection() {
        promoSection = new LinearLayout(this);
        promoSection.setOrientation(
                LinearLayout.VERTICAL
        );

        LinearLayout.LayoutParams sectionLp =
                new LinearLayout.LayoutParams(-1, -2);

        sectionLp.setMargins(
                0,
                0,
                0,
                dp(12)
        );

        content.addView(promoSection, sectionLp);

        promoHeader = text(
                "Promo Hari Ini",
                16,
                "#0B3A78",
                true
        );

        promoSection.addView(
                promoHeader,
                new LinearLayout.LayoutParams(-1, -2)
        );

        promoEmptyText = text(
                "Belum ada promo hari ini",
                12,
                "#7B8DA3",
                false
        );

        promoEmptyText.setGravity(
                Gravity.CENTER_VERTICAL
        );

        promoEmptyText.setPadding(
                dp(14),
                dp(11),
                dp(14),
                dp(11)
        );

        promoEmptyText.setBackground(
                Shape.roundStroke(
                        "#FFFFFF",
                        "#E3ECF7",
                        dp(14),
                        1
                )
        );

        LinearLayout.LayoutParams emptyLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(46)
                );

        emptyLp.setMargins(
                0,
                dp(7),
                0,
                0
        );

        promoSection.addView(
                promoEmptyText,
                emptyLp
        );

        promoScroll =
                new HorizontalScrollView(this);

        promoScroll.setHorizontalScrollBarEnabled(
                false
        );

        promoScroll.setClipToPadding(false);
        promoScroll.setVisibility(View.GONE);

        promoTrack = new LinearLayout(this);
        promoTrack.setOrientation(
                LinearLayout.HORIZONTAL
        );

        promoScroll.addView(
                promoTrack,
                new HorizontalScrollView.LayoutParams(
                        -2,
                        -2
                )
        );

        LinearLayout.LayoutParams scrollLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(126)
                );

        scrollLp.setMargins(
                0,
                dp(7),
                0,
                dp(5)
        );

        promoSection.addView(
                promoScroll,
                scrollLp
        );

        promoDots = new LinearLayout(this);
        promoDots.setGravity(Gravity.CENTER);
        promoDots.setVisibility(View.GONE);

        LinearLayout.LayoutParams dotsLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(14)
                );

        promoSection.addView(
                promoDots,
                dotsLp
        );

        promoScroll.setOnScrollChangeListener(
                (
                        view,
                        scrollX,
                        scrollY,
                        oldX,
                        oldY
                ) -> {
                    if (promoCount <= 1) {
                        return;
                    }

                    int width =
                            dp(PROMO_CARD_WIDTH_DP)
                                    + dp(PROMO_CARD_GAP_DP);

                    int index = Math.max(
                            0,
                            Math.min(
                                    promoCount - 1,
                                    Math.round(
                                            (float) scrollX
                                                    / width
                                    )
                            )
                    );

                    if (index != activePromoIndex) {
                        activePromoIndex = index;
                        updatePromoDots(index);
                    }

                    stopPromoAutoSlide();

                    uiHandler.postDelayed(
                            promoAutoRunnable,
                            PROMO_INTERVAL_MS
                    );
                }
        );
    }

    private void renderPromos(List<Promo> promos) {
        stopPromoAutoSlide();

        promoTrack.removeAllViews();
        promoDots.removeAllViews();

        promoCount =
                promos == null
                        ? 0
                        : Math.min(2, promos.size());

        if (promoCount == 0) {
            activePromoIndex = 0;

            promoEmptyText.setVisibility(
                    View.VISIBLE
            );

            promoScroll.setVisibility(
                    View.GONE
            );

            promoDots.setVisibility(
                    View.GONE
            );

            promoScroll.scrollTo(0, 0);

            // Tidak ada fixed-height banner,
            // sehingga Layanan Transiva langsung naik.
            return;
        }

        promoEmptyText.setVisibility(View.GONE);
        promoScroll.setVisibility(View.VISIBLE);

        promoDots.setVisibility(
                promoCount > 1
                        ? View.VISIBLE
                        : View.GONE
        );

        for (int i = 0; i < promoCount; i++) {
            promoTrack.addView(
                    promoBanner(promos.get(i))
            );
        }

        if (promoCount > 1) {
            for (int i = 0; i < promoCount; i++) {
                View dot = new View(this);

                LinearLayout.LayoutParams dotLp =
                        new LinearLayout.LayoutParams(
                                dp(7),
                                dp(7)
                        );

                dotLp.setMargins(
                        dp(3),
                        0,
                        dp(3),
                        0
                );

                promoDots.addView(dot, dotLp);
            }
        }

        activePromoIndex = 0;
        updatePromoDots(0);

        promoScroll.post(
                () -> promoScroll.scrollTo(0, 0)
        );

        startPromoAutoSlide();
    }

    private View promoBanner(Promo promo) {
        FrameLayout card = new FrameLayout(this);

        card.setBackground(
                Shape.gradient(
                        promo.themeStart,
                        promo.themeEnd,
                        dp(17)
                )
        );

        card.setElevation(dp(2));

        ImageView image = new ImageView(this);

        RemoteImageLoader.loadCenterCrop(
                image,
                promo.imageUrl,
                drawable("img_promo_vehicle")
        );

        FrameLayout.LayoutParams imageLp =
                new FrameLayout.LayoutParams(
                        dp(132),
                        -1
                );

        imageLp.gravity = Gravity.END;
        card.addView(image, imageLp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        box.setPadding(
                dp(13),
                dp(10),
                dp(8),
                dp(8)
        );

        card.addView(
                box,
                new FrameLayout.LayoutParams(-1, -1)
        );

        box.addView(
                text(
                        promo.title,
                        20,
                        "#FFFFFF",
                        true
                )
        );

        TextView description = text(
                promo.description,
                11,
                "#FFFFFF",
                false
        );

        LinearLayout.LayoutParams descriptionLp =
                new LinearLayout.LayoutParams(
                        dp(165),
                        -2
                );

        descriptionLp.setMargins(
                0,
                dp(2),
                0,
                dp(6)
        );

        box.addView(
                description,
                descriptionLp
        );

        if (
                promo.code != null
                        && !promo.code.trim().isEmpty()
        ) {
            TextView code = text(
                    "Kode: " + promo.code,
                    10,
                    "#FFFFFF",
                    true
            );

            code.setPadding(
                    dp(7),
                    dp(4),
                    dp(7),
                    dp(4)
            );

            code.setBackground(
                    Shape.roundStroke(
                            "#0A6FEA",
                            "#FFFFFF",
                            dp(8),
                            1
                    )
            );

            box.addView(
                    code,
                    new LinearLayout.LayoutParams(-2, -2)
            );
        }

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(
                        dp(PROMO_CARD_WIDTH_DP),
                        dp(118)
                );

        cardLp.setMargins(
                0,
                0,
                dp(PROMO_CARD_GAP_DP),
                0
        );

        card.setLayoutParams(cardLp);

        return card;
    }

    private void updatePromoDots(int selected) {
        if (promoDots == null) {
            return;
        }

        for (
                int i = 0;
                i < promoDots.getChildCount();
                i++
        ) {
            promoDots
                    .getChildAt(i)
                    .setBackground(
                            Shape.round(
                                    i == selected
                                            ? "#0B7CFF"
                                            : "#CBD5E1",
                                    dp(4)
                            )
                    );
        }
    }

    private void startPromoAutoSlide() {
        stopPromoAutoSlide();

        if (
                promoCount > 1
                        && promoScroll != null
                        && promoScroll.getVisibility()
                        == View.VISIBLE
        ) {
            uiHandler.postDelayed(
                    promoAutoRunnable,
                    PROMO_INTERVAL_MS
            );
        }
    }

    private void stopPromoAutoSlide() {
        uiHandler.removeCallbacks(
                promoAutoRunnable
        );
    }

    private void buildServiceSection() {
        TextView header = text(
                "Layanan Transiva",
                16,
                "#0B3A78",
                true
        );

        content.addView(
                header,
                new LinearLayout.LayoutParams(-1, -2)
        );

        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams gridLp =
                new LinearLayout.LayoutParams(-1, -2);

        gridLp.setMargins(
                0,
                dp(7),
                0,
                dp(14)
        );

        content.addView(grid, gridLp);

        grid.addView(
                serviceRow(
                        service(
                                "TransRide",
                                "ic_service_ride",
                                TransRideActivity.class
                        ),
                        service(
                                "TransCar",
                                "ic_service_car",
                                PassengerCarActivity.class
                        ),
                        service(
                                "TransFood",
                                "ic_service_food",
                                TransFoodActivity.class
                        ),
                        service(
                                "TransTour",
                                "ic_service_tour",
                                TranstourActivity.class
                        )
                )
        );

        grid.addView(
                serviceRow(
                        service(
                                "Laundry",
                                "ic_service_laundry",
                                TransLaundryActivity.class
                        ),
                        service(
                                "Pickup",
                                "ic_service_pickup",
                                TransPickupActivity.class
                        ),
                        serviceAction(
                                "TransMart",
                                "ic_service_mart",
                                () -> Toast.makeText(
                                        this,
                                        "TransMart segera tersedia",
                                        Toast.LENGTH_SHORT
                                ).show()
                        ),
                        serviceAction(
                                "Lainnya",
                                "ic_service_more",
                                () -> Toast.makeText(
                                        this,
                                        "Layanan lainnya segera tersedia",
                                        Toast.LENGTH_SHORT
                                ).show()
                        )
                )
        );
    }

    private View serviceRow(View... items) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(
                LinearLayout.HORIZONTAL
        );

        for (int i = 0; i < items.length; i++) {
            LinearLayout.LayoutParams itemLp =
                    new LinearLayout.LayoutParams(
                            0,
                            dp(84),
                            1
                    );

            if (i > 0) {
                itemLp.setMargins(
                        dp(6),
                        0,
                        0,
                        0
                );
            }

            row.addView(items[i], itemLp);
        }

        LinearLayout.LayoutParams rowLp =
                new LinearLayout.LayoutParams(-1, -2);

        rowLp.setMargins(
                0,
                0,
                0,
                dp(6)
        );

        row.setLayoutParams(rowLp);
        return row;
    }

    private View service(
            String title,
            String icon,
            Class<?> destination
    ) {
        return serviceAction(
                title,
                icon,
                () -> startActivity(
                        new Intent(
                                this,
                                destination
                        )
                )
        );
    }

    private View serviceAction(
            String title,
            String icon,
            Runnable action
    ) {
        LinearLayout card =
                new LinearLayout(this);

        card.setOrientation(
                LinearLayout.VERTICAL
        );

        card.setGravity(Gravity.CENTER);

        card.setPadding(
                dp(4),
                dp(5),
                dp(4),
                dp(5)
        );

        card.setBackground(
                Shape.roundStroke(
                        "#FFFFFF",
                        "#EDF2F7",
                        dp(15),
                        1
                )
        );

        card.setElevation(dp(1));

        ImageView image = new ImageView(this);
        image.setImageResource(
                drawable(icon)
        );

        image.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        card.addView(
                image,
                new LinearLayout.LayoutParams(
                        dp(38),
                        dp(38)
                )
        );

        TextView label = text(
                title,
                9,
                "#0B3A78",
                true
        );

        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);

        LinearLayout.LayoutParams labelLp =
                new LinearLayout.LayoutParams(-1, -2);

        labelLp.setMargins(
                0,
                dp(4),
                0,
                0
        );

        card.addView(label, labelLp);

        card.setOnClickListener(
                view -> action.run()
        );

        return card;
    }

    private void buildOrderSection() {
        FrameLayout card = new FrameLayout(this);

        card.setBackground(
                Shape.roundStroke(
                        "#FFFFFF",
                        "#EDF2F7",
                        dp(17),
                        1
                )
        );

        card.setElevation(dp(1));

        LinearLayout.LayoutParams cardLp =
                new LinearLayout.LayoutParams(
                        -1,
                        dp(92)
                );

        cardLp.setMargins(
                0,
                0,
                0,
                dp(15)
        );

        content.addView(card, cardLp);

        ImageView illustration =
                new ImageView(this);

        illustration.setImageResource(
                drawable("img_order_empty")
        );

        illustration.setScaleType(
                ImageView.ScaleType.CENTER_INSIDE
        );

        FrameLayout.LayoutParams artLp =
                new FrameLayout.LayoutParams(
                        dp(105),
                        -1
                );

        artLp.gravity = Gravity.END;
        card.addView(illustration, artLp);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        box.setPadding(
                dp(14),
                dp(12),
                dp(110),
                dp(10)
        );

        card.addView(
                box,
                new FrameLayout.LayoutParams(-1, -1)
        );

        box.addView(
                text(
                        "Status Pesanan",
                        15,
                        "#0B3A78",
                        true
                )
        );

        orderText = text(
                "Belum ada pesanan aktif",
                11,
                "#718096",
                false
        );

        LinearLayout.LayoutParams orderLp =
                new LinearLayout.LayoutParams(-1, -2);

        orderLp.setMargins(
                0,
                dp(5),
                0,
                0
        );

        box.addView(orderText, orderLp);

        orderHint = text(
                "Yuk, pesan layanan Transiva sekarang!",
                9,
                "#8AA0B8",
                false
        );

        LinearLayout.LayoutParams hintLp =
                new LinearLayout.LayoutParams(-1, -2);

        hintLp.setMargins(
                0,
                dp(2),
                0,
                0
        );

        box.addView(orderHint, hintLp);
    }

    private void buildRecommendationSection() {
        recommendationController =
                new RecommendationSectionController(this);

        content.addView(
                recommendationController.buildView(),
                new LinearLayout.LayoutParams(-1, -2)
        );
    }

    private View buildBottomNavigation() {
        LinearLayout nav =
                new LinearLayout(this);

        nav.setOrientation(
                LinearLayout.HORIZONTAL
        );

        nav.setGravity(Gravity.CENTER);

        nav.setPadding(
                dp(5),
                dp(4),
                dp(5),
                dp(4)
        );

        nav.setBackgroundColor(Color.WHITE);
        nav.setElevation(dp(8));

        nav.addView(
                navItem(
                        "Beranda",
                        "ic_nav_home",
                        null,
                        true
                ),
                navLp()
        );

        nav.addView(
                navItem(
                        "Aktivitas",
                        "ic_nav_activity",
                        CustomerHistoryActivity.class,
                        false
                ),
                navLp()
        );

        nav.addView(
                navItem(
                        "Pesan",
                        "ic_nav_chat",
                        CustomerChatActivity.class,
                        false
                ),
                navLp()
        );

        nav.addView(
                navAction(
                        "Transaksi",
                        "ic_nav_wallet",
                        this::openBalanceTransactions,
                        false
                ),
                navLp()
        );

        nav.addView(
                navItem(
                        "Akun",
                        "ic_nav_profile",
                        ProfileActivity.class,
                        false
                ),
                navLp()
        );

        return nav;
    }

    private LinearLayout.LayoutParams navLp() {
        return new LinearLayout.LayoutParams(
                0,
                -1,
                1
        );
    }

    private View navItem(
            String label,
            String icon,
            Class<?> target,
            boolean active
    ) {
        return navAction(
                label,
                icon,
                target == null
                        ? null
                        : () -> startActivity(
                                new Intent(
                                        this,
                                        target
                                )
                        ),
                active
        );
    }

    private View navAction(
            String label,
            String icon,
            Runnable action,
            boolean active
    ) {
        LinearLayout item =
                new LinearLayout(this);

        item.setOrientation(
                LinearLayout.VERTICAL
        );

        item.setGravity(Gravity.CENTER);

        ImageView image = new ImageView(this);
        image.setImageResource(drawable(icon));

        image.setAlpha(
                active ? 1f : 0.62f
        );

        item.addView(
                image,
                new LinearLayout.LayoutParams(
                        dp(22),
                        dp(22)
                )
        );

        TextView title = text(
                label,
                9,
                active
                        ? "#0B7CFF"
                        : "#64748B",
                active
        );

        title.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams titleLp =
                new LinearLayout.LayoutParams(-1, -2);

        titleLp.setMargins(
                0,
                dp(2),
                0,
                0
        );

        item.addView(title, titleLp);

        if (action != null) {
            item.setOnClickListener(
                    view -> action.run()
            );
        }

        return item;
    }

    private void loadLocation() {
        if (
                checkSelfPermission(
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
                        &&
                checkSelfPermission(
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    REQ_LOCATION
            );
            return;
        }

        try {
            LocationManager manager =
                    (LocationManager)
                            getSystemService(
                                    LOCATION_SERVICE
                            );

            if (manager == null) {
                return;
            }

            boolean gps =
                    manager.isProviderEnabled(
                            LocationManager.GPS_PROVIDER
                    );

            boolean network =
                    manager.isProviderEnabled(
                            LocationManager.NETWORK_PROVIDER
                    );

            if (!gps && !network) {
                locationText.setText("GPS mati");

                locationText.setOnClickListener(
                        view -> startActivity(
                                new Intent(
                                        Settings.ACTION_LOCATION_SOURCE_SETTINGS
                                )
                        )
                );

                return;
            }

            String provider =
                    gps
                            ? LocationManager.GPS_PROVIDER
                            : LocationManager.NETWORK_PROVIDER;

            Location cached =
                    manager.getLastKnownLocation(provider);

            if (cached != null) {
                resolveLocation(cached);
            }

            manager.requestSingleUpdate(
                    provider,
                    new LocationListener() {
                        @Override
                        public void onLocationChanged(
                                Location location
                        ) {
                            resolveLocation(location);
                        }

                        @Override
                        public void onStatusChanged(
                                String provider,
                                int status,
                                Bundle extras
                        ) {
                        }

                        @Override
                        public void onProviderEnabled(
                                String provider
                        ) {
                        }

                        @Override
                        public void onProviderDisabled(
                                String provider
                        ) {
                        }
                    },
                    Looper.getMainLooper()
            );

        } catch (Exception error) {
            locationText.setText("Lokasi gagal");
        }
    }

    private void resolveLocation(Location location) {
        new Thread(
                () -> {
                    String result = "Lokasi saya";

                    try {
                        List<Address> addresses =
                                new Geocoder(
                                        this,
                                        new Locale("id", "ID")
                                ).getFromLocation(
                                        location.getLatitude(),
                                        location.getLongitude(),
                                        1
                                );

                        if (
                                addresses != null
                                        && !addresses.isEmpty()
                        ) {
                            Address address =
                                    addresses.get(0);

                            result = first(
                                    address.getSubLocality(),
                                    address.getLocality(),
                                    address.getSubAdminArea(),
                                    "Lokasi saya"
                            );
                        }

                        new SessionManager(this)
                                .saveLastLocation(
                                        String.valueOf(
                                                location.getLatitude()
                                        ),
                                        String.valueOf(
                                                location.getLongitude()
                                        )
                                );

                    } catch (Exception ignored) {
                    }

                    String finalResult = result;

                    runOnUiThread(
                            () -> locationText.setText(
                                    finalResult
                            )
                    );
                }
        ).start();
    }

    @Override
    public void showLoading(boolean visible) {
        if (loading != null) {
            loading.setVisibility(
                    visible
                            ? View.VISIBLE
                            : View.GONE
            );
        }
    }

    @Override
    public void showDashboard(
            DashboardState state
    ) {
        if (state == null) {
            return;
        }

        balanceText.setText(
                rupiah(state.balance)
        );

        String activeOrderText =
                first(
                        state.activeOrderText,
                        "Belum ada pesanan aktif"
                );

        orderText.setText(activeOrderText);

        boolean hasActiveOrder =
                isActiveOrderText(activeOrderText);

        if (orderHint != null) {
            orderHint.setVisibility(
                    hasActiveOrder
                            ? View.GONE
                            : View.VISIBLE
            );
        }

        renderPromos(state.promos);
    }

    @Override
    public void showError(String message) {
        // Saat dashboard gagal dimuat, promo tidak boleh meninggalkan
        // area kosong tinggi. Tampilkan empty state yang ringkas.
        renderPromos(null);

        Toast.makeText(
                this,
                first(
                        message,
                        "Dashboard gagal dimuat"
                ),
                Toast.LENGTH_SHORT
        ).show();
    }

    private boolean isActiveOrderText(
            String value
    ) {
        String normalized =
                first(value)
                        .trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (normalized.isEmpty()) {
            return false;
        }

        return !normalized.equals(
                "belum ada pesanan aktif"
        )
                && !normalized.equals(
                "tidak ada pesanan aktif"
        )
                && !normalized.equals(
                "belum ada order aktif"
        )
                && !normalized.equals(
                "tidak ada order aktif"
        )
                && !normalized.equals(
                "no active order"
        );
    }

    private TextView text(
            String value,
            int sp,
            String color,
            boolean bold
    ) {
        TextView view = new TextView(this);

        view.setText(
                value == null ? "" : value
        );

        view.setTextSize(sp);

        view.setTextColor(
                Color.parseColor(color)
        );

        view.setIncludeFontPadding(false);

        if (bold) {
            view.setTypeface(
                    Typeface.DEFAULT,
                    Typeface.BOLD
            );
        }

        return view;
    }

    private int drawable(String name) {
        return getResources().getIdentifier(
                name,
                "drawable",
                getPackageName()
        );
    }

    private int dp(int value) {
        return Math.round(
                value
                        * getResources()
                        .getDisplayMetrics()
                        .density
        );
    }

    private String rupiah(double amount) {
        return NumberFormat
                .getCurrencyInstance(
                        new Locale("id", "ID")
                )
                .format(amount);
    }

    private String first(String... values) {
        if (values == null) {
            return "";
        }

        for (String value : values) {
            if (
                    value != null
                            && !value.trim().isEmpty()
                            && !"null".equalsIgnoreCase(
                                    value.trim()
                            )
                            && !"undefined".equalsIgnoreCase(
                                    value.trim()
                            )
            ) {
                return value.trim();
            }
        }

        return "";
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (
                requestCode == REQ_LOCATION
                        && grantResults.length > 0
                        && grantResults[0]
                        == PackageManager.PERMISSION_GRANTED
        ) {
            loadLocation();

        } else if (requestCode == REQ_LOCATION) {
            locationText.setText("Izin ditolak");
        }
    }
}
