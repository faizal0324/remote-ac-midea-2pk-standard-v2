# Remote Midea (MSAF-18CRN2X)

Aplikasi remote AC Midea memakai pemancar IR di HP (itel City 200).

## Cara mendapatkan APK (tanpa Android Studio)
1. Buat akun gratis di github.com, lalu buat repository baru (boleh private).
2. Upload seluruh isi folder ini (termasuk folder `.github`) ke repository itu.
3. Buka tab **Actions**, pilih **Build APK**, klik **Run workflow**. Tunggu sekitar 5 menit.
4. Buka hasil build yang sudah hijau, unduh artifact **remote-midea-apk**, ekstrak zip-nya.
5. Kirim `app-debug.apk` ke HP, buka, izinkan "install dari sumber tidak dikenal", lalu install.

## Cara tes
- Arahkan sisi atas HP (tempat pemancar IR) ke unit indoor AC, jarak 1 sampai 3 meter.
- Ketuk tombol power besar. Lihat apakah AC berbunyi "bip".
- Cek pemancar: lihat bagian atas HP lewat kamera depan HP lain, saat tombol ditekan akan tampak titik ungu berkedip.

## Catatan
- Kode IR dari library IRremoteESP8266 (protokol Midea). Belum dites ke AC Anda.
- Tampilan: ilustrasi AC dengan angin beranimasi, warna berubah sesuai mode.
- Mode Panas tidak ditampilkan karena seri CRN2 umumnya hanya pendingin.
- Fitur Ayun, Turbo, Eco, Lampu, Senyap, dan Bersih berupa perintah toggle, jadi status di layar adalah perkiraan.
