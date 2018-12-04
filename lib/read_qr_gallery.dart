import 'dart:async';

import 'package:flutter/services.dart';

enum ImageSource {
  gallery
}

class ReadQrGallery {
  static const MethodChannel _channel =
      const MethodChannel('read_qr_gallery');

  static Future<dynamic> pickImage(){
    return _channel.invokeMethod('pickImage');
  }
}
