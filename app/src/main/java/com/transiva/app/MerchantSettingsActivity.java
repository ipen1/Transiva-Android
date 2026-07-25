package com.transiva.app;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Pengaturan lokal khusus sisi merchant. */
public class MerchantSettingsActivity extends MerchantBaseActivity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        setContentView(page(root));
        build(root);
        MerchantAppSettings.apply(this);
    }

    private void build(LinearLayout root) {
        root.addView(title("Pengaturan Merchant"));
        root.addView(sub("Atur tampilan dan pembaruan aplikasi Transiva Merchant"));

        TextView section = tv("Tampilan", 13, NAVY, true);
        LinearLayout.LayoutParams sectionLp = new LinearLayout.LayoutParams(-1, -2);
        sectionLp.setMargins(0, dp(8), 0, dp(8));
        root.addView(section, sectionLp);

        LinearLayout card = settingCard();
        card.addView(toggleRow("Mode Malam",
                "Aktifkan tema gelap pada seluruh halaman merchant",
                MerchantAppSettings.isDarkMode(this),
                (button, checked) -> {
                    MerchantAppSettings.setDarkMode(this, checked);
                    recreate();
                }));
        root.addView(card);

        TextView updateSection = tv("Pembaruan", 13, NAVY, true);
        LinearLayout.LayoutParams updateSectionLp = new LinearLayout.LayoutParams(-1, -2);
        updateSectionLp.setMargins(0, dp(18), 0, dp(8));
        root.addView(updateSection, updateSectionLp);

        LinearLayout updateCard = settingCard();
        LinearLayout updateRow = new LinearLayout(this);
        updateRow.setGravity(Gravity.CENTER_VERTICAL);
        updateRow.setPadding(0, dp(4), 0, dp(4));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(tv("Cek Pembaruan Aplikasi", 15, NAVY, true));
        labels.addView(tv("Versi terpasang " + AppUpdateClient.installedVersionName(this), 11, MUTED, false));
        updateRow.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView arrow = tv("›", 30, BLUE, true);
        updateRow.addView(arrow);
        updateRow.setOnClickListener(v -> {
            Intent i = new Intent(this, UpdateDownloadActivity.class);
            i.putExtra(UpdateDownloadActivity.EXTRA_ROLE, "merchant");
            startActivity(i);
        });
        updateCard.addView(updateRow);
        root.addView(updateCard);

        TextView accountSection = tv("Akun Merchant", 13, NAVY, true);
        LinearLayout.LayoutParams accLp = new LinearLayout.LayoutParams(-1, -2);
        accLp.setMargins(0, dp(18), 0, dp(8));
        root.addView(accountSection, accLp);

        LinearLayout accountCard = settingCard();
        accountCard.setOnClickListener(v -> open(MerchantRestaurantProfileActivity.class));
        LinearLayout accountRow = new LinearLayout(this);
        accountRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout accountLabels = new LinearLayout(this);
        accountLabels.setOrientation(LinearLayout.VERTICAL);
        accountLabels.addView(tv("Profil Merchant", 15, NAVY, true));
        accountLabels.addView(tv("Kelola nama restoran dan banner merchant", 11, MUTED, false));
        accountRow.addView(accountLabels, new LinearLayout.LayoutParams(0, -2, 1f));
        accountRow.addView(tv("›", 30, BLUE, true));
        accountCard.addView(accountRow);
        root.addView(accountCard);

        TextView note = tv("Mode malam tersimpan otomatis dan diterapkan kembali saat aplikasi dibuka.", 11, MUTED, false);
        LinearLayout.LayoutParams noteLp = new LinearLayout.LayoutParams(-1, -2);
        noteLp.setMargins(dp(4), dp(12), dp(4), dp(8));
        root.addView(note, noteLp);
    }

    private LinearLayout toggleRow(String title, String subtitle, boolean checked,
                                   CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        labels.addView(tv(title, 15, NAVY, true));
        labels.addView(tv(subtitle, 11, MUTED, false));
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(listener);
        row.addView(toggle);
        return row;
    }

    private LinearLayout settingCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(round(Color.WHITE, dp(20)));
        card.setElevation(dp(2));
        return card;
    }
}
