# Video Popup Guard — Android

Proyek aplikasi Android 8+ (API 26). Ini bukan ekstensi Chrome: situs dibuka
di WebView dalam aplikasi. JavaScript pemblokir ada di
app/src/main/assets/guard.js; Java hanya menyediakan UI dan API WebView native.

## Status paket

Ini kode sumber, BELUM APK siap pasang. Lingkungan pembuat paket tidak memiliki
Android SDK/Gradle, jadi build APK dan uji perangkat belum dilakukan.
Pengujian mock JavaScript lulus. Unit kebijakan URL disertakan tetapi belum
dijalankan karena compiler Java tidak tersedia. Integrasi Android belum diuji.

## Buat APK di Mac

1. Pasang Android Studio di Mac, lalu lewat SDK Manager pasang Android SDK
   Platform 35 dan Build Tools 34.0.0. Gunakan JDK 17.
2. Ekstrak ZIP. Buka Terminal dalam folder android-popup-guard.
3. Jika Java belum tersedia di Terminal, gunakan Java bawaan Android Studio:

       export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"

   Jika versi bundled JDK tidak cocok, pilih/install JDK 17.
4. Jalankan:

       bash build-apk.sh

   Launcher ini mengunduh Gradle 8.9 dari services.gradle.org, memeriksa SHA-256,
   lalu menjalankan assembleDebug. Internet diperlukan untuk Gradle/dependensi.
   Android SDK di Mac dicari di ~/Library/Android/sdk; untuk lokasi lain gunakan:

       ANDROID_HOME="/lokasi/android/sdk" bash build-apk.sh

5. APK hasil build ada di app/build/outputs/apk/debug/app-debug.apk.
6. Salin APK ke Android, buka, dan izinkan instalasi dari sumber tersebut bila diminta.

Bisa juga membuka proyek di Android Studio. Setelah launcher mengunduh Gradle,
atur distribusi Gradle lokal ke .build-tools/gradle-8.9, pilih JDK 17, lalu Sync.
Proyek ini memakai launcher shell sendiri, bukan Gradle wrapper standar.
Debug APK ditandatangani dengan debug key untuk pemakaian pribadi.

## Pakai

Masukkan URL http:// atau https://, tekan Buka URL, lalu tekan play.
Semua jendela/tab tambahan ditolak, termasuk popup login yang sah.
Tombol Kembali dan Muat ulang tersedia. Fullscreen video ditangani oleh WebView.

Opsi "Blokir pindah domain di halaman utama" menolak navigasi lintas hostname
yang diterima oleh callback WebView. Ini opsional karena banyak situs memakai
redirect dan login lintas domain yang sah. Opsi ini bukan firewall dan tidak
memblokir request video/iframe dari domain lain. Setiap Buka URL menentukan
hostname yang diizinkan ulang. Tidak semua navigasi melewati callback tersebut.

## Cara pemblokiran

- Native: setSupportMultipleWindows(true) + onCreateWindow mengembalikan false.
  Tidak membuat WebView anak dan tidak menjalankan intent/browser eksternal.
  Jangan mengganti setSupportMultipleWindows menjadi false: target blank dapat
  berubah menjadi navigasi di halaman yang sama.
- JS: menolak window.open, link target blank/bernama, dan form tab baru.
- AndroidX memasang JS saat awal dokumen di semua origin/iframe dalam WebView,
  bila DOCUMENT_START_SCRIPT didukung versi WebView perangkat.
- Jika fitur awal tidak tersedia, native tetap aktif; JS fallback disisipkan
  setelah page mulai/selesai, sehingga lebih mudah terlambat.

Counter hanya mencatat callback native, bukan semua popup yang dicegah oleh JS.
Tidak merekam URL popup karena onCreateWindow tidak menyediakan URL tujuannya.

## Batas

Tidak menjamin semua trik iklan terblokir. Overlay HTML, dialog JS, download,
dan redirect di halaman yang sama bukan popup jendela baru. Pemutar dengan DRM,
cookie pihak ketiga, mixed content atau login eksternal bisa tidak berfungsi.
Update Android System WebView sebelum uji. Cookies pihak ketiga ditolak.
JavaScript web tidak mendapat bridge Java atau akses file/konten lokal.
HTTP diizinkan agar URL LAN dapat dibuka; HTTPS digunakan bila tersedia.
URL terakhir disimpan lokal. Tidak ada server/analytics.

## Uji

1. Jalankan halaman tests/popup-test.html dari server HTTP di komputer.
2. Buka URL IP LAN komputer di aplikasi, misalnya:
   http://192.168.1.88:8765/tests/popup-test.html
3. Uji window.open, tautan blank/bernama, form dan iframe: tidak boleh membuka
   jendela lain. Tautan biasa dan tombol video harus tetap bisa dipakai.
4. Masukkan URL MP4 untuk menguji playback/fullscreen.
5. Aktifkan kunci domain dan uji tautan ke domain lain.

Unit test JS: node tests/guard.test.cjs
Unit test URL (JDK):

    mkdir -p .test-classes
    javac -d .test-classes app/src/main/java/id/local/videopopupguard/UrlPolicy.java tests/UrlPolicyTest.java
    java -cp .test-classes UrlPolicyTest

Referensi:
https://developer.android.com/develop/ui/views/layout/webapps/webview
https://developer.android.com/reference/android/webkit/WebChromeClient
https://developer.android.com/reference/androidx/webkit/WebViewCompat
https://developer.android.com/build/releases/agp-8-7-0-release-notes
# android-popup-guard
