package com.exameple.readqrgallery;

import android.os.Environment;
import android.support.annotation.VisibleForTesting;

import java.io.File;

import android.content.Intent;
import android.os.Environment;
import android.support.annotation.VisibleForTesting;
import android.util.Log;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;
import java.io.File;

import javax.xml.transform.Result;
/** ReadQrGalleryPlugin */
public class ReadQrGalleryPlugin implements MethodChannel.MethodCallHandler {
  /** Plugin registration. */

  private static final String CHANNEL = "read_qr_gallery";
  private final PluginRegistry.Registrar registrar;
  private final ImagePickerDelegate delegate;

  public static void registerWith(PluginRegistry.Registrar registrar) {

    final MethodChannel channel = new MethodChannel(registrar.messenger(), CHANNEL);

    final ImagePickerDelegate delegate = new ImagePickerDelegate(registrar.activity());


    registrar.addActivityResultListener(delegate);
    registrar.addRequestPermissionsResultListener(delegate);

    final ReadQrGalleryPlugin instance = new ReadQrGalleryPlugin (registrar, delegate);


    channel.setMethodCallHandler(instance);
  }


  @VisibleForTesting
  ReadQrGalleryPlugin(PluginRegistry.Registrar registrar, ImagePickerDelegate delegate) {
    this.registrar = registrar;
    this.delegate = delegate;
  }

  @Override
  public void onMethodCall(MethodCall call, MethodChannel.Result result) {
    if (registrar.activity() == null) {
      result.error("no_activity", "image_picker requiere una actividad en primer plano.", null);
      return;
    }
    if (call.method.equals("pickImage")) {
      delegate.chooseImageFromGallery(call, result);
      //delegate.qr();
    } else {
      throw new IllegalArgumentException("Unknown method " + call.method);
    }
  }
}
