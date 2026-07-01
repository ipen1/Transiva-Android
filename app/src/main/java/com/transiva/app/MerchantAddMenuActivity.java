package com.transiva.app;

import android.os.Bundle;
import android.net.Uri;
import android.content.Intent;
import android.text.InputType;
import android.widget.*;
import org.json.JSONObject;

public class MerchantAddMenuActivity extends MerchantBaseActivity {
    private static final int PICK_IMAGE = 801;
    private LinearLayout root;
    private EditText nameInput, priceInput, categoryInput;
    private TextView originalText, feeText, appText, fileText;
    private Uri imageUri = null;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); build(); }

    private void build(){
        root = new LinearLayout(this); setContentView(page(root));
        root.addView(title("Tambah Menu"));
        root.addView(sub("Harga tampil otomatis ditambah fee gross up seperti web"));

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
        priceInput.addTextChangedListener(new android.text.TextWatcher(){
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ updatePreview(); }
            public void afterTextChanged(android.text.Editable e){}
        });

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
    }

    private int gross(long price){ if(price <= 0) return 0; if(price < 5000) return 200; if(price <= 10000) return 300; return 500; }
    private void updatePreview(){
        long original = 0; try{ original = Long.parseLong(priceInput.getText().toString().trim()); }catch(Exception ignored){}
        int fee = gross(original);
        originalText.setText("Harga Asli: " + rupiah(original) + "\nFee Gross Up: " + rupiah(fee) + "\nHarga Tampil: " + rupiah(original + fee));
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
