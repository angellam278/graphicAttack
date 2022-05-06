package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.content.res.Resources;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.Toast;


/*
    There are 3 files I made: ScreenFilterService.Java, MainActivity.java, overlay.xml, activity_main.xml
    .xml files are the UI files
    AndroidManifest.xml is not a UI file but it tells the App everything the app needs to know of.
    MainActivity.java is the main entry of the app which launches the ScreenFilterService in the background.

    If you see this error: I/Choreographer: Skipped 84 frames!  The application may be doing too much work on its main thread.
    I'm not sure if its still a bug, but possibly multithreading in service can solve this because pixel evaluations are parallel?
    Here are what others said about id: https://github.com/flutter/flutter/issues/40563

    DEBUGGING TIPS:
    System.out.println("message to print"); -> to print to the "Run" console
    Log.e(TAG_string, "message"); -> to print with logger
        Log.e <- (red) show error messages
        Log.d <- (grey) show debug log message
        Log.w <- show warning log messages

    An Activity gives us the main UI of the app.
    (https://developer.android.com/reference/android/app/Activity)
    (why AppCompat Activity: //https://stackoverflow.com/questions/31297246/activity-appcompatactivity-fragmentactivity-and-actionbaractivity-when-to-us)
*/


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MyApplication main";

    // NOTE: need to match with the ones in service
    // (they're request code strings, doesn't matter what they are but just need to be unique)
    private static final String EXTRA_RESULT_CODE =  "EXTRA_RESULT_CODE";
    private static final String EXTRA_DATA =  "EXTRA_DATA";
    private static final String EXTRA_STATUSBAR_HEIGHT = "EXTRA_STATUSBAR_HEIGHT";
    private static final String EXTRA_USETOAST = "EXTRA_USETOAST";
    private static final String EXTRA_OVERLAYTYPE = "EXTRA_OVERLAYTYPE";

    private static final String EXTRA_FLASHDURATION = "EXTRA_FLASHDURATION";
    private static final String EXTRA_LONGFLASHAREA = "EXTRA_LONGFLASHAREA";
    private static final String EXTRA_FLASHFRAMECOUNT = "EXTRA_FLASHFRAMECOUNT";
    private static final String EXTRA_HIGHAREAPERCENT = "EXTRA_HIGHAREAPERCENT";
    private static final String EXTRA_BRIGHTNESSDIFF = "EXTRA_BRIGHTNESSDIFF";
    private static final String EXTRA_DARKERBRIGHTNESS = "EXTRA_DARKERBRIGHTNESS";

    private static final int REQUEST_CODE_MEDIA_PROJECTION = 2;

    private RadioGroup _overlayGroup;
    private Switch _toastSwitch;
    private EditText _flashingDuration;
    private EditText _longFlashArea;
    private EditText _flashFrameCount;
    private EditText _highAreaPercent;
    private EditText _brightnessDiff;
    private EditText _darkerBrightness;
    private Button _resetButton;
    private Button _updateButton;

    private Intent _mediaRequestIntentData;
    private int _mediaRequestresultCode;

    private int _noOverlayId;
    private int _solidOverlayId;
    private int _lerpOverlayId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // set UI
        // R.layout means using the res/layout/activity_main.xml UI files
        setContentView(R.layout.activity_main);

        // store UI elements
        _overlayGroup = (RadioGroup) findViewById(R.id.overlayGroup);
        _toastSwitch = (Switch) findViewById(R.id.toastSwitch);

        _flashingDuration = (EditText) findViewById(R.id.flashingDuration);
        _longFlashArea = (EditText) findViewById(R.id.longFlashArea);
        _flashFrameCount = (EditText) findViewById(R.id.flashFrameCount);
        _highAreaPercent = (EditText) findViewById(R.id.highAreaPercent);
        _brightnessDiff = (EditText) findViewById(R.id.brightnessDiff);
        _darkerBrightness = (EditText) findViewById(R.id.darkerBrightness);

        _resetButton = (Button) findViewById(R.id.resetButton);
        _updateButton = (Button) findViewById(R.id.updateService);

        _noOverlayId = findViewById(R.id.noOverlay).getId();
        _solidOverlayId = findViewById(R.id.solidOverlay).getId();
        _lerpOverlayId = findViewById(R.id.lerpOverlay).getId();

        // update service's configs if switch is changed
        _overlayGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener(){
            public void onCheckedChanged(RadioGroup group, int checkId) {
                updateService();
            }
        });
        _toastSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                updateService();
            }
        });

        // reset settings if reset button is clicked
        _resetButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // reset to paper's original values
                _flashingDuration.setText("5"); // 5 seconds
                _longFlashArea.setText("8"); // 8%
                _flashFrameCount.setText("4"); // 4 frames
                _highAreaPercent.setText("25"); // 25%
                _brightnessDiff.setText("20"); //20 cd/m^2
                _darkerBrightness.setText("160"); // 160
            }
        });

        // update the service (resend values) only when button is pressed (to avoid resending too much)
        _updateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // resend values to service
                updateService();
            }
        });

        // ask for permission to record screen
        final MediaProjectionManager _mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(_mediaProjectionManager.createScreenCaptureIntent(),  REQUEST_CODE_MEDIA_PROJECTION);

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(this,
                        "User denied screen sharing permission", Toast.LENGTH_SHORT).show();
                return;
            }

            // store to later use to update service
            _mediaRequestIntentData = data;
            _mediaRequestresultCode = resultCode;

            // cannot startActivity for Result in Service, so need to ask for permission in activity
            // and send result code into the Service
            updateService();

        } else {
            Log.e(TAG, "Unknown request code: " + requestCode);
            return;
        }

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private void updateService(){
        // Bundle wraps data we want to send to the Service with the Intent
        Bundle mBundle = new Bundle();
        Intent mIntent =new Intent(MainActivity.this, ScreenFilterService.class);

        mBundle.putInt(EXTRA_RESULT_CODE, _mediaRequestresultCode);
        mBundle.putInt(EXTRA_STATUSBAR_HEIGHT, getStatusBarHeight());
        mBundle.putBoolean(EXTRA_USETOAST, _toastSwitch.isChecked());
        int btnId = _overlayGroup.getCheckedRadioButtonId();
        int overlayMode = -1;
        if (btnId == _noOverlayId) {
            overlayMode = 0;
        } else if (btnId == _solidOverlayId) {
            overlayMode = 1;
        } else if (btnId == _lerpOverlayId) {
            overlayMode = 2;
        }
        mBundle.putInt(EXTRA_OVERLAYTYPE, overlayMode);

        // evaluation values
        String flashingDurStr = _flashingDuration.getText().toString();
        String flashingAreaStr = _longFlashArea.getText().toString();
        String flashingFrameStr = _flashFrameCount.getText().toString();
        String highAreaStr = _highAreaPercent.getText().toString();
        String brightDiffStr = _brightnessDiff.getText().toString();
        String darknessStr = _darkerBrightness.getText().toString();

        // catching empty strings
        if (flashingDurStr.isEmpty()) {
            flashingDurStr = "0";
        }
        if (flashingAreaStr.isEmpty()) {
            flashingAreaStr = "0";
        }
        if (flashingFrameStr.isEmpty()) {
            flashingFrameStr = "0";
        }
        if (highAreaStr.isEmpty()) {
            highAreaStr = "0";
        }
        if (brightDiffStr.isEmpty()) {
            brightDiffStr = "0";
        }
        if (darknessStr.isEmpty()) {
            darknessStr = "0";
        }

        mBundle.putInt(EXTRA_FLASHDURATION, Integer.parseInt(flashingDurStr));
        mBundle.putInt(EXTRA_LONGFLASHAREA, Integer.parseInt(flashingAreaStr));
        mBundle.putInt(EXTRA_FLASHFRAMECOUNT, Integer.parseInt(flashingFrameStr));
        mBundle.putInt(EXTRA_HIGHAREAPERCENT, Integer.parseInt(highAreaStr));
        mBundle.putInt(EXTRA_BRIGHTNESSDIFF, Integer.parseInt(brightDiffStr));
        mBundle.putInt(EXTRA_DARKERBRIGHTNESS, Integer.parseInt(darknessStr));

        mBundle.putParcelable(EXTRA_DATA, _mediaRequestIntentData);

        mIntent.putExtras(mBundle);

        // restart service
        stopService(mIntent);
        startService(mIntent);
    }

    // src: https://www.tutorialspoint.com/what-is-the-height-of-the-status-bar-in-android
    private int getStatusBarHeight() {
        int height;
        Resources myResources = getResources();
        int idStatusBarHeight = myResources.getIdentifier( "status_bar_height", "dimen", "android");
        if (idStatusBarHeight > 0) {
            height = getResources().getDimensionPixelSize(idStatusBarHeight);
        } else {
            height = 0;
        }
        return height;
    }

}
