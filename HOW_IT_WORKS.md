# Cara Kerja OnvifCam — dari App Sampai Terhubung ke Blue Iris

Dokumen ini menjelaskan alur teknis lengkap: bagaimana HP Android (via app
`onvifcam`) berubah jadi kamera IP yang bisa ditarik streamnya oleh VMS
(Blue Iris), protokol apa saja yang terlibat, dan file kode mana yang
bertanggung jawab atas tiap bagian.

## 1. Gambaran Besar

```
┌─────────────┐     ┌──────────────────────────────────────────────┐     ┌───────────┐
│  Flutter UI  │◄──►│         Native Android (Kotlin)                │     │ Blue Iris │
│ (lib/)       │     │                                                │     │   (VMS)   │
│              │     │  Kamera → Encoder H.264 → RTP → Server RTSP    │◄───►│           │
└─────────────┘     └──────────────────────────────────────────────┘     └───────────┘
   MethodChannel /        foreground Service, semua kerja berat              TCP koneksi
   EventChannel           di sini, jalan walau layar mati                    ke port 8554
```

Flutter cuma jadi **panel kontrol** (tombol start/stop, pilih kamera,
username/password, lihat status & log). Semua pekerjaan berat — buka
kamera, encode video, kirim data lewat jaringan — jalan di **native Android
Service**, supaya tetap jalan walau app di-background atau layar dikunci.

## 2. Komponen Native & Perannya

| File | Peran |
|---|---|
| `MainActivity.kt` | Jembatan Flutter ↔️ Service. Terima panggilan dari Flutter (`MethodChannel`), kirim status/log balik ke Flutter (`EventChannel`). |
| `service/CameraServerService.kt` | **Otak orkestrasi.** Foreground Service yang merangkai semua komponen di bawah jadi satu pipeline saat "Start server" ditekan. |
| `capture/CameraSourceRegistry.kt` | Memilih sumber video: kamera bawaan (Camera2) atau kamera USB (UVC), berdasarkan pilihan user di Settings. |
| `capture/CameraCaptureController.kt` | Buka kamera bawaan HP via Android **Camera2 API**. |
| `capture/UvcCaptureController.kt` | Buka **kamera USB (webcam UVC)** langsung lewat USB, bukan lewat Camera2 (lihat §5). |
| `encode/H264Encoder.kt` | Bungkus `MediaCodec` Android: ubah gambar mentah dari kamera jadi video terkompresi **H.264**. |
| `rtp/RtpPacketizer.kt`, `rtp/RtpSession.kt`, `rtp/StreamFanOut.kt` | Pecah data H.264 jadi paket-paket **RTP** sesuai standar (RFC 6184), kirim ke tiap client yang nonton. |
| `rtsp/RtspServer.kt`, `rtsp/RtspConnectionHandler.kt` | Server **RTSP**: "bahasa" yang dipakai VMS untuk minta/atur stream video. |
| `rtsp/SdpBuilder.kt` | Bikin deskripsi teknis stream (format video, resolusi, dst.) yang dikirim ke VMS saat diminta. |
| `auth/DigestAuthenticator.kt` | Cek username/password pakai standar **HTTP Digest**, dipakai RTSP server. |

## 3. Apa yang Terjadi Saat "Start Server" Ditekan

1. `CameraServerService` jalan sebagai **foreground service** (notifikasi
   permanen muncul, Android tak akan bunuh proses ini sembarangan).
2. `H264Encoder` disiapkan: minta Android buat `MediaCodec` encoder H.264
   dengan resolusi/target 1280×720@30fps, dan diberi sebuah **Surface**
   (kanvas digital) sebagai tempat menerima gambar mentah.
3. `RtspServer` mulai **listen** di port 8554 (nunggu koneksi masuk).
4. `CameraSourceRegistry` membuka kamera yang dipilih user, dan diarahkan
   supaya gambar yang ditangkap langsung digambar ke **Surface yang sama**
   milik encoder — jadi tak ada proses copy manual (disebut *zero-copy*).
5. Begitu kamera mengirim gambar, `MediaCodec` otomatis mengubahnya jadi
   H.264 dan memanggil callback kita dengan potongan data terkompresi.

Di titik ini, server sudah "siap" — tinggal menunggu VMS datang meminta
stream.

## 4. Percakapan RTSP: Bagaimana Blue Iris Sebenarnya "Connect"

Saat kamu isi `rtsp://192.168.1.29:8554/onvif1` di Blue Iris, ini yang
terjadi di baliknya — semuanya lewat **satu koneksi TCP** ke port 8554:

```
Blue Iris                                   OnvifCam (HP)
    │                                             │
    │──── OPTIONS ───────────────────────────────►│   "Metode apa yg kamu dukung?"
    │◄─── 200 OK (daftar metode) ─────────────────│
    │                                             │
    │──── DESCRIBE (tanpa kredensial) ───────────►│
    │◄─── 401 Unauthorized + tantangan Digest ────│   "Butuh login, ini nonce-nya"
    │                                             │
    │──── DESCRIBE (dengan Authorization) ───────►│
    │◄─── 200 OK + SDP (deskripsi video) ─────────│   berisi info: H.264, ukuran
    │                                             │   gambar, SPS/PPS (parameter dekoder)
    │──── SETUP (mode transport RTP) ────────────►│   "Kirim video lewat jalur mana?"
    │◄─── 200 OK (jalur disepakati) ──────────────│
    │                                             │
    │──── PLAY ───────────────────────────────────►│   "Mulai kirim!"
    │◄─── 200 OK ──────────────────────────────────│
    │                                             │
    │◄══════ paket-paket RTP (video) ═════════════│   mengalir terus...
    │                                             │
```

Penjelasan tiap langkah:

- **OPTIONS** — basa-basi awal, cek metode apa saja yang didukung server.
  Tidak butuh login.
- **DESCRIBE** — Blue Iris minta "spesifikasi" stream. Server **wajib**
  minta login dulu (kecuali OPTIONS) — makanya balasan pertama selalu
  `401` berisi *nonce* acak. Blue Iris lalu hitung ulang response digest
  pakai username/password yang kamu isi di Settings app, kirim lagi.
  Kalau cocok → server balas `200 OK` beserta **SDP**, isinya antara lain
  `sprop-parameter-sets` (SPS/PPS, semacam "kamus" yang dibutuhkan
  decoder video Blue Iris untuk mengerti format H.264 yang dikirim).

  > ⚠️ Kalau kamera belum sempat kirim frame pertama (baru buka / gagal
  > buka), server balas `503 Service Unavailable` di sini — bukan error
  > jaringan, tapi tanda kamera belum siap.

- **SETUP** — negosiasi "lewat jalur mana video dikirim". Ada dua mode:
  - **UDP terpisah**: server buka 2 socket UDP baru (RTP + RTCP), Blue
    Iris kasih tahu port UDP miliknya, server kirim video ke situ.
  - **TCP interleaved** (lebih umum dipakai Blue Iris secara default):
    video "ditumpangkan" di koneksi TCP yang sama dengan perintah RTSP,
    dibungkus header kecil (`$` + nomor kanal + panjang data) supaya bisa
    dibedakan dari teks perintah RTSP biasa.
- **PLAY** — sinyal "mulai". Server langsung minta encoder membuat
  **keyframe** (gambar penuh, bukan cuma "perubahan") supaya Blue Iris
  langsung dapat gambar utuh, tak perlu nunggu keyframe terjadwal
  berikutnya.
- Setelah itu, **setiap kali encoder menghasilkan potongan video baru**,
  `StreamFanOut` memecahnya jadi satu atau beberapa **paket RTP** (potongan
  besar dipecah lagi jadi beberapa paket kalau ukurannya melebihi batas
  jaringan — disebut *FU-A fragmentation*), lalu mengirimkannya ke semua
  client yang sedang nonton (bisa lebih dari satu sekaligus).

## 5. Dua Jalur Kamera yang Berbeda

### Kamera bawaan HP (Camera2)

Jalur standar Android: `CameraCaptureController` minta `CameraManager`
buka sensor kamera, arahkan hasilnya ke Surface milik encoder. Simpel,
selalu didukung.

### Kamera USB (UVC webcam)

Ini kasus khusus. Kamera USB **tidak** muncul di Camera2 API kebanyakan
HP (sudah dibuktikan lewat `dumpsys media.camera` — device sempat
terdeteksi sekejap lalu hilang lagi). Solusinya: bicara **langsung ke USB**
pakai protokol UVC (USB Video Class), lewat library `org.uvccamera`:

1. `USBMonitor` (dari library) mendeteksi device USB yang tersambung,
   minta izin akses ke Android (`UsbManager.requestPermission` — muncul
   dialog sistem).
2. Setelah diizinkan, `UVCCamera` membuka koneksi ke webcam, mengatur
   resolusi & format capture-nya.
3. `cam.setPreviewDisplay(surface)` — sama seperti Camera2, hasil gambar
   digambar langsung ke Surface milik encoder.

**Kesulitan yang ditemukan:** banyak webcam USB hanya mendukung resolusi
tinggi (mis. 1280×720) dalam format **MJPEG** (terkompresi), bukan
**YUYV** (mentah) yang jadi default library. Kalau format yang diminta
tak didukung kamera, `startPreview()` tetap "sukses" secara API tapi tak
pernah benar-benar mengirim gambar — akibatnya encoder tak pernah
menghasilkan video, dan Blue Iris terus-menerus connect-disconnect karena
tak kunjung dapat data. Makanya kode sekarang mencoba beberapa kombinasi
resolusi+format (MJPEG dulu, baru YUYV, baru ukuran default) sampai salah
satu diterima kamera.

## 6. Autentikasi (HTTP Digest)

Username/password yang kamu isi di Settings app **tidak** dikirim polos.
`DigestAuthenticator` mengimplementasi **HTTP Digest** (RFC 2617):

1. Server kasih *nonce* (angka acak sekali pakai) + *realm*.
2. Client (Blue Iris) hitung hash `MD5(username:realm:password)` lalu
   digabung dengan method+URL+nonce, hasilnya hash lagi → dikirim sebagai
   `response`.
3. Server hitung ulang hash yang sama, bandingkan. Password asli tidak
   pernah melewati jaringan.

RTSP (RFC 2326) memang didesain untuk pakai persis skema header Digest
yang sama dengan HTTP biasa, jadi satu class ini otomatis bisa dipakai
untuk RTSP tanpa perlu ditulis dua kali.

## 7. Kenapa Restart Server Kadang Bermasalah (dan Sudah Diperbaiki)

Dua bug yang sempat muncul saat testing, sudah diperbaiki:

- **`EADDRINUSE` (port RTSP bentrok)** — kalau stop lalu langsung start
  lagi, port lama kadang belum benar-benar dilepas OS. Fix: socket RTSP
  sekarang pakai `SO_REUSEADDR` supaya bisa langsung dipakai ulang.
- **Kamera USB gagal dibuka (`open failed:result=-99`)** — kalau start
  dipanggil sebelum proses "tutup kamera" dari stop sebelumnya selesai,
  dua proses rebutan akses ke device USB yang sama. Fix: `start()`
  sekarang menunggu (dengan batas waktu) proses teardown sebelumnya
  selesai dulu, dijalankan di thread terpisah supaya UI app tak ikut
  macet (ANR) kalau proses tutup kameranya lambat/macet.

## 8. Yang Sudah Jalan vs Yang Belum (Batasan Saat Ini)

✅ **Sudah jalan:**
- Streaming video H.264 real-time via RTSP/RTP (UDP maupun TCP
  interleaved).
- Autentikasi HTTP Digest.
- Sumber video: kamera bawaan (depan/belakang) atau kamera USB (UVC).
- Terhubung ke Blue Iris sebagai kamera **Generic/RTSP** (alamat RTSP
  dimasukkan manual, bukan otomatis ditemukan).

❌ **Belum ada** (bagian dari standar ONVIF penuh, di luar cakupan saat ini):
- **WS-Discovery** — HP belum bisa "ditemukan otomatis" di jaringan;
  alamat IP+port harus diketik manual di Blue Iris.
- **SOAP Device/Media Service** (endpoint `/onvif/device_service`, dst.)
  — belum ada, jadi VMS yang mewajibkan "ONVIF asli" (bukan sekadar RTSP
  generik) belum tentu bisa mendeteksi kamera ini.
- **Static IP otomatis** — app cuma kasih instruksi manual, tidak bisa
  ubah setting jaringan sendiri.

Singkatnya: yang sudah dibangun adalah **kamera RTSP dengan autentikasi**
yang berfungsi penuh, sedangkan lapisan "ONVIF" (discovery + SOAP) yang
membuatnya dikenali sebagai kamera ONVIF standar oleh sembarang VMS masih
menjadi pekerjaan lanjutan.
