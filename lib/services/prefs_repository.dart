import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../models/server_config.dart';

class PrefsRepository {
  static const _key = 'server_config';

  Future<ServerConfig> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw == null) return const ServerConfig();
    try {
      return ServerConfig.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      return const ServerConfig();
    }
  }

  Future<void> save(ServerConfig config) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(_key, jsonEncode(config.toJson()));
  }
}
