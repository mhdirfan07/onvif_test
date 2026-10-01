class CameraStatus {
  final bool running;
  final String? ip;
  final String? rtspUrl;
  final int httpPort;
  final int rtspClients;
  final bool cameraOpen;
  final String? lastError;

  const CameraStatus({
    this.running = false,
    this.ip,
    this.rtspUrl,
    this.httpPort = 0,
    this.rtspClients = 0,
    this.cameraOpen = false,
    this.lastError,
  });

  factory CameraStatus.fromMap(Map<dynamic, dynamic> map) => CameraStatus(
        running: map['running'] as bool? ?? false,
        ip: map['ip'] as String?,
        rtspUrl: map['rtspUrl'] as String?,
        httpPort: (map['httpPort'] as num?)?.toInt() ?? 0,
        rtspClients: (map['rtspClients'] as num?)?.toInt() ?? 0,
        cameraOpen: map['cameraOpen'] as bool? ?? false,
        lastError: map['lastError'] as String?,
      );

  static const initial = CameraStatus();
}
