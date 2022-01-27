package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
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

    private boolean prevStored = false;
    private long start = 0;
    private long elapse = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // verifying permission to write/read files from file system
        verifyStoragePermission(this);

        // https://stackoverflow.com/questions/57026489/mediaprojection-service-type-not-recognized-in-android-q/57186064#57186064
        // starting service in the background with a notification channel
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

        // test comparing saved images
//        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());
//        compare(
//                BitmapFactory.decodeFile(contextWrapper.getExternalFilesDir(Environment.DIRECTORY_PICTURES) +  "/name1.jpeg"),
//                BitmapFactory.decodeFile(contextWrapper.getExternalFilesDir(Environment.DIRECTORY_PICTURES) +  "/name2.jpeg")
//        );

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

                // TODO find hz
                long current = System.currentTimeMillis();
                elapse = current - start;
                start = current;
                Log.d(TAG, "elapse: " + elapse);

                // https://stackoverflow.com/questions/27581750/android-capture-screen-to-surface-of-imagereader
                FileOutputStream fos = null;
                Bitmap bitmap = null;
                Image img = null;
                // NOTE: for debugging -> saving out the images
                String imageName = "/name1.jpeg";
                if (prevStored == true){
                    // we are processing the second image, will need to compare
                    imageName = "/name2.jpeg";
                }
                try {
                    // gets current image
                    img = reader.acquireLatestImage();
                    // gets the next image to be rendered
                    // img = reader.acquireNextImage();
                    if (img != null) {
                        Image.Plane[] planes = img.getPlanes();
                        if (planes[0].getBuffer() == null) {
                            return;
                        }

                        // The distance between adjacent pixel samples, in bytes.
                        int pixelStride = planes[0].getPixelStride();
                        // This is the distance between the start of two consecutive rows of pixels in the image.
                        int rowStride = planes[0].getRowStride();
                        int rowPadding = rowStride - pixelStride * mDisplayWidth;

                        int offset = 0;
                        // this web suggests diff file format for faster speed (TODO) if this is too slow:
                        // https://stackoverflow.com/questions/29965725/imagereader-in-android-needs-too-long-time-for-one-frame-to-be-available
                        bitmap = Bitmap.createBitmap(mDisplayWidth, mDisplayHeight, Bitmap.Config.ARGB_8888);
                        // copyPixelsFromBuffer crashed and needed to use this process
                        // |= is +=
                        ByteBuffer buffer = planes[0].getBuffer();
                        for (int i = 0; i < mDisplayHeight; ++i) {
                            for (int j = 0; j < mDisplayWidth; ++j) {
                                int pixel = 0;
                                // RGBA_8888: Each pixel is 4 bytes: 8 red bits, 8 green bits, 8 blue bits, and 8 alpha
                                // 1 byte = 8 bits = 00000000~11111111 (binary)
                                // https://stackoverflow.com/questions/11380062/what-does-value-0xff-do-in-java
                                // << shifts bits to the left
                                // color: https://developer.android.com/reference/android/graphics/Color
                                // a,r,g,b
                                int colorInt = (buffer.get(offset + 3) & 0xff) << 24 | (buffer.get(offset) & 0xff) << 16 | (buffer.get(offset + 1) & 0xff) << 8 | (buffer.get(offset + 2)  & 0xff);
                                bitmap.setPixel(j, i, colorInt);
                                offset += pixelStride;
                            }
                            offset += rowPadding;
                        }

                        // because getExternalDir() gives permission error
                        // to access the file: go to the other SDCard### directory
                        ContextWrapper contextWrapper = new ContextWrapper(getApplicationContext());

                        // NOTE: currently writing to same file, just to see if its reading new frames
                        File file = new File(contextWrapper.getExternalFilesDir(Environment.DIRECTORY_PICTURES), imageName);
                        fos = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                        // always storing 2 consecutive images
                        if (prevStored == true){
                            // compare the new image, if yes -> alert!
                            compare(BitmapFactory.decodeFile(contextWrapper.getExternalFilesDir(Environment.DIRECTORY_PICTURES) +  "/name1.jpeg"), bitmap);
                            // restart loop
                            prevStored = false;
                        } else {
                            prevStored = true;
                        }

                        Log.i(TAG, "image saved in" + contextWrapper.getExternalFilesDir(Environment.DIRECTORY_PICTURES) +  "name1.jpeg");
                        // need to close image when we are done to avoid reaching max outstanding img count (IllegalStateException)
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

    /**
     * Obtaining the intensity levels of a given pixel
     * @param red value of red of the pixel
     * @param green value of green of the pixel
     * @param blue value of blue of the pixel
     * @return intensity level (luminance) range of 0.0 to 255.0
     */
    private static double intensity(float red, float green, float blue) {
        return 0.299 * red + 0.587 * green + 0.114 * blue;
    }

    /**
     * Finding oout whether the intensity levels of the two pixels are compatible.
     * @param intensityA intensity value of pixel A
     * @param intensityB intensity value of pixel B
     * @return true/false Two colors are compatible if the difference in their monochrome luminance is at least 128.0).
     */
    private static double COMPATIBLE = 128.0;
    private static boolean isCompatible(double intensityA, double intensityB) {
        double value = Math.abs(intensityA - intensityB);
        return value < COMPATIBLE;
    }

    /**
     * Obtaining the brightness level through luminance.
     * @param luminance luminance value
     * @return brightness level
     */
    private static double toBrightness(double luminance) {
        return 413.435 * Math.pow(0.002745 * luminance + 0.0189623, 2.2);
    }

    private void compare(Bitmap imgA, Bitmap imgB) {

        long difference = 0;
        double intensityA = 0.0;
        double intensityB = 0.0;
        double totalIntensityA = 0.0;
        double totalIntensityB = 0.0;
        double countDiff = 0.0;
        double difPercentage = 0.0;
        int totalCount = 0;
        double notCompatibleCount = 0.0;

        // using colorspace: sRGB IEC61966-2.1 range [0, 1]
        // Log.i(TAG, "imageA: " + imgA.getColorSpace());
        // Log.i(TAG, "imageb: " + imgB.getColorSpace());

        // Going through every pixel
        for (int y = 0; y < mDisplayHeight; y++) {
            for (int x = 0; x < mDisplayWidth; x++) {
                Color imgAColor = imgA.getColor(x, y);
                Color imgBColor = imgB.getColor(x, y);
                if (imgAColor.toArgb() != imgBColor.toArgb()) {
                    countDiff++;
                }

                // TODO COLOR HAS LUMINANCE FUNCTION
                // if use color.red(color int) will get [0, 255]
                float redA = imgAColor.red() * 255;
                float greenA = imgAColor.green() * 255;
                float blueA = imgAColor.blue() * 255;
                float redB = imgBColor.red() * 255;
                float greenB = imgBColor.green() * 255;
                float blueB = imgBColor.blue() * 255;
                //Log.i(TAG, "imageA: (" + Integer.toString(x) + ", " + Integer.toString(y) + ") " + redA + " " + greenA + " " + blueA + " " + imgAColor.alpha());
                //Log.i(TAG, "imageB: (" + Integer.toString(x) + ", " + Integer.toString(y) + ") " + redB + " " + greenB + " " + blueB + " " + imgBColor.alpha());
                difference += Math.abs(redA - redB);
                difference += Math.abs(greenA - greenB);
                difference += Math.abs(blueA - blueB);
                // intensity levels of frame A and frame B
                intensityA = intensity(redA, greenA, blueA);
                totalIntensityA += intensity(redA, greenA, blueA);
                intensityB = intensity(redB, greenB, blueB);
                totalIntensityB += intensity(redB, greenB, blueB);
                if (isCompatible(intensityA, intensityB)) {
                    notCompatibleCount++;
                }
            }
        }

        difPercentage = notCompatibleCount / countDiff * 100;

        // EVALUATING THE OUTPUT

        // Total number of red pixels = width * height
        // Total number of blue pixels = width * height
        // Total number of green pixels = width * height
        // So total number of pixels = width * height * 3
        double totalPixels = mDisplayHeight * mDisplayWidth * 3;
        System.out.println("The total amount of pixels are: " + totalPixels);
        double differentPixelPercentage = countDiff / totalPixels;
        double dangerPercent = differentPixelPercentage * difPercentage;
        System.out.println("The percentage of dangerous Pixels are: " + dangerPercent + "%");
        if (dangerPercent > 30) {
            System.out.println("The percentage of dangerous pixels are dangerous");
            totalCount++;
        }
        // to compensate for hzdangerous -> always dangerous for now (60hz is the max fps for android)
        totalCount++;
//        if (isHzDangerous(delayTime)) {
//            System.out.println("This GIF's Hz is in the range of being dangerous");
//            totalCount++;
//        } else {
//            System.out.println("This GIF's Hz is in the range of being safe");
//        }
        // Normalizing the value of different pixels for accuracy(average pixels per color component)
        double avg_different_pixels = difference / totalPixels;
        // There are 255 values of pixels in total
        double percentage = (avg_different_pixels / 255) * 100;
        System.out.println("Difference Percentage-->" + percentage);

        double avgIntensityA = totalIntensityA / totalPixels;
        double avgIntensityB = totalIntensityB / totalPixels;
        System.out.println("This is the average intensity of frame A: " + avgIntensityA);
        System.out.println("This is the average intensity of frame B: " + avgIntensityB);
        if (avgIntensityA >= avgIntensityB) {
            if (avgIntensityB / avgIntensityA <= .55) {
                System.out.println("Average intensity ratio between frame A and B " + avgIntensityB / avgIntensityA + " is dangerous");
                totalCount++;
            }
        } else {
            if (avgIntensityA / avgIntensityB <= .55) {
                System.out.println("Average intensity ratio between frame A and B " + avgIntensityA / avgIntensityB + " is dangerous");
                totalCount++;
            }
        }
        System.out.println("Average brightness of frame A: " + toBrightness(avgIntensityA));
        System.out.println("Average brightness of frame B: " + toBrightness(avgIntensityB));
        // reducing totalCount by 1 because ignoring duration for now
        if (totalCount == 1) {
            System.out.println("This GIF is risky");
        } else if(totalCount == 2) {
            System.out.println("This GIF is dangerous");
        } else if (totalCount == 3) {
            System.out.println("This GIF is extreme");
        } else {
            System.out.println("This GIF is safe to watch");
        }

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



