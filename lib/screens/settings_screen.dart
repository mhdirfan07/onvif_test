import 'package:flutter/material.dart';

import '../models/camera_option.dart';
import '../models/server_config.dart';
import '../services/native_bridge.dart';

class SettingsScreen extends StatefulWidget {
  final ServerConfig config;

  const SettingsScreen({super.key, required this.config});

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

class _SettingsScreenState extends State<SettingsScreen> {
  late final _username = TextEditingController(text: widget.config.username);
  late final _password = TextEditingController(text: widget.config.password);
  late final _rtspPort = TextEditingController(text: '${widget.config.rtspPort}');
  late final _httpPort = TextEditingController(text: '${widget.config.httpPort}');
  late final _deviceName = TextEditingController(text: widget.config.deviceName);
  late final _location = TextEditingController(text: widget.config.location);

  String? _selectedCameraId;
  List<CameraOption> _cameras = [];
  bool _loadingCameras = true;

  @override
  void initState() {
    super.initState();
    _selectedCameraId = widget.config.cameraId;
    _loadCameras();
  }

  Future<void> _loadCameras() async {
    try {
      final cameras = await NativeBridge.instance.getAvailableCameras();
      setState(() {
        _cameras = cameras;
        if (_selectedCameraId != null && !cameras.any((c) => c.id == _selectedCameraId)) {
          _selectedCameraId = null;
        }
        _loadingCameras = false;
      });
    } catch (_) {
      setState(() => _loadingCameras = false);
    }
  }

  void _save() {
    final updated = ServerConfig(
      username: _username.text.trim().isEmpty ? 'admin' : _username.text.trim(),
      password: _password.text,
      rtspPort: int.tryParse(_rtspPort.text) ?? widget.config.rtspPort,
      httpPort: int.tryParse(_httpPort.text) ?? widget.config.httpPort,
      deviceName: _deviceName.text.trim().isEmpty ? 'OnvifCam' : _deviceName.text.trim(),
      location: _location.text.trim(),
      cameraId: _selectedCameraId,
    );
    Navigator.of(context).pop(updated);
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Settings'),
        actions: [
          IconButton(icon: const Icon(Icons.check), onPressed: _save),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('Camera input', style: Theme.of(context).textTheme.titleMedium),
              IconButton(
                icon: const Icon(Icons.refresh),
                tooltip: 'Rescan cameras (e.g. after plugging in a USB webcam)',
                onPressed: () {
                  setState(() => _loadingCameras = true);
                  _loadCameras();
                },
              ),
            ],
          ),
          const SizedBox(height: 8),
          if (_loadingCameras)
            const LinearProgressIndicator()
          else if (_cameras.isEmpty)
            const Text(
              'No cameras detected yet. Grant camera permission and reopen settings.',
              style: TextStyle(color: Colors.grey),
            )
          else
            DropdownButtonFormField<String?>(
              initialValue: _selectedCameraId,
              decoration: const InputDecoration(border: OutlineInputBorder()),
              items: [
                const DropdownMenuItem<String?>(
                  value: null,
                  child: Text('Auto (back camera)'),
                ),
                ..._cameras.map(
                  (c) => DropdownMenuItem<String?>(
                    value: c.id,
                    child: Text(c.label),
                  ),
                ),
              ],
              onChanged: (value) => setState(() => _selectedCameraId = value),
            ),
          const SizedBox(height: 24),
          const Text(
            'These credentials are required by any VMS connecting over RTSP/SOAP (HTTP Digest).',
            style: TextStyle(color: Colors.grey),
          ),
          const SizedBox(height: 16),
          TextField(
            controller: _username,
            decoration: const InputDecoration(labelText: 'Username'),
          ),
          TextField(
            controller: _password,
            obscureText: true,
            decoration: const InputDecoration(labelText: 'Password'),
          ),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _rtspPort,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'RTSP port'),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: TextField(
                  controller: _httpPort,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(labelText: 'ONVIF HTTP port'),
                ),
              ),
            ],
          ),
          TextField(
            controller: _deviceName,
            decoration: const InputDecoration(labelText: 'Device name (ONVIF scope)'),
          ),
          TextField(
            controller: _location,
            decoration: const InputDecoration(labelText: 'Location (ONVIF scope)'),
          ),
        ],
      ),
    );
  }
}
