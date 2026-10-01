import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';

import '../models/camera_status.dart';
import '../models/log_entry.dart';
import '../models/server_config.dart';
import '../services/native_bridge.dart';
import '../services/prefs_repository.dart';
import '../widgets/status_card.dart';
import 'logs_screen.dart';
import 'settings_screen.dart';
import 'static_ip_help_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _prefs = PrefsRepository();
  final _bridge = NativeBridge.instance;
  final List<LogEntry> _logs = [];

  ServerConfig _config = const ServerConfig();
  CameraStatus _status = CameraStatus.initial;
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _bridge.statusStream.listen((status) => setState(() => _status = status));
    _bridge.logStream.listen((entry) {
      setState(() {
        _logs.add(entry);
        if (_logs.length > 200) _logs.removeAt(0);
      });
    });
    _load();
  }

  Future<void> _load() async {
    final config = await _prefs.load();
    final status = await _bridge.getStatus();
    setState(() {
      _config = config;
      _status = status;
    });
  }

  Future<bool> _ensurePermissions() async {
    final results = await [
      Permission.camera,
      Permission.notification,
    ].request();
    return results.values.every((s) => s.isGranted || s.isLimited);
  }

  Future<void> _toggle() async {
    setState(() => _busy = true);
    try {
      if (_status.running) {
        final status = await _bridge.stopServer();
        setState(() => _status = status);
      } else {
        final granted = await _ensurePermissions();
        if (!granted) {
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              const SnackBar(content: Text('Camera and notification permission are required.')),
            );
          }
          return;
        }
        final status = await _bridge.startServer(_config);
        setState(() => _status = status);
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('OnvifCam'),
        actions: [
          IconButton(
            icon: const Icon(Icons.wifi_tethering),
            tooltip: 'Static IP instructions',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => const StaticIpHelpScreen()),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.article_outlined),
            tooltip: 'Logs',
            onPressed: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => LogsScreen(entries: _logs)),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.settings),
            tooltip: 'Settings',
            onPressed: () async {
              final updated = await Navigator.of(context).push<ServerConfig>(
                MaterialPageRoute(builder: (_) => SettingsScreen(config: _config)),
              );
              if (updated != null) {
                setState(() => _config = updated);
                await _prefs.save(updated);
              }
            },
          ),
        ],
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            StatusCard(status: _status),
            const SizedBox(height: 24),
            FilledButton.icon(
              onPressed: _busy ? null : _toggle,
              icon: Icon(_status.running ? Icons.stop : Icons.play_arrow),
              label: Text(_status.running ? 'Stop server' : 'Start server'),
            ),
          ],
        ),
      ),
    );
  }
}
