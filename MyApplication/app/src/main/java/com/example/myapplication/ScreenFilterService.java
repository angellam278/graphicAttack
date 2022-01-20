package com.example.myapplication;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

public class ScreenFilterService extends Service {

    private static final String TAG = "ScreenFilterService";

    public static int STATE_ACTIVE = 0;
    public static int STATE_INACTIVE = 1;

    public static int STATE;
    static {
        STATE = STATE_INACTIVE;
    }


    public ScreenFilterService() {
    }

    // needed to implement, for when others want to bind to this service
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        // can set state active and inactive in here
        STATE = STATE_ACTIVE;
        Log.d(TAG, "onCreate called");

        //createNotificationChannel();
        //do MediaProjection things that you want
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // WindowManager windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        // assert windowManager != null;
        // windowManager.removeView(mView);
        STATE = STATE_INACTIVE;
        Log.d(TAG, "onDestroy called");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand called");
        Toast.makeText(getApplicationContext(),"There is a Service running in Background",
                Toast.LENGTH_SHORT).show();
        return super.onStartCommand(intent, flags, startId);
    }
//
//    //https://stackoverflow.com/questions/64591594/android-10-androidforegroundservicetype-mediaprojection-not-working-with-ser/68343645#68343645
//    private void createNotificationChannel() {
//        Notification.Builder builder = new Notification.Builder(this.getApplicationContext()); //获取一个Notification构造器
//        Intent nfIntent = new Intent(this, MainActivity.class); //点击后跳转的界面，可以设置跳转数据
//
//        builder.setContentIntent(PendingIntent.getActivity(this, 0, nfIntent, 0)) // 设置PendingIntent
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
//    }
}