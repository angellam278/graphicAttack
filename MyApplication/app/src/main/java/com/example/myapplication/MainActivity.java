package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.media.projection.MediaProjectionManager;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;


//https://stackoverflow.com/questions/31297246/activity-appcompatactivity-fragmentactivity-and-actionbaractivity-when-to-us
public class MainActivity extends AppCompatActivity {

    private static final String _tag = "MyApplication main";
    // NOTE: need to match with the one in service -> maybe make constant file?
    private static final String EXTRA_RESULT_CODE =  "EXTRA_RESULT_CODE";
    public static final String EXTRA_DATA =  "EXTRA_DATA";
    private static final String EXTRA_STATUSBAR_HEIGHT = "EXTRA_STATUSBAR_HEIGHT";

    // permission requests
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSION_STORAGE = {
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    private static final int REQUEST_CODE_MEDIA_PROJECTION = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // verifying permission to write/read files from file system
        // TODO remove
        verifyStoragePermission(this);

        // looks like imagereader is best: https://stackify.dev/121209-take-a-screenshot-using-mediaprojection
        // because doing opengl will just allow shaders, but we just want to reader the pixels. -> we don't want to modify the pixels so no need for shaders
        // ask for permission to record screen
        final MediaProjectionManager _mediaProjectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(_mediaProjectionManager.createScreenCaptureIntent(),  REQUEST_CODE_MEDIA_PROJECTION);

    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    private GLSurfaceView mGLView;


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        //super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_MEDIA_PROJECTION) {
            if (resultCode != RESULT_OK) {
                Toast.makeText(this,
                        "User denied screen sharing permission", Toast.LENGTH_SHORT).show();
                return;
            }

            // cannot startActivity for Result in Service, so need to ask for permission in activity
            // and send result code into the Service
            Bundle mBundle = new Bundle();
            Intent mIntent =new Intent(MainActivity.this, ScreenFilterService.class);
            mBundle.putInt(EXTRA_RESULT_CODE, resultCode);
            mBundle.putInt(EXTRA_STATUSBAR_HEIGHT, getStatusBarHeight());
            mBundle.putParcelable(EXTRA_DATA, data);
            mIntent.putExtras(mBundle);
            startService(mIntent);

        } else {
            Log.e(_tag, "Unknown request code: " + requestCode);
            return;
        }

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

    public static void verifyStoragePermission(Activity activity) {
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, PERMISSION_STORAGE, REQUEST_EXTERNAL_STORAGE);
        }
    }

}
