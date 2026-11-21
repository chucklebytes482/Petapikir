# Prompt & SOP Generator Kredensial Rilis & Kontainer VeraCrypt (Umum)

Dokumen ini adalah panduan umum siap pakai di folder `panduan/` untuk memerintahkan AI membuatkan seluruh kredensial rilis permanen Android, dokumentasi kesiapan Google Cloud, berkas keystore `.jks`, serta mengemas seluruh berkas sensitif ke dalam **kontainer standar VeraCrypt (`.hc`)** di folder `secret/` yang **dimasukkan ke `.gitignore`** agar tidak pernah ter-push ke GitHub dan hanya tersimpan sementara di AI Studio.

---

## 1. Pembagian Zona Folder Repositori (Wajib Konsisten)

| Folder | Status Git | Fungsi Eksklusif |
|---|---|---|
| `panduan/` | Masuk Git | Seluruh dokumen panduan, template kode, mindmap alur build, prompt SOP, dan salinan publik `SETUP_GOOGLE_CLOUD.md` *(tanpa rahasia)*. |
| `docs/` | Masuk Git | **Hanya** berkas web publik **GitHub Pages** (`index.html`, `privacy.html`, `terms.html`, serta aset web publiknya). |
| `secret/` | **Diabaikan `.gitignore`** | **Hanya** kontainer **VeraCrypt (`.hc`)** terenkripsi sementara di workspace AI Studio (tidak pernah masuk GitHub; hapus setelah tersimpan aman di HP). |

---

## 2. Kalimat Perintah Siap Salin untuk AI

Salin dan kirimkan blok perintah berikut ke AI saat aplikasi Android Anda sudah siap untuk konfigurasi rilis permanen:

```text
Buatkan seluruh kredensial rilis permanen dan dokumentasi konfigurasi untuk aplikasi Android yang sedang kita buat sekarang, lalu kemas seluruh berkas sensitifnya ke dalam 1 kontainer standar VeraCrypt (`.hc`) terenkripsi di folder `secret/` yang diabaikan penuh di `.gitignore` (hanya lokal di AI Studio, tidak pernah masuk GitHub).

Daftar berkas yang wajib dibuat dan dimasukkan ke dalam kontainer `secret/<slug_app>_secrets.hc` (di dalam subfolder `release/`):
1. `<NamaApp>_Release_Setup_Guide.md`:
   - Berisi seluruh GitHub Secrets dalam kotak kode terpisah agar mudah disalin 1 ketukan di HP:
     - `RELEASE_KEYSTORE_BASE64` (1 baris tanpa spasi/baris baru)
     - `STORE_PASSWORD` (acak kuat 24 karakter alfanumerik)
     - `KEY_PASSWORD` (acak kuat 24 karakter alfanumerik)
     - `KEY_ALIAS` (`upload`, cocok dengan `signingConfigs.release` di `app/build.gradle.kts` & `.github/workflows/release.yml`)
     - `GOOGLE_SERVICES_JSON_BASE64` (jika aplikasi memakai Firebase / Google Services)
   - Berisi `applicationId` (Package Name), fingerprint `SHA-1` & `SHA-256` permanen, checklist Google Cloud Console, dan langkah menjalankan workflow GitHub Actions Release.
2. `SETUP_GOOGLE_CLOUD.md`:
   - Berisi tabel ringkasan status kesiapan proyek (Google Cloud Project, Package Name, API aktif, OAuth Brand & Scope, App Domain, Test Users, Debug & Release SHA-1/SHA-256, GitHub Secrets, GitHub Actions, GitHub Pages).
   - Berisi tautan langsung Google Cloud Console ramah HP dan tabel troubleshooting OAuth Android.
   - Simpan juga salinan publik `SETUP_GOOGLE_CLOUD.md` (bersih tanpa password, Base64 keystore, atau kunci privat) di dalam folder `panduan/` — JANGAN menyimpannya di folder `docs/` karena `docs/` khusus untuk GitHub Pages.
3. `<slug_app>-release.jks`:
   - Generate langsung keystore permanen RSA 2048-bit, masa berlaku 30 tahun (`-validity 10950`), `-alias upload`, dengan `STORE_PASSWORD` dan `KEY_PASSWORD` yang sama persis dengan yang tertulis di `<NamaApp>_Release_Setup_Guide.md`.
4. Berkas secret pendukung lainnya (sesuaikan dengan aplikasi):
   - `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) untuk kebutuhan build lokal bila suatu saat diperlukan.
   - `CERT_FINGERPRINT.txt` berisi hasil verifikasi `keytool` untuk sertifikat Debug dan Release (SHA-1 & SHA-256).
   - `google-services.json` atau `.env` (jika aplikasi memang memilikinya).
   - *(Opsional)* folder `katasandi/` bila juga membuat daftar kata sandi tambahan.

Standar Enkripsi VeraCrypt & Keamanan Wajib:
- Kemas seluruh berkas sensitif ke kontainer standar **VeraCrypt v5** (`secret/<slug_app>_secrets.hc`, ukuran ringkas mis. 1,00 MiB) dengan parameter:
  - Cipher: **AES-256-XTS**
  - Hash / KDF: **PBKDF2-HMAC-SHA512** (500.000 iterasi / PIM default)
  - Header utama & header cadangan valid, serta filesystem internal **ext2** (kompatibel penuh dengan **Vault Explorer** dari F-Droid).
- JANGAN mengubah `debug.keystore`, `debug.keystore.base64`, atau blok `signingConfigs` / `signingConfig` di `app/build.gradle.kts`.
- Proses seluruh berkas mentah sensitif (`.jks`, `keystore.properties`, `google-services.json`, `.env`) di direktori sementara (`/tmp`) lalu hapus bersih setelah masuk ke kontainer `.hc`.
- Pastikan `.gitignore` memblokir seluruh folder `secret/` dan berkas `*.hc` (serta `*.jks`, `*.keystore`, `*.b64`, `keystore.properties`, `google-services.json`, `.env`) sehingga kontainer rahasia hanya ada secara lokal di workspace AI Studio dan **tidak pernah** ikut ter-push ke GitHub.
- Pastikan semua dokumen panduan & template tersimpan di folder `panduan/`, dan folder `docs/` hanya berisi berkas halaman web GitHub Pages.
- Verifikasi secara independen bahwa kontainer `.hc` dapat didekripsi (header utama, header cadangan, CRC-32, filesystem `ext2` dengan `e2fsck`, dan SHA-1 `.jks` dengan `keytool`), lalu tampilkan password kontainer VeraCrypt di respons chat saja (JANGAN pernah menuliskan password kontainer ke dalam berkas repositori).
```

---

## 3. Daftar Isi Standar Kontainer `secret/<slug_app>_secrets.hc`

| No | Lokasi di Dalam Kontainer `.hc` | Fungsi |
|---|---|---|
| 1 | `release/<NamaApp>_Release_Setup_Guide.md` | Panduan 1-ketuk salin seluruh GitHub Secrets (`RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`), SHA-1/SHA-256, dan langkah rilis |
| 2 | `release/SETUP_GOOGLE_CLOUD.md` | Arsip lengkap dokumentasi kesiapan setup Google Cloud OAuth, Debug/Release fingerprint, deep link ramah HP, dan troubleshooting |
| 3 | `release/<slug_app>-release.jks` | Berkas keystore penandatanganan rilis permanen (RSA 2048-bit, alias `upload`, berlaku 30 tahun) agar APK rilis baru selalu menimpa APK lama |
| 4 | `release/keystore.properties` | Referensi konfigurasi lokal (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) |
| 5 | `release/CERT_FINGERPRINT.txt` | Catatan ringkas fingerprint SHA-1 & SHA-256 sertifikat Debug dan Release |
| 6 | *(Opsional)* `release/google-services.json` / `.env` | Berkas konfigurasi tambahan bila aplikasi menggunakan Firebase atau API key pihak ketiga |
| 7 | *(Opsional)* `katasandi/` | Daftar kata sandi tambahan terenkripsi bila diperlukan |

---

## 4. Pengaturan Membuka di HP (**Vault Explorer — F-Droid**) & Keamanan

1. **Isolasi GitHub & Pengambilan dari AI Studio**:
   - Karena `secret/` dan `*.hc` tercantum di `.gitignore`, kontainer tidak akan pernah ikut saat *push* ke GitHub.
   - Ambil kontainer `.hc` dari workspace AI Studio melalui menu **Export ZIP** di AI Studio (atau dari tampilan workspace lokal sebelum dibersihkan), lalu **hapus kontainer dari `secret/`** setelah tersimpan aman di HP dan backup pribadi Anda.
   - *Catatan:* Pada sebagian tampilan AI Studio, jalur yang tercantum di `.gitignore` dapat disembunyikan di *File Explorer* namun tetap tersedia saat ekspor proyek atau pengelolaan lokal; pastikan selalu memeriksa hasil unduhan sebelum menghapus berkas di workspace.
2. **Cara Membuka Kontainer `.hc` di HP (Vault Explorer dari F-Droid)**:
   - Buka **Vault Explorer** → ketuk **+** / **Open Container** → pilih berkas `<slug_app>_secrets.hc`.
   - Pilih format **VeraCrypt** (Standard Volume).
   - Pengaturan kriptografi: Cipher **AES**, Hash **SHA-512** *(atau Auto)*, **PIM kosong (default)**, Filesystem **ext2** *(terdeteksi otomatis)*.
   - Masukkan kata sandi pembuka yang hanya diberikan di chat AI.
3. **Membuka di PC (Linux / macOS / Windows)**:
   - Gunakan aplikasi resmi **VeraCrypt** (pilih berkas `.hc` → Mount → masukkan kata sandi, PIM kosong, Hash SHA-512 / Auto).
4. **Simpan Password Secara Terpisah & Backup Ganda**:
   - Catat kata sandi pembuka kontainer di pengelola kata sandi (*password manager*) terpisah — **jangan** pernah menyimpan kata sandi di dalam repositori.
   - Simpan salinan cadangan berkas `.hc` di penyimpanan pribadi luring atau cloud pribadi agar keystore rilis permanen tidak pernah hilang.
