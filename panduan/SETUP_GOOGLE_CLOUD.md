# Status Kesiapan Google Cloud & Rilis — PetaPikir (Versi Publik)

Dokumen publik ini tersimpan di folder `panduan/` sebagai referensi kesiapan konfigurasi Google Cloud OAuth, sidik jari sertifikat publik (Debug & Release), GitHub Actions, dan GitHub Pages untuk aplikasi Android **PetaPikir**.

> **Catatan Keamanan:** Dokumen ini **tidak** memuat kata sandi, keystore privat, token, maupun nilai Base64 rahasia. Seluruh berkas sensitif dikelola secara terpisah di dalam kontainer **VeraCrypt (`.hc`)** di folder `secret/` yang diblokir penuh oleh `.gitignore` (hanya lokal di AI Studio, tidak pernah masuk GitHub) dan dibuka di HP menggunakan **Vault Explorer (F-Droid)**.

---

## 1. Ringkasan Identitas & Status Kesiapan Proyek

| Komponen | Nilai / Konfigurasi | Status |
|---|---|---|
| **Nama Aplikasi** | `PetaPikir` | Siap |
| **Package Name (`applicationId`)** | `com.aistudio.petapikir.krmxvp` | Siap |
| **Repositori GitHub** | `https://github.com/chucklebytes482/petapikir` | Siap |
| **GitHub Pages (Folder `docs/`)** | `https://chucklebytes482.github.io/petapikir/` | Siap |
| **Kebijakan Privasi (App Domain)** | `https://chucklebytes482.github.io/petapikir/privacy.html` | Siap |
| **Ketentuan Layanan (App Domain)** | `https://chucklebytes482.github.io/petapikir/terms.html` | Siap |
| **Akun Penguji (Test Users) & Kontak** | `chucklebytes482@gmail.com` | Siap |
| **API Google Aktif** | Google Drive API | Siap |
| **OAuth Scope** | `https://www.googleapis.com/auth/drive.file` *(atau `drive` untuk mode Testing pribadi)* | Siap |
| **Workflow Rilis Otomatis** | `.github/workflows/release.yml` (R8 + shrink + `arm64-v8a`, < 10 MB) | Siap |
| **Penataan Folder Repo** | `panduan/` (panduan & template), `docs/` (khusus GitHub Pages), `secret/` (`.gitignore` penuh) | Siap |

---

## 2. Sidik Jari Sertifikat Publik (Debug & Release)

Nilai SHA-1 dan SHA-256 berikut bersifat **publik** (aman didokumentasikan untuk pendaftaran OAuth Android Client ID di Google Cloud Console dan Firebase Console):

### A. Sertifikat Debug (Emulator / AI Studio)
```text
SHA-1   : 4F:44:55:9B:D9:76:AB:99:99:69:C1:F4:5A:EA:D7:F4:AF:7C:A0:FC
SHA-256 : 44:30:EA:45:85:8A:9E:E1:53:EB:DC:C7:4D:88:62:B5:42:58:66:65:BF:9D:33:D9:DA:41:D2:48:8C:4A:1F:30
```

### B. Sertifikat Release Permanen (`alias = upload`, RSA 2048-bit)
```text
SHA-1   : 58:77:B4:2E:32:3F:DF:EB:E9:72:77:B0:15:04:F0:5E:2A:CC:BE:EA
SHA-256 : 6E:31:B8:29:85:1B:F5:DC:A7:8E:FD:96:85:85:A6:B4:B1:07:B0:3E:4D:77:B0:CB:0F:9E:AF:36:F2:B2:DA:B3
```

---

## 3. Tautan Langsung Google Cloud & GitHub (Ramah HP)

> **Tips HP:** Jika menu Google Cloud Console terpotong di layar ponsel, aktifkan opsi **Situs Desktop / Mode Desktop** di menu browser HP.

- **Google Auth Platform — Branding (App Domain):** `https://console.cloud.google.com/auth/branding`
- **Google Auth Platform — Clients (OAuth Android & Web):** `https://console.cloud.google.com/auth/clients`
- **Google Auth Platform — Audience (Test Users):** `https://console.cloud.google.com/auth/audience`
- **Google Auth Platform — Data Access (Scopes):** `https://console.cloud.google.com/auth/scopes`
- **Google Drive API Library:** `https://console.cloud.google.com/apis/library/drive.googleapis.com`
- **GitHub Actions Secrets (PetaPikir):** `https://github.com/chucklebytes482/petapikir/settings/secrets/actions`
- **GitHub Actions Workflow Release:** `https://github.com/chucklebytes482/petapikir/actions/workflows/release.yml`
- **GitHub Releases Terbaru:** `https://github.com/chucklebytes482/petapikir/releases/latest`

---

## 4. Checklist GitHub Secrets yang Dibutuhkan Workflow Rilis

Isi seluruh nilai rahasia dari kontainer VeraCrypt pribadi Anda (dibuka lewat **Vault Explorer** di HP) pada **Settings → Secrets and variables → Actions**:

| Nama Secret | Keterangan |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | Teks Base64 satu baris dari keystore rilis permanen (`petapikir-release.jks`) |
| `STORE_PASSWORD` | Kata sandi keystore rilis |
| `KEY_PASSWORD` | Kata sandi alias `upload` |
| `KEY_ALIAS` | `upload` |
| `GOOGLE_SERVICES_JSON_BASE64` | *(Opsional / bila memakai Firebase)* Teks Base64 satu baris dari `google-services.json` |

---

## 5. Troubleshooting OAuth Android & Sinkronisasi Drive

| Gejala Error | Penyebab Utama | Solusi Cepat |
|---|---|---|
| `ApiException: 10` (DEVELOPER_ERROR) saat login di APK rilis | SHA-1 / SHA-256 sertifikat rilis belum terdaftar di OAuth Android Client atau `applicationId` tidak cocok | Pastikan OAuth Client ID Android memakai package `com.aistudio.petapikir.krmxvp` dan SHA-1 rilis `58:77:B4:2E:...` di atas. |
| `Error 403: access_denied` saat memilih akun Google | Status aplikasi masih *Testing* dan email belum masuk daftar *Test users* | Tambahkan email pengguna di halaman **Audience → Test users** Google Auth Platform. |
| Token Drive ditolak / error `403 insufficientPermissions` | Google Drive API belum aktif atau scope Drive belum didaftarkan | Aktifkan **Google Drive API** di APIs & Services dan pastikan scope `drive.file` terdaftar di **Data Access**. |
| Pembaruan APK rilis gagal menimpa aplikasi lama (`App not installed`) | APK ditandatangani dengan keystore sementara berbeda | Pastikan seluruh 4 GitHub Secrets (`RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`) terisi benar sebelum menjalankan workflow rilis. |
