# Spesifikasi Kredensial Rilis & Kontainer VeraCrypt HTML (Umum)

Dokumentasi teknis standar untuk menghasilkan kredensial rilis permanen Android, mengemas **3 berkas rahasia** ke kontainer **VeraCrypt v5 asli (1–2 MiB)** (`<slug_app>_secrets.hc`), lalu membungkusnya ke dalam **1 berkas HTML pengunduh mandiri di root** (`<slug_app>_secrets_download.html`).

> **Cara Pakai:** Lampirkan atau referensikan dokumen ini ke AI lalu cukup ketik **"jalankan"** atau **"jalankan yang sesuai saja"**.

---

## 1. Tiga Berkas Wajib di Dalam `<slug_app>_secrets.hc`

Kontainer hanya berisi **3 berkas tepat di root kontainer** dengan nama persis sama, **tanpa teks panduan/cara apa pun** di dalamnya:

| Nama Berkas | Isi Wajib | Aturan Format Markdown |
|---|---|---|
| `GITHUB_SECRETS.md` | `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS` | **Kotak kode salin (` ```text `) hanya untuk nilai panjang** (`RELEASE_KEYSTORE_BASE64` 1 baris utuh tanpa spasi/baris baru — otomatis tampil 1 baris standar dengan gulir horizontal). Nilai pendek (`STORE_PASSWORD`, `KEY_PASSWORD`, `KEY_ALIAS`) cukup teks/inline code biasa tanpa kotak kode besar. Tanpa teks cara/penjelasan. |
| `apk Fingerprint.md` | `Package name`, `Alias keystore`, `SHA-1`, `SHA-256` | `Package name` & `Alias keystore` ditulis ringkas (inline code); kotak kode salin (` ```text `) hanya untuk nilai panjang (`SHA-1` & `SHA-256`). Tanpa teks cara/penjelasan. |
| `release.jks` | Keystore rilis permanen Android | RSA 2048-bit, alias `upload`, berlaku 30 tahun (`-validity 10950`), `STORE_PASSWORD` & `KEY_PASSWORD` acak kuat 24 karakter (atau gunakan keystore existing bila pengguna melampirkannya). |

### Contoh Struktur Bersih `GITHUB_SECRETS.md`

```markdown
### RELEASE_KEYSTORE_BASE64

```text
<base64_1_baris_utuh_tanpa_spasi_atau_newline>
```

- **STORE_PASSWORD:** `<password_keystore>`
- **KEY_PASSWORD:** `<password_key>`
- **KEY_ALIAS:** `upload`
```

### Contoh Struktur Bersih `apk Fingerprint.md`

```markdown
- **Package name:** `<applicationId>`
- **Alias keystore:** `upload`

### SHA-1

```text
<AA:BB:CC:...>
```

### SHA-256

```text
<AA:BB:CC:...>
```
```

---

## 2. Spesifikasi Kontainer VeraCrypt v5 (Wajib 1–2 MiB)

- **Nama berkas kontainer:** `<slug_app>_secrets.hc`.
- **Ukuran wajib:** **1–2 MiB** (standar **1,00 MiB = 1.048.576 byte**, boleh hingga `2,00 MiB = 2.097.152 byte`).
- **Format enkripsi asli:** **VeraCrypt v5** (`VERA`, header format `0x0005`, minimum version `0x010b`):
  - Cipher: **AES-256-XTS** (unit data 512 byte, offset area data `131.072` byte / sektor `256`).
  - Derivasi kunci: **PBKDF2-HMAC-SHA512** (`500.000` iterasi / PIM default).
  - Integritas: header utama (64 KiB awal) dan header cadangan (64 KiB di `ukuran - 131.072`) keduanya terenkripsi dengan salt acak berbeda serta CRC-32 valid.
  - Filesystem internal: **`ext2`** (`mke2fs -t ext2 -b 1024 -m 0`).
- **Dilarang keras:** membuat arsip ZIP biasa yang diganti ekstensi `.hc`, kontainer di bawah 1 MiB, atau kata sandi palsu yang tidak benar-benar mengenkripsi data.

---

## 3. Aturan 1 Berkas HTML Pengunduh & Keamanan Repositori

- **Jangan dipisah di repositori:** seluruh berkas mentah (`GITHUB_SECRETS.md`, `apk Fingerprint.md`, `release.jks`) dan kontainer `.hc` **wajib diproses di `/tmp`** lalu dihapus bersih setelah dikemas ke HTML.
- **1 berkas tunggal di root:** hasil akhir yang diletakkan di root repositori hanya **`<slug_app>_secrets_download.html`** berisi biner `.hc` (Base64 dibungkus 76 karakter/baris) dengan:
  1. Tombol unduh utama (Blob),
  2. Tautan unduh cadangan (Data URL),
  3. Verifikasi **SHA-256** otomatis di browser sebelum mengunduh.
- **Aturan `.gitignore`:**
  - **Jangan** sembunyikan `<slug_app>_secrets_download.html` di `.gitignore` agar terlihat di root untuk langsung diunduh lewat browser lalu dihapus pengguna.
  - **Tetap blokir** seluruh rahasia mentah di `.gitignore`: `*.jks`, `*.keystore`, `*.b64`, `*.hc`, `keystore.properties`, `google-services.json`, `.env`.
- **Kata sandi hanya di chat:** sebutkan kata sandi kontainer `<slug_app>_secrets.hc` **hanya pada balasan chat** — dilarang keras menuliskan kata sandi ke dalam HTML atau berkas repositori mana pun.
- **Perlindungan Signing AI Studio:** dilarang mengubah `debug.keystore`, `debug.keystore.base64`, atau blok `signingConfigs` / `signingConfig` pada `app/build.gradle.kts`.
- **Catatan `PUBLIC_DIST_TOKEN` (Dual-Repo):** cukup gunakan **1 Fine-grained PAT bersama** (izin tunggal `Contents: Read and write` pada *All repositories* atau *Only select repositories*) untuk seluruh repo publik `*-site` tanpa membuat ulang PAT di setiap proyek baru.

---

## 4. Verifikasi Wajib Sebelum Selesai

1. Dekripsi ulang header utama & header cadangan `.hc` memakai kata sandi yang dihasilkan — pastikan magic `VERA`, versi `0x0005`, serta kedua CRC-32 cocok.
2. Dekripsi seluruh area data, verifikasi filesystem `ext2` dengan `e2fsck -fn`, lalu periksa isi menggunakan `debugfs` bahwa tepat 3 berkas (`GITHUB_SECRETS.md`, `apk Fingerprint.md`, `release.jks`) ada dan identik byte-per-byte.
3. Verifikasi `release.jks` dengan `keytool -list -v` agar alias, SHA-1, dan SHA-256 cocok 100% dengan `apk Fingerprint.md`.
4. Verifikasi round-trip Base64 dari `<slug_app>_secrets_download.html` menghasilkan ukuran 1–2 MiB dan SHA-256 identik 100% dengan `<slug_app>_secrets.hc`.
5. Pastikan tidak ada berkas mentah (`GITHUB_SECRETS.md`, `apk Fingerprint.md`, `release.jks`, `*.hc`, skrip sementara) yang tersisa di repositori maupun `/tmp`.
