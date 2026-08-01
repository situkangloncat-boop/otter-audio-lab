# OTTER AUDIO EXPRESS

Aplikasi Android untuk mengubah bagian-bagian lagu secara otomatis lewat slider:
Tempo, Pitch/Nada, Volume, Bass, Mid, Treble, Reverb, Echo/Delay, Panning,
Kompresi, Distorsi/Saturasi, Fade In/Out, Loop, Trim, dan mode Vokal/Instrumen.

## Yang baru di versi ini

- **Tombol Stop** di kontrol pemutaran hasil (sebelumnya cuma ada Putar).
- **Kotak input angka di setiap slider** — geser slider atau ketik angka
  langsung, keduanya saling sinkron, jadi tidak perlu lagi menebak-nebak
  posisi persentase.
- **Nama file hasil edit bisa ditentukan sendiri** sebelum diproses (kotak
  "Nama File Hasil Edit"), dipakai sebagai nama file `.m4a` yang keluar.
- **Panel Trim baru**: menampilkan gelombang (waveform) lagu asli, bisa
  diputar langsung di situ, dan bagian yang mau dipotong ditentukan dengan
  menggeser gagang kuning di atas gelombangnya (atau tetap bisa ketik
  detiknya manual di kotak Mulai/Selesai — keduanya saling sinkron).
- **Logo & nama aplikasi baru**: ikon launcher memakai logo yang dikirim
  pengguna, dan nama aplikasi berubah jadi "OTTER AUDIO EXPRESS".

## PENTING — kenapa ini source code, bukan .apk jadi

Saya (Claude) tidak punya Android SDK/Gradle dan tidak ada akses internet di
lingkungan kerja saya, jadi tidak bisa compile & sign .apk langsung. Project
ini source code lengkap yang tinggal di-build sendiri di Android Studio —
prosesnya cuma beberapa menit.

## Cara build APK

1. Download & extract folder project ini.
2. Buka **Android Studio** → `Open` → pilih folder `OtterAudioLab`.
3. Tunggu Gradle sync selesai (butuh internet, akan download dependency
   otomatis termasuk FFmpegKit).
4. Cek dulu versi terbaru dependency FFmpegKit di
   `app/build.gradle.kts` — buka
   https://mvnrepository.com/artifact/io.github.jamaismagic.ffmpeg/ffmpeg-kit-lts-full-16kb
   dan ganti angka versi kalau sudah ada yang lebih baru dari `6.1.7`.
5. Build → **Build Bundle(s) / APK(s) → Build APK(s)**.
6. APK hasil ada di `app/build/outputs/apk/debug/app-debug.apk`.
7. Untuk APK yang bisa di-share/install permanen (release, signed),
   pakai `Build → Generate Signed Bundle / APK`.

## Kenapa pakai FFmpegKit fork komunitas, bukan yang resmi?

FFmpegKit resmi (`com.arthenica`) **sudah pensiun/dihentikan** dan binary-nya
dihapus dari Maven Central per April 2025 — kalau dipakai, Gradle sync akan
gagal ("could not find..."). Project ini pakai fork komunitas
(`io.github.jamaismagic.ffmpeg`) yang masih aktif dirilis dan package API-nya
tetap `com.arthenica.ffmpegkit.*` (sama persis), jadi kode `MainActivity.kt`
tidak perlu diubah kalau suatu saat mau pindah dependency lagi.

## Cara kerja

Semua 16 parameter di slider dirangkai jadi **satu filter chain FFmpeg**
(fungsi `buildFilterChain()` di `MainActivity.kt`), lalu dieksekusi sekali
lewat `FFmpegKit.executeAsync(...)`. Urutan proses: trim → tempo/pitch →
EQ (bass/mid/treble) → kompresi → distorsi → panning → vokal/instrumen →
reverb → echo → volume → fade → loop.

| Fitur di UI | Filter FFmpeg yang dipakai |
|---|---|
| Tempo | `atempo` (dirangkai otomatis kalau di luar 0.5x–2.0x) |
| Nada/Pitch | `asetrate` + `aresample` + `atempo` (pitch independen dari tempo) |
| Volume | `volume` |
| Bass | `bass` |
| Mid | `equalizer` (band 1kHz) |
| Treble | `treble` |
| Reverb | `aecho` multi-tap (pendekatan, bukan convolution reverb asli) |
| Echo/Delay | `aecho` |
| Panning | `stereotools` |
| Kompresi | `acompressor` |
| Distorsi/Saturasi | `asoftclip` |
| Fade In/Out | `afade` |
| Loop | `aloop` |
| Trim/Cut | `-ss` / `-to` pada input |

## Keterbatasan yang perlu kamu tahu

- **Vokal/Instrumen**: fitur ini pakai trik pembatalan channel-tengah
  (`pan=stereo|c0=c0-c1|c1=c1-c0` untuk mengurangi vokal, `stereotools`
  untuk memperkuat). Ini **bukan** pemisahan sumber suara berbasis AI
  (seperti Spleeter/Demucs). Hasilnya lumayan di lagu stereo dengan vokal
  di tengah, tapi tidak akan bersih 100% dan tidak bekerja di lagu mono.
  Kalau kamu butuh pemisahan vokal/instrumen yang benar-benar bersih, itu
  perlu model machine learning terpisah (di luar cakupan app single-file
  ini) — beri tahu saya kalau mau saya bantu rancang jalur itu juga.
- **Reverb**: pendekatan multi-tap echo, bukan convolution reverb
  berkualitas studio.
- Semua efek diproses **sekali jalan, offline** (bukan real-time saat lagu
  diputar) — pilih lagu → atur slider → tekan Proses → hasil baru bisa
  diputar/disimpan.
- Format output: `.m4a` (AAC 192kbps). Bisa diganti ke `.mp3` di
  `MainActivity.kt` (ganti `-c:a aac` jadi `-c:a libmp3lame`, dan
  pastikan package FFmpegKit yang dipakai menyertakan `lame` — variant
  `full` di project ini sudah termasuk).

## Struktur project

```
OtterAudioLab/
├── app/
│   ├── build.gradle.kts          (dependency FFmpegKit di sini)
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/otter/audiolab/
│       │   ├── MainActivity.kt          (semua logic + UI slider/efek)
│       │   ├── TrimWaveformView.kt      (view waveform + gagang trim)
│       │   └── WaveformExtractor.kt     (decode audio -> data waveform)
│       └── res/layout/activity_main.xml              (UI dasar)
├── build.gradle.kts
└── settings.gradle.kts
```
