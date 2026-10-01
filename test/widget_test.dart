import 'package:flutter_test/flutter_test.dart';

import 'package:onvifcam/app.dart';

void main() {
  testWidgets('Home screen renders with start button', (WidgetTester tester) async {
    await tester.pumpWidget(const OnvifCamApp());
    await tester.pump();

    expect(find.text('OnvifCam'), findsOneWidget);
    expect(find.textContaining('Start server'), findsOneWidget);
  });
}
