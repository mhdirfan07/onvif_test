import 'package:flutter/material.dart';

import '../models/camera_status.dart';

class StatusCard extends StatelessWidget {
  final CameraStatus status;

  const StatusCard({super.key, required this.status});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(
                  status.running ? Icons.videocam : Icons.videocam_off,
                  color: status.running ? Colors.green : theme.disabledColor,
                ),
                const SizedBox(width: 8),
                Text(
                  status.running ? 'Server running' : 'Server stopped',
                  style: theme.textTheme.titleMedium,
                ),
              ],
            ),
            const SizedBox(height: 12),
            _row('IP address', status.ip ?? '—'),
            _row('RTSP URL', status.rtspUrl ?? '—'),
            _row('HTTP port (ONVIF)', status.httpPort > 0 ? '${status.httpPort}' : '—'),
            _row('RTSP clients', '${status.rtspClients}'),
            if (status.lastError != null) ...[
              const SizedBox(height: 8),
              Text(
                status.lastError!,
                style: TextStyle(color: theme.colorScheme.error),
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _row(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: const TextStyle(color: Colors.grey)),
          SelectableText(value),
        ],
      ),
    );
  }
}
