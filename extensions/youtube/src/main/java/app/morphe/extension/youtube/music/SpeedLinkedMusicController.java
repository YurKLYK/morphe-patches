package app.morphe.extension.youtube.music;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

/** Runtime coordinator. Location collection and UI are deliberately injected. */
public final class SpeedLinkedMusicController {
    public interface VideoSwitcher { boolean switchTo(String videoId); }

    public static final class BgmSet {
        private final String name;
        private final EnumMap<SpeedLinkedMusic.Band, List<String>> videos = new EnumMap<>(SpeedLinkedMusic.Band.class);
        public BgmSet(String name) { this.name = name == null ? "" : name; }
        public String name() { return name; }
        public void add(SpeedLinkedMusic.Band band, String videoId) {
            if (band == null || videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) return;
            videos.computeIfAbsent(band, ignored -> new ArrayList<>()).add(videoId);
        }
        public List<String> videos(SpeedLinkedMusic.Band band) {
            return new ArrayList<>(videos.getOrDefault(band, new ArrayList<>()));
        }
    }

    private final SpeedLinkedMusic.Smoother smoother;
    private final SpeedLinkedMusic.Stabilizer stabilizer;
    private final VideoSwitcher switcher;
    private final Random random;
    private final Queue<String> pending = new ArrayDeque<>();
    private final Set<String> failedIds = new HashSet<>();
    private BgmSet selectedSet;
    private boolean enabled;
    private boolean manualOverride;
    private String currentVideoId = "";
    private boolean switching;

    public SpeedLinkedMusicController(VideoSwitcher switcher, long smoothingMs, long confirmationMs, long cooldownMs) {
        this(switcher, SpeedLinkedMusic.defaultBands(), smoothingMs, confirmationMs, cooldownMs, 0.5f);
    }

    public SpeedLinkedMusicController(VideoSwitcher switcher, List<SpeedLinkedMusic.SpeedBand> bands,
                                      long smoothingMs, long confirmationMs, long cooldownMs) {
        this(switcher, bands, smoothingMs, confirmationMs, cooldownMs, 0.5f);
    }

    public SpeedLinkedMusicController(VideoSwitcher switcher, List<SpeedLinkedMusic.SpeedBand> bands,
                                      long smoothingMs, long confirmationMs, long cooldownMs, float hysteresisKmh) {
        if (switcher == null) throw new IllegalArgumentException("switcher is required");
        this.switcher = switcher;
        this.smoother = new SpeedLinkedMusic.Smoother(smoothingMs);
        this.stabilizer = new SpeedLinkedMusic.Stabilizer(bands, confirmationMs, cooldownMs, hysteresisKmh);
        this.random = new Random();
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) { smoother.clear(); stabilizer.reset(); pending.clear(); manualOverride = false; }
    }
    public synchronized boolean isEnabled() { return enabled; }
    public synchronized void setManualOverride(boolean manualOverride) { this.manualOverride = manualOverride; }
    public synchronized void setSet(BgmSet set) { selectedSet = set; }
    public synchronized SpeedLinkedMusic.Band currentBand() { return stabilizer.current(); }
    public synchronized Float currentSpeedMs() { return smoother.value(); }
    public synchronized String currentVideoId() { return currentVideoId; }
    public synchronized void observeVideo(String videoId) {
        if (videoId != null && videoId.matches("[A-Za-z0-9_-]{11}")) currentVideoId = videoId;
    }

    public void onSpeed(float speedMs, long timestampMs) {
        String videoId;
        synchronized (this) {
            if (!enabled || manualOverride || selectedSet == null) return;
            smoother.add(speedMs, timestampMs);
            Float smooth = smoother.value();
            if (smooth == null) return;
            SpeedLinkedMusic.Band band = stabilizer.update(smooth, timestampMs);
            if (band == null) return;
            videoId = choose(selectedSet.videos(band));
            if (videoId == null || videoId.equals(currentVideoId) || failedIds.contains(videoId)) return;
            pending.add(videoId);
        }
        drain();
    }

    public synchronized void forceVideo(String videoId) {
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) return;
        manualOverride = true;
        pending.clear();
        pending.add(videoId);
        drain();
    }

    public void playImmediate(String videoId) {
        synchronized (this) {
            if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) return;
            pending.clear();
            pending.add(videoId);
        }
        drain();
    }

    private synchronized String choose(List<String> candidates) {
        if (candidates.isEmpty()) return null;
        List<String> available = new ArrayList<>();
        for (String id : candidates) if (!failedIds.contains(id)) available.add(id);
        if (available.isEmpty()) return null;
        String selected = available.get(random.nextInt(available.size()));
        if (available.size() > 1 && selected.equals(currentVideoId)) selected = available.get((available.indexOf(selected) + 1) % available.size());
        return selected;
    }

    private void drain() {
        String next;
        synchronized (this) {
            if (switching || pending.isEmpty()) return;
            next = pending.poll(); switching = true;
        }
        boolean ok = false;
        try { ok = switcher.switchTo(next); }
        catch (RuntimeException ignored) { }
        synchronized (this) {
            if (ok) currentVideoId = next; else failedIds.add(next);
            switching = false;
            if (!pending.isEmpty()) {
                // Keep only the newest requested video to avoid a rapid cascade.
                String newest = null; while (!pending.isEmpty()) newest = pending.poll(); if (newest != null) pending.add(newest);
            }
        }
        drain();
    }
}
