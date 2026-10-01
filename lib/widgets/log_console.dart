import 'package:flutter/material.dart';

import '../models/log_entry.dart';

class LogConsole extends StatelessWidget {
  final List<LogEntry> entries;

  const LogConsole({super.key, required this.entries});

  Color _colorFor(String level) {
    switch (level) {
      case 'ERROR':
        return Colors.redAccent;
      case 'WARN':
        return Colors.orangeAccent;
      default:
        return Colors.lightGreenAccent;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Container(
      color: Colors.black,
      padding: const EdgeInsets.all(8),
      child: ListView.builder(
        reverse: true,
        itemCount: entries.length,
        itemBuilder: (context, index) {
          final entry = entries[entries.length - 1 - index];
          final time = entry.timestamp.toIso8601String().substring(11, 19);
          return Text(
            '$time [${entry.level}] ${entry.message}',
            style: TextStyle(
              color: _colorFor(entry.level),
              fontFamily: 'monospace',
              fontSize: 12,
            ),
          );
        },
      ),
    );
  }
}
