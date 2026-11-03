package app.morphe.extension.youtube.music;

import android.content.Context;
import android.content.Intent;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.Manifest;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.patches.LoadVideoPatch;
import app.morphe.extension.youtube.settings.Settings;

/** Runtime bridge used by the bytecode patch and the eventual settings UI. */
public final class SpeedLinkedMusicRuntime {
    private static SpeedLinkedMusicController controller;
    private static Handler settingsHandler;
    private static boolean lastSetting;
    private static boolean serviceRequested;
    private static boolean permissionRequested;
    private static String lastConfiguration = "";

    private SpeedLinkedMusicRuntime() {}

    public static void initialize(LoadVideoPatch.PlayerInterface ignored) {
        initialize(Utils.getContext());
    }

    public static synchronized void initialize(Context context) {
        if (controller != null || context == null) return;
        controller = new SpeedLinkedMusicController(
                videoId -> LoadVideoPatch.deliverVideoIntentInProcess(videoId),
                configuredBands(), bounded(Settings.SPEED_LINKED_MUSIC_SMOOTHING_MS.get(), 15_000, 30_000),
                Math.max(0, Settings.SPEED_LINKED_MUSIC_CONFIRMATION_MS.get()),
                Math.max(0, Settings.SPEED_LINKED_MUSIC_COOLDOWN_MS.get()),
                Math.max(0f, Settings.SPEED_LINKED_MUSIC_HYSTERESIS_KMH.get()));
        loadConfiguredSet();
        lastConfiguration = configurationKey();
        settingsHandler = new Handler(Looper.getMainLooper());
        lastSetting = Settings.SPEED_LINKED_MUSIC_ENABLED.get();
        applySetting(context, lastSetting);
        settingsHandler.postDelayed(() -> pollSetting(context), 1_000L);
    }

    public static synchronized void setEnabled(Context context, boolean enabled) {
        initialize(context);
        if (controller == null) return;
        controller.setEnabled(enabled);
        applySetting(context, enabled);
    }

    private static synchronized void pollSetting(Context context) {
        if (controller == null || settingsHandler == null) return;
        boolean enabled = Settings.SPEED_LINKED_MUSIC_ENABLED.get();
        if (enabled != lastSetting) {
            lastSetting = enabled;
            loadConfiguredSet();
            applySetting(context, enabled);
        }
        if (!configurationKey().equals(lastConfiguration)) {
            lastConfiguration = configurationKey();
            loadConfiguredSet();
        }
        if (lastSetting && !serviceRequested) applySetting(context, true);
        if (lastSetting) {
            float simulatedSpeed = Settings.SPEED_LINKED_MUSIC_DEBUG_SPEED_MS.get();
            if (Float.isFinite(simulatedSpeed) && simulatedSpeed >= 0f) onLocationSpeed(simulatedSpeed, System.currentTimeMillis());
        }
        settingsHandler.postDelayed(() -> pollSetting(context), 1_000L);
    }

    private static void applySetting(Context context, boolean enabled) {
        controller.setEnabled(enabled);
        Intent service = new Intent(context, SpeedLocationForegroundService.class);
        if (!enabled) { serviceRequested = false; permissionRequested = false; context.stopService(service); return; }
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Activity activity = Utils.getActivity();
            if (!permissionRequested && activity != null) {
                permissionRequested = true;
                activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7315);
            }
            serviceRequested = false;
            return;
        }
        permissionRequested = false;
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service); else context.startService(service);
            serviceRequested = true;
        } catch (RuntimeException exception) { }
    }

    public static synchronized void onLocationSpeed(float speedMs, long timestampMs) {
        if (controller != null) controller.onSpeed(speedMs, timestampMs);
    }

    public static synchronized void onLocationUnknown() {
        if (controller == null) return;
        if ("RETURN_NORMAL".equalsIgnoreCase(Settings.SPEED_LINKED_MUSIC_UNKNOWN_BEHAVIOR.get())) {
            controller.playImmediate(Settings.SPEED_LINKED_MUSIC_NORMAL_VIDEO_ID.get());
        }
    }

    public static synchronized void onVideoIdChanged(String videoId) {
        if (controller != null) controller.observeVideo(videoId);
    }

    public static synchronized SpeedLinkedMusicController controller() { return controller; }

    public static synchronized void registerVideo(SpeedLinkedMusic.Band band, String videoId) {
        if (band == null || videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) return;
        String selected = selectedSetName();
        String raw = Settings.SPEED_LINKED_MUSIC_BANDS.get();
        if (raw == null) raw = "";
        String[] lines = raw.split("\\R", -1);
        StringBuilder out = new StringBuilder();
        boolean inSelected = false;
        boolean foundBand = false;
        boolean hadSection = false;
        boolean foundSection = false;
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                hadSection = true;
                inSelected = trimmed.substring(1, trimmed.length() - 1).trim().equals(selected);
                foundSection |= inSelected;
            }
            if (inSelected && trimmed.regionMatches(true, 0, band.name(), 0, band.name().length())
                    && (trimmed.length() == band.name().length() || trimmed.charAt(band.name().length()) == '=' || trimmed.charAt(band.name().length()) == '|')) {
                if (!trimmed.contains(videoId)) {
                    int equals = line.indexOf('=');
                    line = equals >= 0 ? line + "," + videoId : band.name() + "=" + videoId;
                }
                foundBand = true;
            }
            if (out.length() > 0) out.append('\n');
            out.append(line);
        }
        if (!hadSection) {
            if (out.length() > 0 && out.charAt(out.length() - 1) != '\n') out.append('\n');
            out.append('[').append(selected).append("]\n");
            out.append(band.name()).append('=').append(videoId);
        } else if (!foundSection) {
            if (out.length() > 0) out.append('\n');
            out.append('[').append(selected).append("]\n");
            out.append(band.name()).append('=').append(videoId);
        } else if (!foundBand) {
            if (out.length() > 0) out.append('\n');
            out.append(band.name()).append('=').append(videoId);
        }
        Settings.SPEED_LINKED_MUSIC_BANDS.save(out.toString());
        loadConfiguredSet();
        lastConfiguration = configurationKey();
    }

    private static String configurationKey() {
        return selectedSetName() + "\u0000"
                + String.valueOf(Settings.SPEED_LINKED_MUSIC_BANDS.get());
    }

    private static List<String> configuredLines() {
        List<String> result = new ArrayList<>();
        String selected = selectedSetName();
        boolean hasSections = false;
        boolean inSelected = selected == null || selected.trim().isEmpty();
        String serialized = Settings.SPEED_LINKED_MUSIC_BANDS.get();
        if (serialized == null) return result;
        for (String line : serialized.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                hasSections = true;
                inSelected = trimmed.substring(1, trimmed.length() - 1).trim().equals(selected);
                continue;
            }
            if (!hasSections || inSelected) result.add(line);
        }
        return result;
    }

    private static String selectedSetName() {
        String selected = Settings.SPEED_LINKED_MUSIC_SET.get();
        return selected == null || selected.trim().isEmpty() ? "Default" : selected.trim();
    }

    private static void loadConfiguredSet() {
        if (controller == null) return;
        SpeedLinkedMusicController.BgmSet set = new SpeedLinkedMusicController.BgmSet(
                selectedSetName());
        for (String line : configuredLines()) {
            String[] pair = line.split("[=:]", 2);
            if (pair.length != 2) continue;
            try {
                String bandToken = pair[0].split("\\|", -1)[0];
                SpeedLinkedMusic.Band band = SpeedLinkedMusic.Band.valueOf(bandToken.trim().toUpperCase(Locale.ROOT));
                for (String videoId : pair[1].split(",")) set.add(band, videoId.trim());
            } catch (IllegalArgumentException ignored) { }
        }
        controller.setSet(set);
    }

    private static int bounded(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    private static List<SpeedLinkedMusic.SpeedBand> configuredBands() {
        List<SpeedLinkedMusic.SpeedBand> bands = new ArrayList<>(SpeedLinkedMusic.defaultBands());
        for (String line : configuredLines()) {
            String[] pair = line.split("[=:]", 2);
            if (pair.length != 2) continue;
            String[] parts = pair[0].split("\\|", -1);
            if (parts.length < 4) continue;
            try {
                SpeedLinkedMusic.Band band = SpeedLinkedMusic.Band.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
                float min = Float.parseFloat(parts[2].trim());
                float max = parts[3].trim().equalsIgnoreCase("inf") ? Float.POSITIVE_INFINITY : Float.parseFloat(parts[3].trim());
                SpeedLinkedMusic.SpeedBand replacement = new SpeedLinkedMusic.SpeedBand(band, parts[1].trim(), min, max);
                for (int i = 0; i < bands.size(); i++) if (bands.get(i).band == band) bands.set(i, replacement);
            } catch (IllegalArgumentException ignored) { }
        }
        return bands;
    }
}
