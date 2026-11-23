# Status Kesiapan Rilis, Dual Repo & Sertifikat — PetaPikir (Versi Publik)

Dokumen publik ini tersimpan di folder `panduan/` sebagai referensi status arsitektur **100% Luring (Offline)**, pencadangan lokal otomatis ke `/storage/emulated/0/Documents/`, konfigurasi **Dual Repo GitHub Actions**, dan sidik jari sertifikat publik (Debug & Release) untuk aplikasi Android **PetaPikir**.

> **Catatan Keamanan:** Dokumen ini **tidak** memuat kata sandi, keystore privat, token, maupun nilai Base64 rahasia. Seluruh berkas sensitif dikelola secara terpisah di dalam kontainer **VeraCrypt (`.hc`)** di folder `secret/` yang diblokir penuh oleh `.gitignore` (hanya lokal di AI Studio, tidak pernah masuk GitHub) dan dibuka di HP menggunakan **Vault Explorer (F-Droid)**.

---

## 1. Ringkasan Identitas & Status Kesiapan Proyek

| Komponen | Nilai / Konfigurasi | Status |
|---|---|---|
| **Nama Aplikasi** | `PetaPikir` | Aktif |
| **Package Name (`applicationId`)** | `com.aistudio.petapikir.krmxvp` | Aktif |
| **Mode Operasi Aplikasi** | **100% Luring (0 Izin Internet/Jaringan)** | Aktif |
| **Pencadangan Otomatis Default** | `/storage/emulated/0/Documents/` *(tanpa subfolder otomatis; bebas pilih subfolder di dalam `Documents/`)* | Aktif |
| **Repositori Publik / Rilis** | `https://github.com/chucklebytes482/petapikir` | Siap |
| **GitHub Pages (Folder `docs/`)** | `https://chucklebytes482.github.io/petapikir/` | Siap |
| **Kebijakan Privasi & Ketentuan** | `docs/privacy.html` & `docs/terms.html` | Diperbarui (Luring) |
| **Workflow Rilis Dual Repo** | `.github/workflows/release.yml` (R8 + shrink + `arm64-v8a`, < 10 MB) | Aktif |
| **Workflow Sinkronisasi `docs/` Dual Repo** | `.github/workflows/sync-docs-public.yml` (khusus `docs/` ke `PUBLIC_REPO`) | Aktif |
| **Integrasi Google Drive / OAuth** | **Dinonaktifkan** *(arsitektur luring penuh — tidak memerlukan `INTERNET` atau `google-services.json`)* | Nonaktif |
| **Penataan Folder Repo** | `panduan/` (panduan & template), `docs/` (khusus GitHub Pages), `secret/` (`.gitignore` penuh) | Aktif |

---

## 2. Sidik Jari Sertifikat Publik (Debug & Release Permanen)

Nilai SHA-1 dan SHA-256 berikut bersifat **publik** dan dipertahankan permanen agar pembaruan APK PetaPikir selalu dapat menimpa pemasangan sebelumnya dengan aman:

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

## 3. Arsitektur Dual Repo & GitHub Secrets

Pada mode **Dual Repo**, kode sumber & panduan tetap aman di repositori privat, sedangkan **APK rilis** dan **halaman web `docs/`** dipublikasikan otomatis ke repositori publik target (`PUBLIC_REPO`):

| Nama Secret | Isi Nilai | Keterangan |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | Teks Base64 satu baris dari keystore rilis permanen (`petapikir-release.jks`) | Wajib untuk tanda tangan rilis permanen |
| `STORE_PASSWORD` | Kata sandi keystore rilis | Wajib |
| `KEY_PASSWORD` | Kata sandi alias `upload` | Wajib |
| `KEY_ALIAS` | `upload` | Disarankan |
| `PUBLIC_REPO` | Format `owner/repo-publik` *(mis. `chucklebytes482/petapikir`)* | Opsional — bila kosong, rilis otomatis masuk repo saat ini |
| `PUBLIC_REPO_TOKEN` | Fine-Grained PAT (`Contents: Read and write` khusus repo `PUBLIC_REPO`) | Wajib jika `PUBLIC_REPO` disetel |

### Tautan Cepat GitHub (Ramah HP)
- **GitHub Actions Secrets:** `https://github.com/chucklebytes482/petapikir/settings/secrets/actions`
- **Workflow Build & Release:** `https://github.com/chucklebytes482/petapikir/actions/workflows/release.yml`
- **Workflow Sync Docs Dual Repo:** `https://github.com/chucklebytes482/petapikir/actions/workflows/sync-docs-public.yml`
- **Fine-Grained Personal Access Tokens:** `https://github.com/settings/personal-access-tokens/new`
- **GitHub Releases Terbaru:** `https://github.com/chucklebytes482/petapikir/releases/latest`
