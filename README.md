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

Cadangan dilakukan ke folder lokal di perangkat.

## Keputusan teknis

- Offline: sinkron Google Drive dihapus. Cadangan folder lokal tetap dipertahankan.
- Kolom `driveFileId` di tabel Room dibiarkan agar skema database tidak berubah.
- Dependensi jaringan (Retrofit, OkHttp, Moshi, Coil, Google Play Services) dihapus dari Gradle.
- Izin `INTERNET` tidak dideklarasikan.
