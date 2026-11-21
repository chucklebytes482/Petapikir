# Prompt & SOP Generator Kredensial Rilis & Arsip Secret (Umum)

Dokumen ini adalah panduan umum siap pakai untuk memerintahkan AI membuatkan seluruh kredensial rilis permanen Android, dokumentasi kesiapan Google Cloud, berkas keystore `.jks`, serta mengemas semuanya ke dalam **arsip ZIP terenkripsi ber-password** di folder utama `secret/`.

---

## 1. Kalimat Perintah Siap Salin untuk AI

Salin dan kirimkan blok perintah berikut ke AI saat aplikasi Android Anda sudah siap untuk konfigurasi rilis permanen:

```text
Buatkan seluruh kredensial rilis permanen dan dokumentasi konfigurasi untuk aplikasi Android yang sedang kita buat sekarang, lalu kemas seluruh berkas sensitifnya ke dalam 1 file ZIP terenkripsi ber-password di folder utama `secret/`.

Daftar berkas yang wajib dibuat dan dimasukkan ke dalam `secret/<slug_app>_release_secrets.zip`:
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
   - Simpan juga salinan publik `SETUP_GOOGLE_CLOUD.md` (tanpa password/keystore privat) di akar repositori proyek sebagai dokumentasi referensi.
3. `<slug_app>-release.jks`:
   - Generate langsung keystore permanen RSA 2048-bit, masa berlaku 30 tahun (`-validity 10950`), `-alias upload`, dengan `STORE_PASSWORD` dan `KEY_PASSWORD` yang sama persis dengan yang tertulis di `<NamaApp>_Release_Setup_Guide.md`.
4. Berkas secret pendukung lainnya (sesuaikan dengan aplikasi):
   - `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) untuk kebutuhan build lokal bila suatu saat diperlukan.
   - `CERT_FINGERPRINT.txt` berisi hasil verifikasi `keytool` untuk sertifikat Debug dan Release (SHA-1 & SHA-256).
   - `google-services.json` atau `.env` (jika aplikasi memang memilikinya).

Aturan Keamanan & Teknis Wajib:
- JANGAN mengubah `debug.keystore`, `debug.keystore.base64`, atau blok `signingConfigs` / `signingConfig` di `app/build.gradle.kts`.
- Pastikan seluruh berkas mentah sensitif (`.jks`, `keystore.properties`, `google-services.json`, `.env`) diproses di direktori sementara (`/tmp`) lalu dihapus bersih setelah masuk ke ZIP terenkripsi; hanya arsip terenkripsi `secret/<slug_app>_release_secrets.zip` yang disimpan di folder utama `secret/`.
- Pastikan `.gitignore` memblokir berkas mentah sensitif (`*.jks`, `*.keystore`, `*.b64`, `keystore.properties`, `google-services.json`, `.env`, `secret/*.md`, `secret/*.txt`, `secret/*.json` — hindari pola negasi `secret/*` + `!secret/*.zip` agar arsip `.zip` terenkripsi tetap terbaca saat push ke GitHub, atau blokir seluruh `secret/` jika tidak ingin menyimpan arsip di repositori).
- Enkripsi arsip ZIP dengan password acak kuat (minimal 20 karakter alfanumerik), verifikasi bahwa arsip bisa didekripsi dan isi `.jks` di dalamnya valid dengan `keytool`, lalu tampilkan password ZIP tersebut di respons chat saja (JANGAN menulis password ZIP ke dalam berkas repositori).
```

---

## 2. Daftar Isi Standar Arsip `secret/<slug_app>_release_secrets.zip`

| No | Nama Berkas di Dalam ZIP | Fungsi |
|---|---|---|
| 1 | `<NamaApp>_Release_Setup_Guide.md` | Panduan 1-ketuk salin seluruh GitHub Secrets (`RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`), SHA-1/SHA-256, dan langkah rilis |
| 2 | `SETUP_GOOGLE_CLOUD.md` | Dokumentasi kesiapan setup Google Cloud OAuth, Debug/Release fingerprint, deep link ramah HP, dan troubleshooting |
| 3 | `<slug_app>-release.jks` | Berkas keystore penandatanganan rilis permanen (RSA 2048-bit, alias `upload`, berlaku 30 tahun) agar APK rilis baru selalu menimpa APK lama |
| 4 | `keystore.properties` | Referensi konfigurasi lokal (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`) |
| 5 | `CERT_FINGERPRINT.txt` | Catatan ringkas fingerprint SHA-1 & SHA-256 sertifikat Debug dan Release |
| 6 | *(Opsional)* `google-services.json` / `.env` | Berkas konfigurasi tambahan bila aplikasi menggunakan Firebase atau API key pihak ketiga |

---

## 3. Aturan Keamanan Penyimpanan

1. **Simpan Password Secara Terpisah**: Catat password ZIP terenkripsi di pengelola kata sandi (*password manager*) atau catatan terkunci pribadi — **jangan** pernah menuliskan password pembuka ZIP di dalam repositori GitHub.
2. **Backup Ganda**: Selain tersimpan di folder utama `secret/`, salin file `secret/<slug_app>_release_secrets.zip` ke Google Drive pribadi atau penyimpanan offline agar kunci rilis tidak pernah hilang (kehilangan `.jks` berarti APK rilis berikutnya tidak bisa menimpa APK yang sudah terpasang).
3. **Cara Membuka Arsip di HP / PC**:
   - **Android**: Gunakan aplikasi **ZArchiver** atau pengelola berkas yang mendukung ZIP ber-password, lalu masukkan password yang diberikan AI.
   - **Linux / macOS**: Jalankan `unzip secret/<slug_app>_release_secrets.zip` lalu masukkan password.
   - **Windows**: Gunakan **7-Zip** atau **WinRAR** (Extract Here → masukkan password).
