package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.core.app.NotificationCompat;
import androidx.core.graphics.ColorUtils;

import java.nio.ByteBuffer;


public class ScreenFilterService extends AccessibilityService {

    // APP CONFIGS
    private int useToast = 0; // 0: use heads up notification, 1: use toast
    private int solidOverlay = 1; // 0: use interpolated color, 1: use solid grey

    // variables prefixed with _ (underscore) are private variables

    // for printing debug lines with Logging
    private static final String _tag = "ScreenFilterService";

    // calculated once
    private int _displayWidth;
    private int _displayHeight;
    private int _screenDensity;
    private int _statusBarHeight;

    // for media projection
    private Surface _surface;
    private MediaProjection _mediaProjection;
    private ImageReader _imgReader;
    //MediaProjection should only be sending frames when something on the screen changes.
    private MediaProjectionManager _mediaProjectionManager;
    private static final String EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA = "EXTRA_DATA";
    private static final String EXTRA_STATUSBAR_HEIGHT = "EXTRA_STATUSBAR_HEIGHT";

    // app's toast
    private Toast appToast;

    // data
    // flag to check if its the first to check
    private boolean _isFirst = true;
    // list of brightness for previous frame
    private double[] _prevBrightnessList;
    // list of color ints for previous frame
    private int[] _prevColorList;

    // list of color ints for current frame
    private double[] _currentBrightnessList;
    private int[] _currentColorList;

    // for evaluation
    private double _localBrightnessChange = 0.0;
    private double _localExtreme = 0.0;
    private int _frameCount = 0;
    private int _flashFrameCount = -1; // frame count when latest flash was detected
    private int _changeCount = 0; // if 2 means flash
    // flash elapse time, store start of the flash
    private long _flashElapseStart = -1;
    private double _prevFrameAverageBrightness;

    // for overlay display
    private int[] _pixelX; // stores pixel x coordinate
    private int[] _pixelY; // stores pixel y coordinate
    private int[] _pixelColorInt; // stores pixel color int
    private Bitmap _bitmap;
    FrameLayout _mLayout;

    public ScreenFilterService() {
    }

    @Override
    public void onCreate() {
        super.onCreate();
        // can set state active and inactive in here
        createNotificationChannel();
        _mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        showAToast("There is a Service running in Background"); // tells user
        startScreenFilter(intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onServiceConnected() {
        // called after enabling:
        // to enable on device: go to settings -> accessibility and turn on for this application
        // if this is not enabled, we cannot draw overlay

        super.onServiceConnected();

        // Create an overlay
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        _mLayout = new FrameLayout(this);

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        // layout is a view
        // TYPE_ACCESSIBILITY_OVERLAY allows us to send touch gestures through our overlay and draw on top of other apps
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;

        LayoutInflater inflater = LayoutInflater.from(this);
        inflater.inflate(R.layout.overlay, _mLayout);

        wm.addView(_mLayout, lp);

        // set full screen
       _bitmap = Bitmap.createBitmap(_displayWidth, _displayHeight, Bitmap.Config.ARGB_8888);
       if (_bitmap == null) {
           Log.e(_tag, "failed to create overlay bitmal.");
       }

        updateBitmapView();
    }

    protected void updateBitmapView() {
        // redisplay bitmap on imageview
        // image view is needed to display bitmap on layout
        ImageView iv = (ImageView)_mLayout.findViewById(R.id.inner);
        iv.setPadding(0, -_statusBarHeight,0,0);
        _mLayout.setPadding(0,0,0,0);

        if (iv == null) {
            Log.e(_tag, "invalid image view!");
        } else {
            iv.setImageBitmap(_bitmap);
        }
    }


    // to avoid display overlapping toasts
    public void showAToast (String st){ //"Toast toast" is declared in the class

        if (useToast == 1) {
            // TOAST appears on the bottom (newer OS doesn't allow repositioning of toasts)
            // NOTE: toast may obscure gif area and make it detect as safe

            try{ appToast.getView().isShown();     // true if visible
                appToast.setText(st);
            } catch (Exception e) {         // invisible if exception
                appToast = Toast.makeText(getApplicationContext(), st, Toast.LENGTH_SHORT);
            } finally {
                appToast = Toast.makeText(getApplicationContext(), st, Toast.LENGTH_SHORT);
            }
            appToast = Toast.makeText(getApplicationContext(), st, Toast.LENGTH_SHORT);
            appToast.setText(st);
            appToast.show();  //finally display it

        } else {

            // heads up notification (like ones when you get calls)
            // vibration, sound, visibility has to be set per device in settings > app notifications
            Intent intent = new Intent(this, MainActivity.class);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                    intent, PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "notification_id")
                    .setContentIntent(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Warning")
                    .setContentText(st)
                    .setPriority(Notification.PRIORITY_MAX);
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.notify(110, builder.build());

        }

    }

    /**
     * start screen recording
     * @param intent
     */
    private void startScreenFilter(final Intent intent) {
        Log.d(_tag, "startScreenFilter" );

        final int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0);
        _statusBarHeight = intent.getIntExtra(EXTRA_STATUSBAR_HEIGHT, 0);

        // get MediaProjection
        _mediaProjection = _mediaProjectionManager.getMediaProjection(resultCode, intent.getParcelableExtra(EXTRA_DATA));
        if (_mediaProjection != null) {
            final DisplayMetrics metrics = getResources().getDisplayMetrics();
            _displayWidth = metrics.widthPixels;
            _displayHeight = metrics.heightPixels;
            _screenDensity = metrics.densityDpi;

            // initializing imageReader to read from Media Projection in image format -> will give us access to image buffer
            // https://stackoverflow.com/questions/25462277/camera-preview-image-data-processing-with-android-l-and-camera2-api
            // https://chromium.googlesource.com/chromium/src/+/2f731e17983201082d9fc725cf7717868fc1e75d/media/capture/content/android/java/src/org/chromium/media/ScreenCapture.java
            // ImageFormat.YUV_420_888 - not all devices support this, but apparently is more efficient
            // so we have to use  PixelFormat.RGBA_8888
            // maximage = lowest as possible, number acquired before need to release
            _imgReader = ImageReader.newInstance(_displayWidth, _displayHeight, PixelFormat.RGBA_8888, 1);
            _surface = _imgReader.getSurface();
            _mediaProjection.createVirtualDisplay(
                    "CAPTURE_THREAD_NAME", _displayWidth, _displayHeight, _screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, _surface, null,
                    null
            );

            // wait for new images to be ready
            _imgReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {

                    _frameCount ++;

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
                            int rowPadding = rowStride - pixelStride * _displayWidth;
                            int offset = 0;

                            ByteBuffer buffer = planes[0].getBuffer();

                            // create buffer to store pixel brightness because we only need brightness for comparison
                            _currentBrightnessList = new double[_displayHeight*_displayWidth];
                            _currentColorList = new int[_displayHeight*_displayWidth];

                            for (int i = 0; i < _displayHeight; ++i) {
                                for (int j = 0; j < _displayWidth; ++j) {
                                    // RGBA_8888: Each pixel is 4 bytes: 8 red bits, 8 green bits, 8 blue bits, and 8 alpha
                                    // 1 byte = 8 bits = 00000000~11111111 (binary)
                                    // https://stackoverflow.com/questions/11380062/what-does-value-0xff-do-in-java
                                    // << shifts bits to the left
                                    // color: https://developer.android.com/reference/android/graphics/Color
                                    // a,r,g,b
                                    int colorInt = (buffer.get(offset + 3) & 0xff) << 24 | (buffer.get(offset) & 0xff) << 16 | (buffer.get(offset + 1) & 0xff) << 8 | (buffer.get(offset + 2)  & 0xff);

                                    int idx = (i*_displayWidth) + j;

                                    // compute brightness
                                    int r = Color.red(colorInt) ;
                                    int g = Color.green(colorInt) ;
                                    int b = Color.blue(colorInt) ;

                                    // store values for comparison between frames
                                    _currentBrightnessList[idx] = toBrightness(luminance(r, g, b));
                                    // if we print out colorInt -> will be negative because:
                                    // https://stackoverflow.com/questions/25073930/color-parsecolor-returns-negative
                                    _currentColorList[idx] = colorInt;

                                    offset += pixelStride;
                                }
                                offset += rowPadding;
                            }

                            if (_isFirst == false){
                                // compare with previous image
                                compareBuffers(buffer);
                            }
                            // make current a previous frame
                            _prevBrightnessList = _currentBrightnessList;
                            _prevColorList = _currentColorList;

                            // close image (max access = 1)
                            img.close();

                            _isFirst = false;
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }
            }, null);


        }
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
                        .setPriority(Notification.PRIORITY_MAX)
                        .setCategory(Notification.CATEGORY_CALL)

                        // Use a full-screen intent only for the highest-priority alerts where you
                        // have an associated activity that you would like to launch after the user
                        // interacts with the notification. Also, if your app targets Android 10
                        // or higher, you need to request the USE_FULL_SCREEN_INTENT permission in
                        // order for the platform to invoke this notification.
                        .setFullScreenIntent(fullScreenPendingIntent, true);

        // notification
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("notification_id", "notification_name", NotificationManager.IMPORTANCE_HIGH);
            channel.setShowBadge(true);
            channel.setDescription("description");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId("notification_id");
        }

        Notification incomingCallNotification = builder.build();
        startForeground(110, incomingCallNotification);
    }

    // Utility functions

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

    private static boolean isFrameDangerous(int framesElapsed) {
        // check display rate with display.getRefreshRate();
        // 1 fps = 1hz
        // dangerous is 2 (from paper) - 3 (from prev implementation) hz
        if (framesElapsed < 4) {
            return true;
        }
        return false;
    }

    // main function to evaluate the change in previous and current buffers
    private void compareBuffers(ByteBuffer imgA) {
        int riskCount = 0;

        int flashingPixelCount = 0;
        double currentTotalBrightness = 0.0;

        _pixelX = new int[_displayHeight*_displayWidth];
        _pixelY = new int[_displayHeight*_displayWidth];
        _pixelColorInt = new int[_displayHeight*_displayWidth];

        // iterates through pixels to find percentage of flashing pixels and calculate average frame brightness
        for (int i = 0; i < _displayHeight; ++i) {
            for (int j = 0; j < _displayWidth; ++j) {
                //iterating by row
                int idx = (i*_displayWidth) + j;

                double prevPixelBrightness = _prevBrightnessList[idx];
                double currentPixelBrightness = _currentBrightnessList[idx];

                // to compute average brightness for current frame
                // previous frame's average brightness is already computed and stored
                currentTotalBrightness += _currentBrightnessList[idx];

                // do we need to look at darker threshold?
                if (Math.abs(prevPixelBrightness - currentPixelBrightness) > 20) {
                    // flashing pixel

                    // store location and color for overlay (interpolated)
                    _pixelX[flashingPixelCount] = j;
                    _pixelY[flashingPixelCount] = i;
                    _pixelColorInt[flashingPixelCount] = ColorUtils.blendARGB(_prevColorList[idx], _currentColorList[idx], 0.50f);

                    // JUST MASK WITH NEUTRAL GREY TODO

                    flashingPixelCount ++;
                }
            }
        }

        // luminance brightness is to 255 which is the value of one color component so we should *3 for RGB
        int totalPixels = _displayHeight* _displayWidth * 3;
        double percent_flash_pixel = flashingPixelCount / totalPixels;

        double currentFrameAverageBrightness =  currentTotalBrightness / totalPixels;//rgba
        double prevFrameAverageBrightness = _prevFrameAverageBrightness;

        // intensity difference
        // positive means increase in brightness
        double brightnessChange = currentFrameAverageBrightness - prevFrameAverageBrightness;

        // store for next comparison
        _prevFrameAverageBrightness = currentFrameAverageBrightness;

        // TODO: still not considering special patterns and red transitions mentioned in the paper

        // if there is a change in brightness
        if (brightnessChange != 0.0) {

            // accumulate lowering of intensity or increasing of intensity
            if ((brightnessChange < 0 && _localBrightnessChange <=0) || (brightnessChange > 0 && _localBrightnessChange >=0)) {
                // accumulate brightness in the same direction (increasing or decreasing)
                // negative means darkening, positive means brightening
                _localBrightnessChange += brightnessChange;
            } else {
                // _localBrightnessChange sign changes (darkening to brightening or vice versa) - peak

                // flash (determined by hz and change in colors) - interval between two peaks
                if (_flashFrameCount!= -1 && isFrameDangerous((_frameCount - _flashFrameCount))) {
                    // one peak of flash
                    _changeCount ++;

                    // pair of flash
                    if (_changeCount > 2) {
                        // pair of change = flash

                        // store time that flash starts
                        long cur = System.currentTimeMillis();

                        // high area flash
                        // frame count will always be > 0
                        // 1) combined area of flashes occurring concurrently occupies more than 25% of the displayed screen area;
                        // 2) the  flash  frequency  is  higher  than 3 Hz
                        if (percent_flash_pixel > 0.25) {
                            // need at least a pair so 2 counts
                            Log.d(_tag, "High area of fast flashing.");
                            riskCount++;
                        }

                        // series of flashing images (flashing/ intensity going up and down) for longer than 5 seconds can be risky
                        if ((_flashElapseStart!=-1) && ((cur - _flashElapseStart / 1000) > 5)){
                            // even if the difference is not high but this will also include small flickers so
                            // need to have a big area of flash but not as high as the other?
                            if (percent_flash_pixel >= 0.08) {
                                Log.d(_tag, "Flashing longer than 5 seconds detected.");
                                riskCount++;
                            }
                            // reset to not have repeated alerts
                            _flashElapseStart = -1;
                        }
                        if (_flashElapseStart == -1) {
                            // start timer for a flash
                            _flashElapseStart = cur;
                        }

                        // high contrast flash
                        // if the brightness change is more than 20 cd/m2 and the darker image's brightness is lower than 160 cd/m2 it is risky
                        if (Math.abs(_localExtreme - _localBrightnessChange) > 20) {
                            if (Math.min(_localExtreme, _localBrightnessChange) < 160) {
                                // paper says 4 flashes is dangerous do we need that ?
                                Log.d(_tag, "Flash between a dark image is detected.");
                                riskCount++;
                            }
                        }
                        //reset after each two peaks
                        _changeCount = 0;
                    }
                } else {
                    Log.d(_tag, "not flash (slow change in brightness)");
                    // not flash (slow change in brightness)

                    // not a flash so reset flash elapse
                    _flashElapseStart = -1;
                }

                // store _frameCount for latest detected flash
                _flashFrameCount = _frameCount;
                // store local extreme
                _localExtreme = _localBrightnessChange;
                // reset
                _localBrightnessChange = 0;
            }
        }

        // Evaluating the results
        if (riskCount == 0) {
            // clear overlay
            if (_bitmap != null) {
                _bitmap.eraseColor(Color.TRANSPARENT);
            }
        } else {

            if (riskCount == 1) {
                System.out.println("This GIF is risky");
                showAToast("This is risky!");
            } else if(riskCount == 2) {
                System.out.println("This GIF is dangerous");
                showAToast("Danger detected!");
            } else if (riskCount == 3) {
                System.out.println("This GIF is extreme");
                showAToast("Extreme Danger detected!");
            } else {
                // should never be called
                Log.e(_tag, "This GIF is beyond extreme");
                showAToast("Extreme Danger detected!");
            }

            // draw overlay
            if (_bitmap != null) {
                for (int i = 0; i < _pixelX.length; ++i) {
                    int x = _pixelX[i];
                    int y = _pixelY[i];

                    int r = Color.red(_pixelColorInt[i]) ;
                    int g = Color.green(_pixelColorInt[i]) ;
                    int b = Color.blue(_pixelColorInt[i]) ;

                    if (solidOverlay == 1) {
                        _bitmap.setPixel(x, y , Color.GRAY);
                    } else {
                        _bitmap.setPixel(x, y , Color.rgb(r,g,b));
                    }
                }
            }

        }

        if (_mLayout != null) {
            updateBitmapView();
        }

    }
}

//I/Choreographer: Skipped 84 frames!  The application may be doing too much work on its main thread.
//https://github.com/flutter/flutter/issues/40563

// debugging tip
// System.out.println("message to print"); -> to print to the "Run" console
// Log.e(TAG_string, "message"); -> to print with logger .e will be red (for error)
// Log.d <- debug log message, .w is for warning