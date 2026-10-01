import 'package:flutter/material.dart';

import 'screens/home_screen.dart';

class OnvifCamApp extends StatelessWidget {
  const OnvifCamApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'OnvifCam',
      theme: ThemeData(colorSchemeSeed: Colors.teal, useMaterial3: true),
      darkTheme: ThemeData(
        colorSchemeSeed: Colors.teal,
        brightness: Brightness.dark,
        useMaterial3: true,
      ),
      home: const HomeScreen(),
    );
  }
}
