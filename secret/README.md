# Arsip Secret Terenkripsi — PetaPikir

Folder ini menyimpan cadangan arsip **ZIP terenkripsi ber-password** untuk kredensial rilis dan kata sandi proyek **PetaPikir**.

> **Kenapa ada berkas `.zip.base64`?**
> Sinkronisasi GitHub otomatis di AI Studio memprioritaskan berkas teks UTF-8 (seperti `debug.keystore.base64`) dan dapat melewati berkas biner `.zip`. Karena itu setiap arsip terenkripsi juga disediakan dalam format teks Base64 (`.zip.base64`) yang tetap **100% terenkripsi dengan kata sandi** agar pasti muncul di repositori GitHub.

---

## Daftar Berkas

| Berkas | Isi (Terenkripsi) |
|---|---|
| `petapikir_release_secrets.zip.base64` *(+ `.zip`)* | `PetaPikir_Release_Setup_Guide.md`, `SETUP_GOOGLE_CLOUD.md`, `petapikir-release.jks`, `keystore.properties`, `CERT_FINGERPRINT.txt`, `google-services.json` |
| `bulk_katasandi_a-z.zip.base64` *(+ `.zip`)* | `bulk_katasandi_a-z.md`, `bulk_katasandi_a-z.txt` (26 kata sandi acak kuat berawalan `a`–`z`) |

---

## Cara Mengembalikan `.zip.base64` Menjadi `.zip`

### Di Linux / macOS / Termux (Android)
```bash
tr -d '[:space:]' < secret/petapikir_release_secrets.zip.base64 | base64 -d > petapikir_release_secrets.zip
unzip petapikir_release_secrets.zip
```
Untuk bulk kata sandi:
```bash
tr -d '[:space:]' < secret/bulk_katasandi_a-z.zip.base64 | base64 -d > bulk_katasandi_a-z.zip
unzip bulk_katasandi_a-z.zip
```

### Di Windows (PowerShell)
```powershell
[IO.File]::WriteAllBytes("petapikir_release_secrets.zip", [Convert]::FromBase64String((Get-Content -Raw secret\petapikir_release_secrets.zip.base64)))
```

> Setelah kembali menjadi berkas `.zip`, ekstrak dengan **ZArchiver** (HP), **7-Zip**, atau `unzip` lalu masukkan kata sandi enkripsi pribadi Anda.
