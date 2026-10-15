# Cia Launcher — Dokumentasi

> Launcher Android ala Windows 11, package `com.cia.launcher`.
> Dibuat 2026-10-11 atas permintaan Driyan ("sama persis yg ada digambar").

## Gambaran Umum

Cia Launcher adalah aplikasi **home screen pengganti** (launcher) untuk HP Android.
Tampilannya ala Windows 11 versi gelap: jam besar + tanggal di layar utama,
taskbar di bawah, menu Start (bottom sheet), dan app drawer dengan kategori.
Temanya ngikutin sistem HP (crDroid dark): kartu solid abu gelap, corner
rounded, aksen biru soft — bukan glass/blur.

## Fitur

- **Layar utama**: jam besar (format Indonesia), tanggal, wallpaper, taskbar.
- **Wallpaper custom**: tahan lama di area kosong layar utama → "Ganti wallpaper"
  (pilih gambar dari galeri, otomatis di-downsample biar nggak OOM) atau
  "Kembalikan default".
- **Taskbar custom**: 3 slot (Files, Chrome, Kamera) — tahan lama untuk
  "Ganti aplikasi" atau "Kembalikan default". Tombol Start nggak bisa diganti.
- **Menu Start** (bottom sheet): search bar, grid **Pinned** (tahan lama app
  pinned → "Lepas pin"), dan **Disarankan** (maks 6 aplikasi yang beneran
  dibuka lewat launcher, dengan waktu relatif "5 menit lalu"; kosong →
  "Belum ada riwayat").
- **App drawer**: daftar aplikasi asli dari PackageManager, tab kategori
  (Semua/Sosial/Alat/Game), pencarian live, indeks alfabet A–Z di kanan,
  animasi masuk dari bawah.
- **Sembunyikan aplikasi**: tahan lama app di drawer → "Sembunyikan".
  Hilang dari drawer, pencarian, kategori, pinned, disarankan, taskbar.
  Ikon mata muncul kalau ada app tersembunyi. Launcher sendiri nggak bisa
  disembunyikan. (Catatan: ini cuma sembunyiin di UI, bukan enkripsi.)
- **Gesture drawer**: swipe kiri/kanan di seluruh layar ganti kategori;
  swipe turun saat list di paling atas menutup drawer; area indeks A–Z
  dikecualikan dari gesture.
- **Optimasi**: query PackageManager sekali jalan (snapshot 60 detik),
  ikon di-cache (LruCache 32, format RGB_565 hemat RAM), semua kerja berat
  di background thread — menu tampil dulu, konten nyusul.

## Struktur File Penting

| File | Fungsi (1 baris) |
|---|---|
| `MainActivity.kt` | Layar utama: jam/tanggal, wallpaper custom, taskbar + menu ganti app |
| `StartMenuSheet.kt` | Menu Start (bottom sheet): search, pinned grid, disarankan |
| `AppDrawerActivity.kt` | App drawer: tab kategori, pencarian, indeks A–Z, gesture |
| `AppRepository.kt` | Satu-satunya akses PackageManager: snapshot 60 dtk, cache ikon |
| `DrawerRootLayout.kt` | Layout custom penangkap gesture swipe seluruh layar |
| `DrawerAdapter.kt` / `PinnedAdapter.kt` | Adapter list drawer & grid pinned |
| `AppPickerDialog.kt` | Dialog pilih aplikasi (untuk ganti slot taskbar) |
| `HiddenAppsDialog.kt` | Dialog daftar aplikasi tersembunyi |
| `PinnedPrefs.kt` | Simpan urutan aplikasi pinned (SharedPreferences) |
| `RecentAppsPrefs.kt` | Riwayat 20 aplikasi terakhir dibuka (untuk "Disarankan") |
| `HiddenAppsPrefs.kt` | Daftar package aplikasi yang disembunyikan |
| `TaskbarPrefs.kt` | Aplikasi custom per slot taskbar |
| `WallpaperPrefs.kt` | URI wallpaper custom |
| `AppEntry.kt` | Model data satu aplikasi |
| `res/values/colors.xml` | **Semua warna tema** — ubah di sini kalau mau ganti warna |
| `res/layout/sheet_start_menu.xml` | Layout menu Start |
| `res/layout/activity_main.xml` | Layout layar utama |
| `AndroidManifest.xml` | Daftar sebagai HOME launcher; ProfileInstaller **dimatikan** (lihat catatan) |

## Cara Build

Build **offline** (truststore Java rusak untuk HTTPS, tapi cache Gradle lengkap):

```bash
cd ~/workspace/cia-launcher
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export GRADLE_USER_HOME=/home/hatch/.gradle
export JAVA_TOOL_OPTIONS="-Djava.net.preferIPv4Stack=true"
~/gradle-dist/gradle-8.9/bin/gradle :app:assembleDebug --offline
```

Hasil: `app/build/outputs/apk/debug/app-debug.apk` (~6–7 MB).

Catatan build:
- Jangan pakai `./gradlew` (mau download distribusi baru) — pakai binary
  langsung `~/gradle-dist/gradle-8.9/bin/gradle`.
- Gradle 8.9 **tidak jalan di Java 25** — wajib JDK 17.
- Kalau `/usr/lib/jvm/` kosong (habis VM replacement): `apt-get install -y openjdk-17-jdk-headless`
  (tunggu apt lock boot-time lepas dulu, cek via `fuser /var/lib/apt/lists/lock`).
- Dependensi transitive di-pin ke versi yang ada di cache (lihat
  `resolutionStrategy` di `app/build.gradle.kts`); compileSdk 35,
  targetSdk 34.

## Cara Install ke HP

```bash
cat ~/workspace/cia-launcher/app/build/outputs/apk/debug/app-debug.apk \
  | ~/bin/phone-ssh 'cat > ~/cia-launcher.apk && su -c "pm install -r /data/data/com.termux/files/home/cia-launcher.apk"'
```

Tunggu output `Success`. (Driyan sudah minta: update launcher selalu
dipasang langsung via SSH, jangan suruh dia download.)

## Panduan Nambah Fitur

- **Ganti warna tema** → `res/values/colors.xml` (`bg_deep`, `card`,
  `pill_selected`, `accent_blue`, dll). Semua komponen pakai warna ini.
- **Ganti corner radius** → file `res/drawable/bg_*.xml` masing-masing
  (`bg_taskbar`, `bg_search`, `bg_pill`, `bg_tile`).
- **Nambah item di menu Start** → `res/layout/sheet_start_menu.xml` untuk
  layout + `StartMenuSheet.kt` untuk logika.
- **Nambah tab kategori drawer** → `AppRepository.kt` (`TAB_*` +
  daftar keyword `*_KEYS`) + `AppDrawerActivity.kt` (`TAB_COUNT`, tabViews).
- **Nambah slot taskbar** → `MainActivity.kt` (daftar `slots`) +
  `res/layout/activity_main.xml`.
- **Data baru yang perlu disimpan** → bikin file `*Prefs.kt` baru mengikuti
  pola `TaskbarPrefs.kt` (SharedPreferences sederhana).
- **Query aplikasi baru** → selalu lewat `AppRepository` (jangan panggil
  PackageManager langsung dari Activity — berat, harus di background).

## Catatan Penting

- **Signature mismatch**: APK debug di-sign pakai `~/.android/debug.keystore`.
  Kalau VM habis replacement, keystore ke-regenerate → install `-r` gagal
  dengan `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Solusi: uninstall dulu
  (`su -c "pm uninstall com.cia.launcher"`), baru install. **Efek samping:**
  semua settingan user (pinned, hidden, taskbar, wallpaper) ke-reset.
- **ProfileInstaller dimatikan** di manifest (`tools:node="remove"` pada
  `ProfileInstallerInitializer`). Jangan dinyalakan lagi — dulu bikin crash
  `ClassNotFoundException: ListenableFuture` saat startup.
- **Jangan panggil PackageManager di main thread** — query + loadIcon
  lambat (~50–100ms per app); selalu lewat `AppRepository` yang sudah async.
- **Hidden apps bukan keamanan** — cuma disembunyikan dari UI launcher.
- Riwayat "Disarankan" hanya mencatat aplikasi yang dibuka **lewat launcher**,
  bukan pemakaian Android global.

## Update 2026-10-11: Hapus aplikasi
- Long-press aplikasi di app drawer → menu muncul "Hapus aplikasi" (di bawah Sematkan/Sembunyikan).
- Ketuk → dialog konfirmasi → intent ACTION_DELETE bawaan Android (ada konfirmasi sistem lagi).
- Opsi disembunyikan untuk: launcher sendiri & aplikasi sistem (tidak bisa dihapus).
- BroadcastReceiver ACTION_PACKAGE_REMOVED → daftar aplikasi otomatis refresh setelah uninstall.
- Catatan: keystore debug keganti (VM reset 2026-10-11) → install ini butuh uninstall dulu; update berikutnya aman selama keystore tidak keganti lagi.

## Update 2026-10-11 (2): uninstaller bawaan launcher
- Ganti ACTION_DELETE intent -> PackageInstaller API langsung
  (`packageManager.packageInstaller.uninstall()` + `REQUEST_DELETE_PACKAGES`).
  Alasan: intent bisa nyasar/dibajak aplikasi lain; API milik launcher sendiri.
- Hasil uninstall ditangani BroadcastReceiver: sukses -> toast + refresh daftar;
  gagal -> toast berisi pesan error; butuh konfirmasi user -> dibuka dialog sistem.
- onResume drawer sekarang reloadApps() penuh (bukan applyFilter saja) agar
  daftar tidak basi setelah uninstall.
- Keystore permanen: debug.keystore di project + signingConfigs "ciaDebug" di
  build.gradle.kts — update in-place tidak lagi INSTALL_FAILED_UPDATE_INCOMPATIBLE
  walau VM ke-reset.
