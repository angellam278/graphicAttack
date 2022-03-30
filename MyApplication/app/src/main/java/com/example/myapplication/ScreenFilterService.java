package com.example.myapplication;


import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
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
import android.view.Gravity;
import android.view.Surface;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.nio.ByteBuffer;



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
    private long start = 0;
    private long elapse = 0;
    // flag to check if its the first to check
    private boolean isFirst = true;
    // list of color ints for previous frame
    private int[] prevColorList;
    // list of color ints for current frame
    private int[] currentColorList;

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
                    long current = System.currentTimeMillis();
                    elapse = current - start;
                    start = current;

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
                            currentColorList = new int[mDisplayHeight*mDisplayWidth];
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
                                    // store color values
                                    currentColorList[idx] = colorInt;
                                    offset += pixelStride;
                                }
                                offset += rowPadding;
                            }

                            if (isFirst == false){
                                // compare with previous image
                                compareBuffers(buffer);
                            }
                            // make current a previous frame
                            prevColorList=currentColorList;
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
                        .setContentTitle("Incoming call")
                        .setContentText("(919) 555-1234")
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

    /**
     * Evaluating the Hertz value of the given GIF
     * @param duration duration of GIF (in milliseconds)
     * @return if the Hertz value is dangerous, return true, else false.
     */
    private static boolean isHzDangerous(int duration) {
        double hz = 1.0 / (duration / 1000.0);
        System.out.println("Hertz: " + hz);
        if (hz >= 3 && hz <= 30) {
            return true;
        }
        return false;
    }

    private void compareBuffers(ByteBuffer imgA) {
        // TODO find a way to skip to current if many are lagging behind?

        long difference = 0;
        double intensityA = 0.0;
        double intensityB = 0.0;
        double totalIntensityA = 0.0;
        double totalIntensityB = 0.0;
        double countDiff = 0.0;
        double difPercentage = 0.0;
        int totalCount = 0;
        double notCompatibleCount = 0.0;

        for (int i = 0; i < mDisplayHeight; ++i) {
            for (int j = 0; j < mDisplayWidth; ++j) {
                //iterating by row
                int idx = (i*mDisplayWidth) + j;

                int curColor = currentColorList[idx];
                int RImageA = Color.red(curColor) * 255;
                int GImageA = Color.green(curColor) * 255;
                int BImageA = Color.blue(curColor) * 255;
                //Log.d(TAG, "COLOR A: " + RImageA + " " + GImageA + " " + BImageA);
                int prevColor = prevColorList[idx];
                int Rprev = Color.red(prevColor) * 255;
                int Gprev = Color.green(prevColor) * 255;
                int Bprev = Color.blue(prevColor) * 255;
                //Log.d(TAG, "COLOR B: " + Rprev + " " + Gprev + " " + Bprev);

                difference += Math.abs(RImageA - Rprev);
                difference += Math.abs(GImageA - Gprev);
                difference += Math.abs(BImageA - Bprev);

                // intensity levels of frame A and frame B
                intensityA = intensity(RImageA, GImageA, BImageA);
                totalIntensityA += intensity(RImageA, GImageA, BImageA);
                intensityB = intensity(Rprev, Gprev, Bprev);
                totalIntensityB += intensity(Rprev, Gprev, Bprev);

                if (curColor != prevColor) {
                    if (isCompatible(intensityA, intensityB)) {
                        notCompatibleCount++;
                    }
                    countDiff++;
                }
            }
        }

        // EVALUATING THE OUTPUT

        // Total number of red pixels = width * height
        // Total number of blue pixels = width * height
        // Total number of green pixels = width * height
        // So total number of pixels = width * height * 3
        double totalPixels = mDisplayHeight * mDisplayWidth * 3;
        System.out.println("The total amount of pixels are: " + totalPixels);
        //double differentPixelPercentage = countDiff / totalPixels;
        //double dangerPercent = differentPixelPercentage * difPercentage;
        //difPercentage = notCompatibleCount / countDiff * 100;
        //merging calculations for difPercentage and differentPixelPercentage to get dangerPercent
        double dangerPercent = (notCompatibleCount/totalPixels) * 100;
        System.out.println("The percentage of dangerous Pixels are: " + dangerPercent + "%" + "   " + notCompatibleCount + " / " + countDiff);
        if (dangerPercent > 30) {
            System.out.println("The percentage of dangerous pixels are dangerous");
            totalCount++;
        }

        // Normalizing the value of different pixels for accuracy(average pixels per color component)
        double avg_different_pixels = difference / totalPixels;
        // There are 255 values of pixels in total
        double percentage = (avg_different_pixels / 255) * 100;
        System.out.println("Difference Percentage-->" + percentage);

        // hz will always be dangerous (because of fps), so maybe only take into account when screen is very different?
        if (isHzDangerous((int) elapse)) {
            System.out.println("This GIF's Hz is in the range of being dangerous");
            totalCount++;
        } else {
            System.out.println("This GIF's Hz is in the range of being safe");
        }

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
            showAToast("This is risky!");
        } else if(totalCount == 2) {
            System.out.println("This GIF is dangerous");
            showAToast("Danger detected!");

        } else if (totalCount == 3) {
            System.out.println("This GIF is extreme");
            showAToast("Extreme Danger detected!");

        } else {
            System.out.println("This GIF is safe to watch");
        }

    }
}