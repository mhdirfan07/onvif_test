class LogEntry {
  final String level;
  final String message;
  final DateTime timestamp;

  LogEntry({required this.level, required this.message, required this.timestamp});

  factory LogEntry.fromMap(Map<dynamic, dynamic> map) => LogEntry(
        level: map['level'] as String? ?? 'INFO',
        message: map['message'] as String? ?? '',
        timestamp: DateTime.fromMillisecondsSinceEpoch(
          (map['ts'] as num?)?.toInt() ?? DateTime.now().millisecondsSinceEpoch,
        ),
      );
}
