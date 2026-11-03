package app.morphe.extension.youtube.music;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;

/**
 * Device-local speed source. It does not persist locations and is inactive
 * until start() is called by the opt-in music mode.
 */
public final class SpeedLocationMonitor {
    public interface Listener { void onSpeed(float speedMs, long timestampMs); void onUnknown(); }

    private static final long MIN_INTERVAL_MS = 5_000L;
    private static final float MAX_ACCURACY_METERS = 50f;
    private final LocationManager locationManager;
    private final Listener listener;
    private final LocationListener locationListener = this::onLocation;
    private Location previous;
    private boolean running;

    public SpeedLocationMonitor(Context context, Listener listener) {
        if (context == null || listener == null) throw new IllegalArgumentException("context and listener are required");
        locationManager = (LocationManager) context.getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        this.listener = listener;
    }

    public boolean hasPermission(Context context) {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint("MissingPermission")
    public synchronized boolean start(Context context) {
        if (!hasPermission(context) || locationManager == null) { listener.onUnknown(); return false; }
        if (running) return true;
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, MIN_INTERVAL_MS, 0f,
                    locationListener, Looper.getMainLooper());
            running = true;
            return true;
        } catch (SecurityException | IllegalArgumentException exception) {
            listener.onUnknown();
            return false;
        }
    }

    public synchronized void stop() {
        if (!running) return;
        locationManager.removeUpdates(locationListener);
        running = false;
        previous = null;
    }

    public synchronized boolean isRunning() { return running; }

    private void onLocation(Location location) {
        if (location == null || (location.hasAccuracy() && location.getAccuracy() > MAX_ACCURACY_METERS)) {
            listener.onUnknown();
            return;
        }
        float speed = Float.NaN;
        if (location.hasSpeed() && Float.isFinite(location.getSpeed()) && location.getSpeed() >= 0f) {
            speed = location.getSpeed();
        } else if (previous != null) {
            long deltaMs = location.getTime() - previous.getTime();
            if (deltaMs > 0L) speed = previous.distanceTo(location) / (deltaMs / 1000f);
        }
        previous = new Location(location);
        if (Float.isFinite(speed) && speed >= 0f) listener.onSpeed(speed, location.getTime());
        else listener.onUnknown();
    }
}
