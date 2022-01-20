package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MyApplication main";

    // permission requests
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSION_STORAGE = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    private static final int REQUEST_CODE_MEDIA_PROJECTION = 1;

    // display projection
    private Surface mSurface;
    private MediaProjection mMediaProjection;
    private ImageReader mImgReader;
    private MediaProjectionManager mMediaProjectionManager;
    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mScreenDensity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // verifying permission to write/read files from file system
        verifyStoragePermission(this);

        // https://stackoverflow.com/questions/57026489/mediaprojection-service-type-not-recognized-in-android-q/57186064#57186064
        // starting service in the background
        // if no service will have this error: java.lang.SecurityException: Media projections require a foreground service of type ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        Intent i =new Intent(MainActivity.this, ScreenFilterService.class);
        startService(i);

        // get screen dimensions
        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(displayMetrics);
        mDisplayHeight = displayMetrics.heightPixels;
        mDisplayWidth = displayMetrics.widthPixels;
        mScreenDensity = displayMetrics.densityDpi;
        // report screen dimensions
        Log.d(TAG, "mDisplayWidth: " + Integer.toString(mDisplayWidth));
        Log.d(TAG, "mDisplayHeight: " + Integer.toString(mDisplayHeight));

        // TODO: put this in service?
        // popup to ask user for permission to record screen, this is required to start recording
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        // startActivityForResult is deprecated
        startActivityForResult(mMediaProjectionManager.createScreenCaptureIntent(), REQUEST_CODE_MEDIA_PROJECTION);
//        ActivityResultLauncher<Intent> mainActivityResultLauncher = registerForActivityResult(
//                new ActivityResultContracts.StartActivityForResult(),
//                new ActivityResultCallback<ActivityResult>() {
//                    @Override
//                    public void onActivityResult(ActivityResult result) {
//                        if (result.getResultCode() == Activity.RESULT_OK) {
//                            // There are no request codes
//                            Intent data = result.getData();
//                            doSomeOperations();
//                        }
//                    }
//                });
//
//        public void openSomeActivityForResult() {
//            Intent intent = new Intent(this, SomeActivity.class);
//            someActivityResultLauncher.launch(intent);
//        }


        // initializing imageReader to read from Media Projection in image format -> will give us access to image buffer
        mImgReader = ImageReader.newInstance(mDisplayWidth, mDisplayHeight, PixelFormat.RGBA_8888, 5);
        mSurface = mImgReader.getSurface();
        mImgReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Log.i(TAG, "OnImageAvailable");

                // https://stackoverflow.com/questions/27581750/android-capture-screen-to-surface-of-imagereader
                FileOutputStream fos = null;
                Bitmap bitmap = null;
                Image img = null;
                try {
                    img = reader.acquireLatestImage();
                    if (img != null) {
                        Image.Plane[] planes = img.getPlanes();
                        if (planes[0].getBuffer() == null) {
                            return;
                        }
                        int mDisplayWidth = img.getWidth();
                        int height = img.getHeight();
                        int pixelStride = planes[0].getPixelStride();
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * mDisplayWidth;

                        int offset = 0;
                        bitmap = Bitmap.createBitmap(mDisplayWidth, height, Bitmap.Config.ARGB_8888);
                        // copyPixelsFromBuffer crashed and needed to use this process
                        ByteBuffer buffer = planes[0].getBuffer();
                        for (int i = 0; i < height; ++i) {
                            for (int j = 0; j < mDisplayWidth; ++j) {
                                int pixel = 0;
                                pixel |= (buffer.get(offset) & 0xff) << 16;     // R
                                pixel |= (buffer.get(offset + 1) & 0xff) << 8;  // G
                                pixel |= (buffer.get(offset + 2) & 0xff);       // B
                                pixel |= (buffer.get(offset + 3) & 0xff) << 24; // A
                                bitmap.setPixel(j, i, pixel);
                                offset += pixelStride;
                            }
                            offset += rowPadding;
                        }

                        // because getExternalDir() gives permission error
                        // to access the file: go to the other SDCard### directory
                        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
                        // NOTE: currently writing to same file, just to see if its reading new frames
                        File file = new File(contextWrapper.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "/name.jpeg");
                        Log.i(TAG, "image path" + contextWrapper.getExternalFilesDir(Environment.DIRECTORY_DCIM) +  "/name.jpeg");
                        fos = new FileOutputStream(file);
                        if (file == null) {
                            Log.i(TAG, "FILE IS NULL");
                        }else {
                            Log.i(TAG, "FILE IS NOT NULL");
                        }
                        if (fos == null) {
                            Log.i(TAG, "FOS IS NULL");
                        } else {
                            Log.i(TAG,"FOS IS NOT NULL");
                        }

                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                        Log.i(TAG, "image saved in" + contextWrapper.getExternalFilesDir(Environment.DIRECTORY_DCIM) +  "name.jpeg");
                        img.close();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (null != fos) {
                        try {
                            fos.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    if (null != bitmap) {
                        bitmap.recycle();
                    }
                    if (null != img) {
                        img.close();
                    }

                }
            }
        }, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_CODE_MEDIA_PROJECTION) {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(this,
                    "User denied screen sharing permission", Toast.LENGTH_SHORT).show();
            return;
        }

        //mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, data);
        mMediaProjection.createVirtualDisplay(
                "CAPTURE_THREAD_NAME", mDisplayWidth, mDisplayHeight, mScreenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mSurface, null,
                null
        );
    }


    public static void verifyStoragePermission(Activity activity) {
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, PERMISSION_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }
    }

}



