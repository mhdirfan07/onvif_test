class CameraOption {
  final String id;
  final String type;
  final String facing;
  final String? nativeLabel;

  const CameraOption({
    required this.id,
    required this.type,
    required this.facing,
    this.nativeLabel,
  });

  String get label {
    if (nativeLabel != null && nativeLabel!.isNotEmpty) return nativeLabel!;
    switch (facing) {
      case 'front':
        return 'Front camera';
      case 'back':
        return 'Back camera';
      case 'external':
        return 'External camera';
      default:
        return 'Camera $id';
    }
  }

  factory CameraOption.fromMap(Map<dynamic, dynamic> map) => CameraOption(
        id: map['id'] as String,
        type: map['type'] as String? ?? 'camera2',
        facing: map['facing'] as String? ?? 'unknown',
        nativeLabel: map['label'] as String?,
      );
}
