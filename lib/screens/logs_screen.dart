import 'package:flutter/material.dart';

import '../models/log_entry.dart';
import '../widgets/log_console.dart';

class LogsScreen extends StatelessWidget {
  final List<LogEntry> entries;

  const LogsScreen({super.key, required this.entries});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Logs')),
      body: LogConsole(entries: entries),
    );
  }
}
