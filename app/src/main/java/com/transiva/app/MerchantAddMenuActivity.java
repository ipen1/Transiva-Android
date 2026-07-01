package com.transiva.app;

import android.os.Bundle;
import android.net.Uri;
import android.content.Intent;
import android.text.InputType;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.provider.MediaStore;
import android.view.Gravity;
import android.widget.*;
import org.json.JSONObject;

public class MerchantAddMenuActivity extends MerchantBaseActivity {
    private static final int PICK_IMAGE = 801;
    private LinearLayout root;
    private EditText nameInput, priceInput, categoryInput;
    private TextView originalText, fileText, previewName, previewPrice, previewCategory, previewIcon;
    private ImageView previewImage;
    private Uri imageUri = null;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); build(); }

    private void build(){
        root = new LinearLayout(this); setContentView(page(root));
        root.addView(title("Tambah Menu"));
        root.addView(sub("Preview di bawah mengikuti tampilan yang akan muncul di aplikasi customer"));

        root.addView(label("Nama Menu"));
        nameInput = input("Contoh: Nasi Goreng", InputType.TYPE_CLASS_TEXT);
        root.addView(nameInput);

        root.addView(label("Harga Asli Merchant"));
        priceInput = input("Contoh: 20000", InputType.TYPE_CLASS_NUMBER);
        root.addView(priceInput);

        root.addView(label("Kategori"));
        categoryInput = input("Makanan / Minuman", InputType.TYPE_CLASS_TEXT);
        root.addView(categoryInput);

        originalText = card("Harga Asli: Rp 0\nFee Gross Up: Rp 0\nHarga Tampil: Rp 0");
        root.addView(originalText);

        root.addView(label("Preview Tampilan di Aplikasi"));
        root.addView(previewCard());

        addWatchers();

        Button pick = outlineBtn("📷 Pilih Gambar Menu");
        pick.setOnClickListener(v -> chooseImage());
        root.addView(pick);
        fileText = sub("Gambar belum dipilih. Jika kosong, server memakai default.");
        root.addView(fileText);

        Button save = btn("Simpan Menu");
        save.setOnClickListener(v -> save(save));
        root.addView(save);

        Button back = outlineBtn("← Kembali");
        back.setOnClickListener(v -> finish());
        root.addView(back);

        updatePreview();
    }

    private LinearLayout previewCard(){
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));
        box.setBackground(round(Color.WHITE, dp(20)));
        box.setElevation(dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(4), 0, dp(14));
        box.setLayoutParams(lp);

        FrameLayout imgWrap = new FrameLayout(this);
        imgWrap.setBackground(round(Color.parseColor("#EEF6FF"), dp(18)));
        previewImage = new ImageView(this);
        previewImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        imgWrap.addView(previewImage, new FrameLayout.LayoutParams(-1, -1));
        previewIcon = tv("🍽️", 34, BLUE, true);
        previewIcon.setGravity(Gravity.CENTER);
        imgWrap.addView(previewIcon, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams imgLp = new LinearLayout.LayoutParams(dp(96), dp(96));
        imgLp.setMargins(0, 0, dp(12), 0);
        box.addView(imgWrap, imgLp);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_VERTICAL);
        previewName = tv("Nama menu", 17, NAVY, true);
        previewPrice = tv("Rp 0", 16, BLUE, true);
        previewCategory = tv("Kategori", 13, MUTED, false);
        TextView hint = tv("Harga ini yang tampil di aplikasi", 11, Color.parseColor("#98A2B3"), false);
        info.addView(previewName);
        info.addView(previewPrice);
        info.addView(previewCategory);
        info.addView(hint);
        box.addView(info, new LinearLayout.LayoutParams(0, -2, 1f));
        return box;
    }

    private void addWatchers(){
        android.text.TextWatcher watcher = new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ updatePreview(); }
            public void afterTextChanged(android.text.Editable e){}
        };
        nameInput.addTextChangedListener(watcher);
        priceInput.addTextChangedListener(watcher);
        categoryInput.addTextChangedListener(watcher);
    }

    private int gross(long price){ if(price <= 0) return 0; if(price < 5000) return 200; if(price <= 10000) return 300; return 500; }
    private void updatePreview(){
        long original = 0; try{ original = Long.parseLong(priceInput.getText().toString().trim()); }catch(Exception ignored){}
        int fee = gross(original);
        long appPrice = original + fee;
        originalText.setText("Harga Asli: " + rupiah(original) + "\nFee Gross Up: " + rupiah(fee) + "\nHarga Tampil: " + rupiah(appPrice));
        String name = nameInput.getText().toString().trim();
        String cat = categoryInput.getText().toString().trim();
        previewName.setText(name.isEmpty() ? "Nama menu" : name);
        previewCategory.setText(cat.isEmpty() ? "Kategori" : cat);
        previewPrice.setText(rupiah(appPrice));
    }

    private void chooseImage(){
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        startActivityForResult(Intent.createChooser(i, "Pilih gambar menu"), PICK_IMAGE);
    }

    @Override protected void onActivityResult(int r, int c, Intent data){
        super.onActivityResult(r,c,data);
        if(r == PICK_IMAGE && c == RESULT_OK && data != null){
            imageUri = data.getData();
            fileText.setText("Gambar dipilih dan siap diupload.");
            showPickedImage(imageUri);
        }
    }

    private void showPickedImage(Uri uri){
        if(uri == null) return;
        try{
            Bitmap bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            if(bmp != null){
                previewIcon.setVisibility(android.view.View.GONE);
                previewImage.setImageBitmap(bmp);
            }
        }catch(Exception e){
            toast("Preview gambar gagal, tapi file masih bisa dicoba upload.");
        }
    }

    private void save(Button save){
        String name = nameInput.getText().toString().trim();
        String cat = categoryInput.getText().toString().trim();
        long original = 0; try{ original = Long.parseLong(priceInput.getText().toString().trim()); }catch(Exception ignored){}
        if(name.isEmpty() || cat.isEmpty() || original <= 0){ alert("Lengkapi Data", "Nama, harga, dan kategori wajib diisi."); return; }
        int fee = gross(original); long appPrice = original + fee;

        final String finalName = name;
        final String finalCat = cat;
        final long finalOriginal = original;
        final int finalFee = fee;
        final long finalAppPrice = appPrice;
        final Uri finalImageUri = imageUri;

        save.setEnabled(false); save.setText("Mengupload...");
        new Thread(() -> {
            try{
                JSONObject f = new JSONObject();
                f.put("name", finalName);
                f.put("price", finalAppPrice);
                f.put("original_price", finalOriginal);
                f.put("grossup_fee", finalFee);
                f.put("category", finalCat);
                f.put("username", username()); f.put("user_id", userId());
                JSONObject res = new JSONObject(postForm(BASE + "add_food_menu.php", f, finalImageUri, "image", "menu.jpg"));
                runOnUiThread(() -> {
                    save.setEnabled(true); save.setText("Simpan Menu");
                    if(res.optBoolean("success", false)){ toast(res.optString("message","Menu berhasil disimpan")); finish(); }
                    else alert("Gagal", res.optString("message","Gagal menyimpan menu"));
                });
            }catch(Exception e){ runOnUiThread(() -> { save.setEnabled(true); save.setText("Simpan Menu"); alert("Error","Server error / koneksi gagal."); }); }
        }).start();
    }
}
