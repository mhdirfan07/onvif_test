import 'package:flutter/services.dart';

import '../models/camera_option.dart';
import '../models/camera_status.dart';
import '../models/log_entry.dart';
import '../models/server_config.dart';

class NativeBridge {
  NativeBridge._();
  static final NativeBridge instance = NativeBridge._();

  static const _control = MethodChannel('com.aranustech.onvifcam/control');
  static const _events = EventChannel('com.aranustech.onvifcam/events');

  Stream<Map<dynamic, dynamic>>? _rawEvents;

  Stream<Map<dynamic, dynamic>> get _events$ {
    return _rawEvents ??= _events.receiveBroadcastStream().map((e) => e as Map<dynamic, dynamic>).asBroadcastStream();
  }

  Stream<CameraStatus> get statusStream => _events$
      .where((e) => e['type'] == 'status')
      .map((e) => CameraStatus.fromMap(e));

  Stream<LogEntry> get logStream => _events$
      .where((e) => e['type'] == 'log')
      .map((e) => LogEntry.fromMap(e));

  Future<CameraStatus> startServer(ServerConfig config) async {
    final result = await _control.invokeMethod<Map<dynamic, dynamic>>('startServer', config.toMap());
    return CameraStatus.fromMap(result ?? const {});
  }

  Future<CameraStatus> stopServer() async {
    final result = await _control.invokeMethod<Map<dynamic, dynamic>>('stopServer');
    return CameraStatus.fromMap(result ?? const {});
  }

  Future<CameraStatus> getStatus() async {
    final result = await _control.invokeMethod<Map<dynamic, dynamic>>('getStatus');
    return CameraStatus.fromMap(result ?? const {});
  }

  Future<String?> getLocalIpAddress() {
    return _control.invokeMethod<String>('getLocalIpAddress');
  }

  Future<List<CameraOption>> getAvailableCameras() async {
    final result = await _control.invokeMethod<List<dynamic>>('getAvailableCameras');
    return (result ?? const [])
        .map((e) => CameraOption.fromMap(e as Map<dynamic, dynamic>))
        .toList();
  }
}
