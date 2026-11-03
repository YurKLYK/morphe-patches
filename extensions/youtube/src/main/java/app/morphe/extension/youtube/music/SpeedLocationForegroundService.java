package app.morphe.extension.youtube.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;

public final class SpeedLocationForegroundService extends Service {
    private static final int NOTIFICATION_ID = 7315;
    private static final String CHANNEL_ID = "morphe_speed_linked_music";
    private SpeedLocationMonitor monitor;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentTitle("Morphe speed-linked music")
                .setContentText("Location is used only while speed-linked music is enabled")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        else startForeground(NOTIFICATION_ID, notification);
        monitor = new SpeedLocationMonitor(this, new SpeedLocationMonitor.Listener() {
            @Override public void onSpeed(float speedMs, long timestampMs) { SpeedLinkedMusicRuntime.onLocationSpeed(speedMs, timestampMs); }
            @Override public void onUnknown() { SpeedLinkedMusicRuntime.onLocationUnknown(); }
        });
        monitor.start(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }

    @Override public void onDestroy() {
        if (monitor != null) monitor.stop();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void createChannel() {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID, "Speed-linked music", NotificationManager.IMPORTANCE_LOW));
    }
}
