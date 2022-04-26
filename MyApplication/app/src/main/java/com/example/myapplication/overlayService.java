package com.example.myapplication;

import android.accessibilityservice.AccessibilityService;
import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.example.myapplication.R;

import java.util.zip.Inflater;

public class overlayService extends AccessibilityService {

    //    The lifecycle of an accessibility service is managed exclusively by the system and follows the established service life cycle.
    //    An accessibility service starts when the user explicitly turns the service on in the device settings.
    //    After the system binds to a service, it calls onServiceConnected(). This method can be overridden by services that want to perform post binding setup.
    //    An accessibility service stops either when the user turns it off in device settings or when it calls disableSelf().

    FrameLayout mLayout;
    View mView;

    @Override
    protected void onServiceConnected() {
        // Create an overlay
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        mLayout = new FrameLayout(this);
        mLayout.setBackgroundColor(ContextCompat.getColor(this, R.color.white));

        //mView = new View(this);
        mLayout.setBackgroundColor(Color.parseColor("#C1424200"));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        lp.width = WindowManager.LayoutParams.MATCH_PARENT;
        lp.height = WindowManager.LayoutParams.MATCH_PARENT;
        LayoutInflater inflater = LayoutInflater.from(this);
        inflater.inflate(R.layout.overlay, mLayout);
        wm.addView(mLayout, lp);

    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
    }

    @Override
    public void onInterrupt() {
    }
}
