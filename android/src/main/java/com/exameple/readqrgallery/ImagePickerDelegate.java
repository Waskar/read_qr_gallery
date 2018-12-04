package com.exameple.readqrgallery;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.barcode.Barcode;
import com.google.android.gms.vision.barcode.BarcodeDetector;

import java.io.File;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.PluginRegistry;

public class ImagePickerDelegate implements PluginRegistry.ActivityResultListener, PluginRegistry.RequestPermissionsResultListener  {
    @VisibleForTesting static final int REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY = 2342;
    @VisibleForTesting static final int REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION = 2344;
    @VisibleForTesting static final int REQUEST_CODE_CHOOSE_VIDEO_FROM_GALLERY = 2352;
    @VisibleForTesting static final int REQUEST_EXTERNAL_VIDEO_STORAGE_PERMISSION = 2354;

    @VisibleForTesting final String fileProviderName;

    private final Activity activity;
    private final File externalFilesDirectory;
    private final ImageResizer imageResizer;
    private final PermissionManager permissionManager;
    private final IntentResolver intentResolver;
    private final FileUriResolver fileUriResolver;
    private final FileUtils fileUtils;

    BarcodeDetector detector;

    interface PermissionManager {
        boolean isPermissionGranted(String permissionName);

        void askForPermission(String permissionName, int requestCode);
    }

    interface IntentResolver {
        boolean resolveActivity(Intent intent);
    }

    interface FileUriResolver {
        Uri resolveFileProviderUriForFile(String fileProviderName, File imageFile);

        void getFullImagePath(Uri imageUri, OnPathReadyListener listener);
    }

    interface OnPathReadyListener {
        void onPathReady(String path);
    }

    private Uri pendingCameraMediaUri;
    private MethodChannel.Result pendingResult;
    private MethodCall methodCall;

    public ImagePickerDelegate(
            final Activity activity, File externalFilesDirectory, ImageResizer imageResizer) {
        this(
                activity,
                externalFilesDirectory,
                imageResizer,
                null,
                null,
                new PermissionManager() {
                    @Override
                    public boolean isPermissionGranted(String permissionName) {
                        return ActivityCompat.checkSelfPermission(activity, permissionName)
                                == PackageManager.PERMISSION_GRANTED;
                    }

                    @Override
                    public void askForPermission(String permissionName, int requestCode) {
                        ActivityCompat.requestPermissions(activity, new String[] {permissionName}, requestCode);
                    }
                },
                new IntentResolver() {
                    @Override
                    public boolean resolveActivity(Intent intent) {
                        return intent.resolveActivity(activity.getPackageManager()) != null;
                    }
                },
                new FileUriResolver() {
                    @Override
                    public Uri resolveFileProviderUriForFile(String fileProviderName, File file) {
                        return FileProvider.getUriForFile(activity, fileProviderName, file);
                    }

                    @Override
                    public void getFullImagePath(final Uri imageUri, final OnPathReadyListener listener) {
                        MediaScannerConnection.scanFile(
                                activity,
                                new String[] {imageUri.getPath()},
                                null,
                                new MediaScannerConnection.OnScanCompletedListener() {
                                    @Override
                                    public void onScanCompleted(String path, Uri uri) {
                                        listener.onPathReady(path);
                                    }
                                });
                    }
                },
                new FileUtils());
    }

    /**
     * This constructor is used exclusively for testing; it can be used to provide mocks to final
     * fields of this class. Otherwise those fields would have to be mutable and visible.
     */
    @VisibleForTesting
    ImagePickerDelegate(
            Activity activity,
            File externalFilesDirectory,
            ImageResizer imageResizer,
            MethodChannel.Result result,
            MethodCall methodCall,
            PermissionManager permissionManager,
            IntentResolver intentResolver,
            FileUriResolver fileUriResolver,
            FileUtils fileUtils) {
        this.activity = activity;
        this.externalFilesDirectory = externalFilesDirectory;
        this.imageResizer = imageResizer;
        this.fileProviderName = activity.getPackageName() + ".flutter.image_provider";
        this.pendingResult = result;
        this.methodCall = methodCall;
        this.permissionManager = permissionManager;
        this.intentResolver = intentResolver;
        this.fileUriResolver = fileUriResolver;
        this.fileUtils = fileUtils;
    }

    public void chooseImageFromGallery(MethodCall methodCall, MethodChannel.Result result) {
        if (!setPendingMethodCallAndResult(methodCall, result)) {
            finishWithAlreadyActiveError();
            return;
        }
        if (!permissionManager.isPermissionGranted(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            permissionManager.askForPermission(
                    Manifest.permission.READ_EXTERNAL_STORAGE, REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION);
            return;
        }
        launchPickImageFromGalleryIntent();
    }

    private void launchPickImageFromGalleryIntent() {
        Intent pickImageIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        pickImageIntent.setType("image/*");
        activity.startActivityForResult(pickImageIntent, REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY);
    }

    @Override
    public boolean onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        boolean permissionGranted =
                grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;

        switch (requestCode) {
            case REQUEST_EXTERNAL_IMAGE_STORAGE_PERMISSION:
                if (permissionGranted) {
                    launchPickImageFromGalleryIntent();
                }
                break;
            default:
                return false;
        }

        if (!permissionGranted) {
            finishWithSuccess(null);
        }

        return true;
    }

    @Override
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CODE_CHOOSE_IMAGE_FROM_GALLERY: handleChooseImageResult(resultCode, data);
                break;
            default:
                return false;
        }
        return true;
    }

    private void handleChooseImageResult(int resultCode, Intent data) {

        if (resultCode == Activity.RESULT_OK && data != null) {
            String path = fileUtils.getPathFromUri(activity, data.getData());
            qr(data);
            Log.i(getClass().getSimpleName(), "Este es el path ? : "+ path);
            return;
        }
        finishWithSuccess(null);
    }
    public void qr(Intent data){
        detector = new BarcodeDetector.Builder(activity.getApplicationContext())
                .setBarcodeFormats(Barcode.DATA_MATRIX | Barcode.QR_CODE)
                .build();

        Uri selectedImage = data.getData();
        Log.i(getClass().getSimpleName(), "Uri y aqui? : "+ selectedImage);
        String[] filePathColumn = {MediaStore.Images.Media.DATA};
        Log.i(getClass().getSimpleName(), "filePathColumn: "+ filePathColumn);
        Cursor cursor = activity.getApplicationContext().getContentResolver().query(selectedImage,filePathColumn,null,null,null);
        Log.i(getClass().getSimpleName(), "Cursor: "+ cursor);
        cursor.moveToFirst();
        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        Log.i(getClass().getSimpleName(), "columnIndex: "+ columnIndex);
        String picturePath = cursor.getString(columnIndex);
        Log.i(getClass().getSimpleName(), "picturePath: "+ picturePath);
        cursor.close();
        Bitmap bitmap = BitmapFactory.decodeFile(picturePath);
        Log.i(getClass().getSimpleName(), "bitmap: "+ bitmap);
        Frame frame = new Frame.Builder().setBitmap(bitmap).build();
        Log.i(getClass().getSimpleName(), "Frame: "+ frame);
        SparseArray<Barcode> barcodes = detector.detect(frame);
        Log.i(getClass().getSimpleName(), "barcodes: "+ barcodes);
        Barcode thisCode = barcodes.valueAt(0);
        Log.i(getClass().getSimpleName(), "thisCode: "+ thisCode);
        Log.i(getClass().getSimpleName(), "thisCode: "+ thisCode.rawValue);
        finishWithSuccess(thisCode.rawValue);

    }

    private boolean setPendingMethodCallAndResult(MethodCall methodCall, MethodChannel.Result result) {
        if (pendingResult != null) {
            return false;
        }
        this.methodCall = methodCall;
        pendingResult = result;
        return true;
    }

    private void finishWithSuccess(String thisCode) {
        Log.i(getClass().getSimpleName(), "Y esto : "+ thisCode);
        pendingResult.success(thisCode);
        Log.i(getClass().getSimpleName(), "este es el image path : "+ thisCode);
        clearMethodCallAndResult();
    }

    private void finishWithAlreadyActiveError() {
        finishWithError("already_active", "Image picker is already active");
    }

    private void finishWithError(String errorCode, String errorMessage) {
        pendingResult.error(errorCode, errorMessage, null);
        clearMethodCallAndResult();
    }

    private void clearMethodCallAndResult() {
        methodCall = null;
        pendingResult = null;
    }
}
