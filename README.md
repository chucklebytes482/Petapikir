# Petapikir

Aplikasi peta pikiran (mind map) Android yang berjalan sepenuhnya offline.

## Build dan tes

```
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## Lokasi data

Data disimpan di database Room lokal di perangkat (`AppDatabase`, tabel `MindMapEntity`).

## Cadangan

Cadangan dilakukan ke satu berkas JSON di penyimpanan perangkat (autosync, lihat keputusan di bawah).

## Keputusan teknis

- Offline: sinkron Google Drive dihapus. Cadangan folder lokal tetap dipertahankan.
- Kolom `driveFileId` di tabel Room dibiarkan agar skema database tidak berubah.
- Dependensi jaringan (Retrofit, OkHttp, Moshi, Coil, Google Play Services) dihapus dari Gradle.
- Izin `INTERNET` tidak dideklarasikan.
- Cadangan pindah ke `/storage/emulated/0/autosync/petapikir_backup_json/petapikir.json` (satu berkas JSON, tanpa pilihan folder). Mengikuti `05-cadangan-dan-sinkron.md` di repo plan.
- Penggabungan memakai last-write-wins per dokumen. Hapus memakai penanda hapus (tombstone) yang tidak dibersihkan, sehingga berkas makin besar.
- Jika berkas tidak bisa dibaca atau rusak, sinkron tidak menulis ulang berkas.
- Pra-Android 10 (API 24–28) menulis ke folder privat aplikasi di penyimpanan eksternal, bukan ke `/storage/emulated/0/autosync`.
- Berkas JSON lama per peta di `Documents/` tidak dihapus dan tidak dibaca lagi.
- Penggabungan pertama dengan berkas yang sudah ada dilakukan otomatis tanpa konfirmasi.
- Belum dibangun atau diuji di sandbox; verifikasi hanya lewat CI.
