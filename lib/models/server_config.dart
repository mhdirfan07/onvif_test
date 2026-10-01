class ServerConfig {
  final String username;
  final String password;
  final int rtspPort;
  final int httpPort;
  final String deviceName;
  final String location;
  final String? cameraId;

  const ServerConfig({
    this.username = 'admin',
    this.password = 'admin',
    this.rtspPort = 8554,
    this.httpPort = 8080,
    this.deviceName = 'OnvifCam',
    this.location = '',
    this.cameraId,
  });

  ServerConfig copyWith({
    String? username,
    String? password,
    int? rtspPort,
    int? httpPort,
    String? deviceName,
    String? location,
    String? cameraId,
  }) {
    return ServerConfig(
      username: username ?? this.username,
      password: password ?? this.password,
      rtspPort: rtspPort ?? this.rtspPort,
      httpPort: httpPort ?? this.httpPort,
      deviceName: deviceName ?? this.deviceName,
      location: location ?? this.location,
      cameraId: cameraId ?? this.cameraId,
    );
  }

  Map<String, dynamic> toMap() => {
        'username': username,
        'password': password,
        'rtspPort': rtspPort,
        'httpPort': httpPort,
        'deviceName': deviceName,
        'location': location,
        'cameraId': cameraId,
      };

  Map<String, dynamic> toJson() => toMap();

  factory ServerConfig.fromJson(Map<String, dynamic> json) => ServerConfig(
        username: json['username'] as String? ?? 'admin',
        password: json['password'] as String? ?? 'admin',
        rtspPort: json['rtspPort'] as int? ?? 8554,
        httpPort: json['httpPort'] as int? ?? 8080,
        deviceName: json['deviceName'] as String? ?? 'OnvifCam',
        location: json['location'] as String? ?? '',
        cameraId: json['cameraId'] as String?,
      );
}
