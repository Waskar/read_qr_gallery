#import "ReadQrGalleryPlugin.h"
#import <read_qr_gallery/read_qr_gallery-Swift.h>

@implementation ReadQrGalleryPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftReadQrGalleryPlugin registerWithRegistrar:registrar];
}
@end
