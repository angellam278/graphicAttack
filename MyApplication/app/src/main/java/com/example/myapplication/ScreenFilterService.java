package com.example.myapplication;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.Surface;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;
import static java.lang.Math.max;
import static java.lang.Math.min;


public class ScreenFilterService extends Service {

    private static final String TAG = "ScreenFilterService";

    public static int STATE_ACTIVE = 0;
    public static int STATE_INACTIVE = 1;

    public static int STATE;
    static {
        STATE = STATE_INACTIVE;
    }

    private int mDisplayWidth;
    private int mDisplayHeight;
    private int mScreenDensity;

    private Surface mSurface;
    private MediaProjection mMediaProjection;
    private ImageReader mImgReader;
    private MediaProjectionManager mMediaProjectionManager;
    private static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA = "EXTRA_DATA";

    // app's toast
    private Toast appToast;

    // for comparison
    // flag to check if its the first to check
    private boolean isFirst = true;
    // list of color ints for previous frame
    private double[] prevBrightnessList;
    // list of color ints for current frame
    private double[] _currentBrightnessList;

    private double localBrightnessChange = 0.0;
    private double localExtreme = 0.0;
    private int frameCount = 0;
    private int flashFrameCount = 0; // frame count when latest flash was detected
    private int extremesCount = 0;
    private int highAreaCount = 0;
    private int changeCount = 0; // if 2 means flash

    // flash elapse time, store start of the flash
    private long flashElapseStart = 0;

    private double _prevFrameAverageBrightness;

    //MediaProjection should only be sending frames when something on the screen changes.

    public ScreenFilterService() {
    }

    // to avoid display overlapping toasts
    public void showAToast (String st){ //"Toast toast" is declared in the class
        // TOAST appears on the bottom (newer OS doesn't allow repositioning of toasts)
//        try{ appToast.getView().isShown();     // true if visible
//            appToast.setText(st);
//        } catch (Exception e) {         // invisible if exception
//            appToast = Toast.makeText(getApplicationContext(), st, Toast.LENGTH_SHORT);
//        } finally {
//            appToast = Toast.makeText(getApplicationContext(), st, Toast.LENGTH_SHORT);
//        }
//        appToast.show();  //finally display it

        // heads up notification (like ones when you get calls)
        // vibration, sound, visibility has to be set per device in settings > app notifications
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "notification_id")
                .setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
                .setSmallIcon(R.mipmap.ic_launcher)
                //.setContentTitle("Incoming call")
                .setContentText(st)
                .setPriority(Notification.PRIORITY_HIGH);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(110, builder.build());

    }

    // needed to implement, for when others want to bind to this service
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * start screen recording
     * @param intent
     */
    private void startScreenFilter(final Intent intent) {
        Log.d(TAG, "startScreenFilter" );
        final int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        // get MediaProjection
        mMediaProjection = mMediaProjectionManager.getMediaProjection(resultCode, intent.getParcelableExtra(EXTRA_DATA));
        if (mMediaProjection != null) {
            final DisplayMetrics metrics = getResources().getDisplayMetrics();
            mDisplayWidth = metrics.widthPixels;
            mDisplayHeight = metrics.heightPixels;
            mScreenDensity = metrics.densityDpi;

            Display display = ((WindowManager) getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
            float refreshRating = display.getRefreshRate();
            System.out.println("refreshRating: " + refreshRating);
            // if 1 hz is 1 fps, can we count frames as hz? so if 3 frames in between is 3hz?

            // initializing imageReader to read from Media Projection in image format -> will give us access to image buffer
            // suggest ImageFormat.YUV_420_888 for faster
            //https://stackoverflow.com/questions/25462277/camera-preview-image-data-processing-with-android-l-and-camera2-api
            //ImageFormat.YUV_420_888 - not all devices support this, but apparently is more efficient
            // maximage = lowest as possible, number acquired before need to release
            mImgReader = ImageReader.newInstance(mDisplayWidth, mDisplayHeight, PixelFormat.RGBA_8888, 1);
            // https://chromium.googlesource.com/chromium/src/+/2f731e17983201082d9fc725cf7717868fc1e75d/media/capture/content/android/java/src/org/chromium/media/ScreenCapture.java
            // seems like not all devices support YUV_420_888
            //mImgReader = ImageReader.newInstance(mDisplayWidth, mDisplayHeight, ImageFormat.YUV_420_888, 5);
            mSurface = mImgReader.getSurface();
            // only affect display rate, this is not displayed...
            //mSurface.setFrameRate(60.0f, FRAME_RATE_COMPATIBILITY_DEFAULT);
            mMediaProjection.createVirtualDisplay(
                    "CAPTURE_THREAD_NAME", mDisplayWidth, mDisplayHeight, mScreenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, mSurface, null,
                    null
            );

            // wait for new images to be ready
            mImgReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    // to find hz
                    // /1000 to convert milliseconds to seconds, rn in seconds
                    frameCount ++;

                    // https://stackoverflow.com/questions/27581750/android-capture-screen-to-surface-of-imagereader
                    Image img = null;

                    try {
                        // gets the next image to be rendered
                        img = reader.acquireNextImage();
                        if (img != null) {

                            Image.Plane[] planes = img.getPlanes();
                            if (planes[0].getBuffer() == null) {
                                return;
                            }

                            // The distance between adjacent pixel samples, in bytes.
                            int pixelStride = planes[0].getPixelStride();
                            // This is the distance between the start of two consecutive rows of pixels in the image.
                            int rowStride = planes[0].getRowStride();
                            // CAN BE STORED
                            int rowPadding = rowStride - pixelStride * mDisplayWidth;
                            int offset = 0;

                            ByteBuffer buffer = planes[0].getBuffer();

                            // create buffer to store pixel brightness because we only need brightness for comparison
                            _currentBrightnessList = new double[mDisplayHeight*mDisplayWidth];

                            for (int i = 0; i < mDisplayHeight; ++i) {
                                for (int j = 0; j < mDisplayWidth; ++j) {
                                    // RGBA_8888: Each pixel is 4 bytes: 8 red bits, 8 green bits, 8 blue bits, and 8 alpha
                                    // 1 byte = 8 bits = 00000000~11111111 (binary)
                                    // https://stackoverflow.com/questions/11380062/what-does-value-0xff-do-in-java
                                    // << shifts bits to the left
                                    // color: https://developer.android.com/reference/android/graphics/Color
                                    // a,r,g,b
                                    int colorInt = (buffer.get(offset + 3) & 0xff) << 24 | (buffer.get(offset) & 0xff) << 16 | (buffer.get(offset + 1) & 0xff) << 8 | (buffer.get(offset + 2)  & 0xff);

                                    int idx = (i*mDisplayWidth) + j;

                                    // compute brightness
                                    int r = Color.red(colorInt) ;
                                    int g = Color.green(colorInt) ;
                                    int b = Color.blue(colorInt) ;
                                    // intensity levels of frame A and frame B
                                    double pixel_luminance = luminance(r, g, b);
                                    double pixel_brightness = toBrightness(pixel_luminance);
                                    // store color values for comparison between frames
                                    _currentBrightnessList[idx] = pixel_brightness;

                                    offset += pixelStride;
                                }
                                offset += rowPadding;
                            }

                            if (isFirst == false){
                                // compare with previous image
                                compareBuffers(buffer);
                            }
                            // make current a previous frame
                            prevBrightnessList = _currentBrightnessList;
                            // close image (max access = 1)
                            img.close();

                            isFirst = false;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }, null);


        }
    }



    @Override
    public void onCreate() {
        super.onCreate();
        // can set state active and inactive in here
        STATE = STATE_ACTIVE;
        createNotificationChannel();
        mMediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        STATE = STATE_INACTIVE;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showAToast("There is a Service running in Background");
        startScreenFilter(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    //https://stackoverflow.com/questions/64591594/android-10-androidforegroundservicetype-mediaprojection-not-working-with-ser/68343645#68343645
    private void createNotificationChannel() {

        Intent fullScreenIntent = new Intent(this, MainActivity.class);
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(this, 0,
                fullScreenIntent, PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "notification_id")
        .setContentIntent(PendingIntent.getActivity(this, 0, fullScreenIntent, PendingIntent.FLAG_IMMUTABLE))
                .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle("")
                        .setContentText("")
                        .setPriority(Notification.PRIORITY_HIGH)
                        .setCategory(Notification.CATEGORY_CALL)

                        // Use a full-screen intent only for the highest-priority alerts where you
                        // have an associated activity that you would like to launch after the user
                        // interacts with the notification. Also, if your app targets Android 10
                        // or higher, you need to request the USE_FULL_SCREEN_INTENT permission in
                        // order for the platform to invoke this notification.
                        .setFullScreenIntent(fullScreenPendingIntent, true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setChannelId("notification_id");
        }
        //前台服务notification适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
            notificationManager.createNotificationChannel(channel);
        }

        Notification incomingCallNotification = builder.build();
        startForeground(110, incomingCallNotification);

//        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器
//        Intent nfIntent = new Intent(this, MainActivity.class); //点击后跳转的界面，可以设置跳转数据
//
//        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, PendingIntent.FLAG_IMMUTABLE)) // 设置PendingIntent
//                .setLargeIcon(BitmapFactory.decodeResource(this.getResources(), R.mipmap.ic_launcher)) // 设置下拉列表中的图标(大图标)
//                //.setContentTitle("SMI InstantView") // 设置下拉列表里的标题
//                .setSmallIcon(R.mipmap.ic_launcher) // 设置状态栏内的小图标
//                .setContentText("is running......") // 设置上下文内容
//                .setWhen(System.currentTimeMillis()); // 设置该通知发生的时间
//
//        /*以下是对Android 8.0的适配*/
//        //普通notification适配
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            builder.setChannelId("notification_id");
//        }
//        //前台服务notification适配
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_LOW);
//            notificationManager.createNotificationChannel(channel);
//        }
//
//        Notification notification = builder.build(); // 获取构建好的Notification
//        notification.defaults = Notification.DEFAULT_SOUND; //设置为默认的声音
//        startForeground(110, notification);
    }

    /**
     * Obtaining the intensity levels of a given pixel
     * @param red value of red of the pixel
     * @param green value of green of the pixel
     * @param blue value of blue of the pixel
     * @return intensity level (luminance) range of 0.0 to 255.0
     */
    private static double luminance(float red, float green, float blue) {
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
     * @param luminance luminance value mV
     * @return brightness level cd/m^2
     */
    private static double toBrightness(double luminance) {
        return 413.435 * Math.pow(0.002745 * luminance + 0.0189623, 2.2);
    }

    /**
     * Evaluating the Hertz value of the given GIF
     * @param duration duration of GIF (in milliseconds)
     * @return if the Hertz value is dangerous, return true, else false.
     */
    private static boolean isHzDangerous(double duration) {
        double hz = 1.0 / (duration / 1000.0);
        System.out.println("Hertz: " + hz);
        if (hz >= 3 && hz <= 30) {
            return true;
        }
        return false;
    }

    private static boolean isFrameDangerous(int framesElapsed) {
        // 1 fps = 1hz
        // dangerous is 2 (from paper) - 3 (from prev implementation) hz
        if (framesElapsed < 4) {
            return true;
        }
        return false;
    }

    private void compareBuffers(ByteBuffer imgA) {
        // TODO find a way to skip to current if many are lagging behind?

        int riskCount = 0;

        double flashingPixelCount = 0.0;

        // can prob store the prev brightness total/average??
        double prevTotalBrightness = 0.0;
        double currentTotalBrightness = 0.0;

        // iterates through pixels to find percentage of flashing pixels and calculate average frame brightness
        for (int i = 0; i < mDisplayHeight; ++i) {
            for (int j = 0; j < mDisplayWidth; ++j) {
                //iterating by row
                int idx = (i*mDisplayWidth) + j;

                double prevPixelBrightness = prevBrightnessList[idx];
                double currentPixelBrightness = _currentBrightnessList[idx];

                // to compute average brightness for current frame
                // previous frame's average brightness is already computed and stored
                currentTotalBrightness += _currentBrightnessList[idx];

                if (Math.abs(prevPixelBrightness - currentPixelBrightness) > 20) {
                    // flashing pixel
                    flashingPixelCount ++;
                }
            }
        }

        int totalPixels = mDisplayHeight* mDisplayWidth;
        double percent_flash_pixel = flashingPixelCount / totalPixels;

        // average brightness per frame DO WE NEED * 3?
        double currentFrameAverageBrightness =  currentTotalBrightness / (totalPixels );//rgba
        double prevFrameAverageBrightness = _prevFrameAverageBrightness;

        // intensity difference
        // positive means increase in brightness
        double brightnessChange = currentFrameAverageBrightness - prevFrameAverageBrightness;

        // store for next comparison
        _prevFrameAverageBrightness = currentFrameAverageBrightness;

        // TODO: still not considering special patterns and red transitions

        // if there is a change in brightness
        if (brightnessChange != 0.0) {

            // accumulate lowering of intensity or increasing of intensity
            if ((brightnessChange < 0 && localBrightnessChange <=0) || (brightnessChange > 0 && localBrightnessChange >=0)) {
                // accumulate brightness in the same direction (increasing or decreasing)
                // negative means darkening, positive means brightening
                localBrightnessChange += brightnessChange;

            } else {
                // localBrightnessChange sign changes (darkening to brightening or vice versa) - peak

                // flash (determined by hz and change in colors) - interval between two peaks
                if (isFrameDangerous((frameCount - flashFrameCount))) {
                    // one peak of flash
                    changeCount ++;

                    if (changeCount >= 2) {
                        // pair of change = flash
                        // System.out.println("Flashing detected.");
                        // TODO should we mark as risky when flashes?

                        // store time that flash starts
                        long cur = System.currentTimeMillis();

                        // high area flash
                        // frame count will always be > 0
                        // 1) combined area of flashes occurring concurrently occupies more than 25% of the displayed screen area;
                        // 2) the  flash  frequency  is  higher  than 3 Hz
                        if (percent_flash_pixel > 0.25) {
                            // need at least a pair so 2 counts
                            System.out.println("High area of fast flashing.");
                            riskCount++;
                        }

                        // series of flashing images (flashing/ intensity going up and down) for longer than 5 seconds can be risky
                        if ((flashElapseStart!=0) && ((cur - flashElapseStart / 1000) > 5)){
                            // even if the difference is not high but this will also include small flickers so
                            // need to have a big area of flash but not as high as the other?
                            if (percent_flash_pixel >= 0.08) {
                                System.out.println("Flashing longer than 5 seconds detected.");
                                // reset to not have repeated alerts
                                flashElapseStart = 0;
                                riskCount++;
                            }
                        }
                        if (flashElapseStart == 0) {
                            // start timer for a flash
                            flashElapseStart = cur;
                        }

                        // high contrast flash
                        // if the brightness change is more than 20 cd/m2 and the darker image's brightness is lower than 160 cd/m2 it is risky
                        if (Math.abs(localExtreme - localBrightnessChange) > 20) {
                            if (Math.min(localExtreme, localBrightnessChange) < 160) {
                                // paper says 4 flashes is dangerous do we need that ?
                                //extremesCount ++;
                                //if (extremesCount >= 4) {
                                    System.out.println("Flash between a dark image is detected.");
                                    riskCount++;
                                    //extremesCount = 0;
                                //}
                            }
                        }
                    }
                } else {
                    // not flash (slow change in brightness)
                    changeCount = 0;
                    flashElapseStart = 0; // not continuous flashing
                }

                // store frameCount for latest detected flash
                flashFrameCount = frameCount;
                // store local extreme
                localExtreme = localBrightnessChange;
                // reset
                localBrightnessChange = 0;
            }

        } else {
            // reset
            flashElapseStart = 0;
        }

        // Evaluating the results
        if (riskCount == 0) {
            System.out.println("This GIF is safe to watch");
        } else  if (riskCount == 1) {
            System.out.println("This GIF is risky");
            showAToast("This is risky!");
        } else if(riskCount == 2) {
            System.out.println("This GIF is dangerous");
            showAToast("Danger detected!");
        } else if (riskCount == 3) {
            System.out.println("This GIF is extreme");
            showAToast("Extreme Danger detected!");
        } else {
            System.out.println("This GIF is beyond extreme");
            showAToast("Extreme Danger detected!");
        }

        // NOTE: toast may obscure gif area and make it detect as safe

    }
}