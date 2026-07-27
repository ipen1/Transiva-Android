# Transiva Android WebView

Project Android WebView untuk https://transiva.my.id/?app=1

## Fitur
- WebView modern
- Kamera dan upload file
- GPS/location permission
- Download Manager
- Getar via JavaScript: `Android.vibrate(300)` atau `window.transivaVibrate(300)`
- Notifikasi lokal via JavaScript: `Android.notify('Transiva','Pesan masuk')` atau `window.transivaNotify('Transiva','Pesan masuk')`
- Halaman offline
- Back button dua kali untuk keluar
- GitHub Actions build AAB dan APK

## Cara build di GitHub
1. Upload semua isi folder ini ke repository GitHub.
2. Buka tab Actions.
3. Pilih workflow `Build Transiva AAB`.
4. Klik `Run workflow`.
5. Download artifact `Transiva-release-aab`.

## Catatan Play Store
AAB release dari workflow ini belum memakai keystore upload pribadi. Untuk upload final Play Store sebaiknya tambahkan signing config dengan keystore upload milik Anda.

## Package name
`com.transiva.app`
