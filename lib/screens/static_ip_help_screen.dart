import 'package:flutter/material.dart';

class StaticIpHelpScreen extends StatelessWidget {
  const StaticIpHelpScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Static IP address')),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: const [
          Text(
            'A VMS expects this camera at a stable address. The app cannot change '
            'system network settings by itself, so set a static IP using one of '
            'these methods:',
            style: TextStyle(fontSize: 16),
          ),
          SizedBox(height: 16),
          _Step(
            title: 'Option A — Static IP on the phone',
            body:
                'Settings > Network & internet > Wi-Fi > (long-press your network) > '
                'Modify network > Advanced options > IP settings > Static. Fill in '
                'IP address, gateway, network prefix length and DNS to match your '
                'router\'s subnet.',
          ),
          _Step(
            title: 'Option B — DHCP reservation on the router (recommended)',
            body:
                'Open the router admin page, find this phone\'s MAC address under '
                'connected devices, and reserve a fixed IP for it. The phone keeps '
                'using DHCP, so it always receives the same address without extra '
                'per-device configuration.',
          ),
          SizedBox(height: 8),
          Text(
            'Whichever method you use, add that IP to the VMS manually if '
            'automatic discovery does not find the camera.',
            style: TextStyle(color: Colors.grey),
          ),
        ],
      ),
    );
  }
}

class _Step extends StatelessWidget {
  final String title;
  final String body;

  const _Step({required this.title, required this.body});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleMedium),
          const SizedBox(height: 4),
          Text(body),
        ],
      ),
    );
  }
}
