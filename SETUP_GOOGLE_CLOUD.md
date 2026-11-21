# Setup Google Cloud Console & Rilis — PetaPikir

Seluruh konfigurasi Google Cloud OAuth, kunci penandatanganan rilis permanen, dan GitHub Pages untuk **PetaPikir** (`com.aistudio.petapikir.krmxvp`) telah selesai disiapkan. Dokumen ini berfungsi sebagai catatan referensi dan troubleshooting.

---

## Ringkasan Status & Identitas Proyek

| Komponen | Status | Nilai |
|---|---|---|
| **Google Cloud Project** | ✅ Selesai | `gen-lang-client-0224561583` (`1000578483438`) |
| **Package Name (`applicationId`)** | ✅ Selesai | `com.aistudio.petapikir.krmxvp` *(namespace kode: `com.example`)* |
| **Google Drive API** | ✅ Aktif | `drive.googleapis.com` |
| **OAuth Brand & Scope Drive** | ✅ Selesai | Brand `PetaPikir` • Scope `https://www.googleapis.com/auth/drive` |
| **App Domain (OAuth Brand)** | ✅ Selesai | Home: `https://chucklebytes482.github.io/petapikir/` • Privacy: `.../privacy.html` • Terms: `.../terms.html` |
| **Audience / Test Users** | ✅ Selesai | Akun Gmail pemilik (`chucklebytes482@gmail.com`) terdaftar |
| **Debug SHA-1** | ✅ Terdaftar | `4F:44:55:9B:D9:76:AB:99:99:69:C1:F4:5A:EA:D7:F4:AF:7C:A0:FC` |
| **Debug SHA-256** | ✅ Terdaftar | `44:30:EA:45:85:8A:9E:E1:53:EB:DC:C7:4D:88:62:B5:42:58:66:65:BF:9D:33:D9:DA:41:D2:48:8C:4A:1F:30` |
| **Release SHA-1 (Permanen)** | ✅ Terdaftar | `58:77:B4:2E:32:3F:DF:EB:E9:72:77:B0:15:04:F0:5E:2A:CC:BE:EA` |
| **Release SHA-256 (Permanen)** | ✅ Terverifikasi | `6E:31:B8:29:85:1B:F5:DC:A7:8E:FD:96:85:85:A6:B4:B1:07:B0:3E:4D:77:B0:CB:0F:9E:AF:36:F2:B2:DA:B3` |
| **GitHub Signing Secrets** | ✅ Aktif | `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS=upload` |
| **GitHub Actions Release** | ✅ Aktif | `.github/workflows/release.yml` — otomatis saat `push` ke `main` bila ada perubahan berkas aplikasi (`app/**`, Gradle, `.env.example`) + manual via **Run workflow** |
| **`GOOGLE_SERVICES_JSON_BASE64`** | ℹ️ Tidak diperlukan | PetaPikir memakai `AuthorizationClient` + Drive REST API v3 langsung (tanpa Firebase SDK) |
| **Repositori & Rilis GitHub** | ✅ Aktif | `https://github.com/chucklebytes482/petapikir` • `https://github.com/chucklebytes482/petapikir/releases/latest` |
| **Web Panduan (GitHub Pages)** | ✅ Aktif | `https://chucklebytes482.github.io/petapikir/` |

---

## Tautan Langsung Google Cloud Console (Ramah HP)

> **Tips HP:** Aktifkan **Situs Desktop / Mode Desktop** di browser jika menu Google Cloud Console terpotong di layar kecil.

- **Google Drive API:** [Buka Library Drive API](https://console.cloud.google.com/apis/library/drive.googleapis.com?project=gen-lang-client-0224561583)
- **OAuth Branding / App Domain:** [Buka Branding](https://console.cloud.google.com/auth/branding?project=gen-lang-client-0224561583)
- **Audience / Test Users:** [Buka Audience](https://console.cloud.google.com/auth/audience?project=gen-lang-client-0224561583)
- **OAuth 2.0 Credentials:** [Buka Credentials](https://console.cloud.google.com/apis/credentials?project=gen-lang-client-0224561583)

---

## Troubleshooting OAuth Android

| Pesan / Gejala | Penyebab | Solusi |
|---|---|---|
| `ApiException: 10 (DEVELOPER_ERROR)` | Package name atau SHA-1 APK terpasang tidak cocok | Pastikan APK rilis ditandatangani kunci permanen (`SHA-1: 58:77:...` / `SHA-256: 6E:31:...`) dan cocok dengan OAuth Client Android `com.aistudio.petapikir.krmxvp`. |
| `Access blocked: has not completed Google verification` | Email Gmail belum masuk daftar *Test users* | Tambahkan email Gmail di **Audience → Test users** pada proyek `gen-lang-client-0224561583`. |
| `403 accessNotConfigured` | Google Drive API belum aktif | Buka tautan **Library Drive API** di atas dan pastikan status **Enabled**. |
| `403 insufficientPermissions` | Izin Drive belum dicentang saat layar persetujuan | Ketuk kartu **Google Drive** → **Putuskan Akun**, lalu hubungkan ulang dan centang izin Google Drive. |
