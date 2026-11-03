/*
 * Copyright 2026 Morphe.
 *
 * Speed classification and stability logic for the optional speed-linked
 * music mode. This class has no Android dependencies so it can be unit tested
 * with simulated speeds before it is connected to LocationManager.
 */
package app.morphe.extension.youtube.music;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;

public final class SpeedLinkedMusic {
    private SpeedLinkedMusic() {}
    public static final float KMH_PER_MS = 3.6f;
    public enum Band { STOPPED, WALKING, RUNNING, MEDIUM, FAST, VERY_FAST }

    public static String displayName(Band band) {
        if (band == null) return "速度不明";
        switch (band) {
            case STOPPED: return "停止";
            case WALKING: return "徒歩";
            case RUNNING: return "ランニング";
            case MEDIUM: return "中速移動";
            case FAST: return "高速移動";
            case VERY_FAST: return "超高速移動";
            default: return "速度不明";
        }
    }
    public static final class SpeedBand {
        public final Band band; public final String name; public final float minKmh; public final float maxKmh;
        public SpeedBand(Band band, String name, float minKmh, float maxKmh) {
            if (band == null || name == null || name.trim().isEmpty() || minKmh < 0 || maxKmh <= minKmh) throw new IllegalArgumentException("Invalid speed band");
            this.band = band; this.name = name; this.minKmh = minKmh; this.maxKmh = maxKmh;
        }
        public boolean contains(float kmh) { return kmh >= minKmh && kmh < maxKmh; }
    }
    public static List<SpeedBand> defaultBands() {
        List<SpeedBand> r = new ArrayList<>();
        r.add(new SpeedBand(Band.STOPPED, "停止", 0f, 2f));
        r.add(new SpeedBand(Band.WALKING, "徒歩", 2f, 7f));
        r.add(new SpeedBand(Band.RUNNING, "ランニング", 7f, 15f));
        r.add(new SpeedBand(Band.MEDIUM, "中速移動", 15f, 30f));
        r.add(new SpeedBand(Band.FAST, "高速移動", 30f, 80f));
        r.add(new SpeedBand(Band.VERY_FAST, "超高速移動", 80f, Float.POSITIVE_INFINITY));
        return r;
    }
    public static Band classify(float speedMs, List<SpeedBand> bands) {
        if (!Float.isFinite(speedMs) || speedMs < 0f) return null;
        float kmh = speedMs * KMH_PER_MS;
        for (SpeedBand band : bands) if (band.contains(kmh)) return band.band;
        return bands.isEmpty() ? null : bands.get(bands.size() - 1).band;
    }
    public static final class Smoother {
        private final long windowMs; private final Deque<Sample> samples = new ArrayDeque<>();
        public Smoother(long windowMs) { if (windowMs < 15_000L || windowMs > 30_000L) throw new IllegalArgumentException("Smoothing window must be 15-30 seconds"); this.windowMs = windowMs; }
        public void add(float speedMs, long timestampMs) {
            if (!Float.isFinite(speedMs) || speedMs < 0f) return;
            samples.addLast(new Sample(speedMs, timestampMs)); long cutoff = timestampMs - windowMs;
            while (!samples.isEmpty() && samples.peekFirst().timeMs < cutoff) samples.removeFirst();
        }
        public Float value() {
            if (samples.isEmpty()) return null; List<Float> values = new ArrayList<>();
            for (Sample sample : samples) values.add(sample.speedMs); Collections.sort(values);
            int trim = values.size() >= 5 ? Math.max(1, values.size() / 5) : 0, from = trim, to = values.size() - trim; float sum = 0f;
            for (int i = from; i < to; i++) sum += values.get(i); return sum / Math.max(1, to - from);
        }
        public void clear() { samples.clear(); }
    }
    public static final class Stabilizer {
        private final long confirmationMs, cooldownMs; private final float hysteresisKmh; private final List<SpeedBand> bands;
        private Band current, candidate; private long candidateSince, lastChange;
        public Stabilizer(List<SpeedBand> bands, long confirmationMs, long cooldownMs) {
            this(bands, confirmationMs, cooldownMs, 0.5f);
        }
        public Stabilizer(List<SpeedBand> bands, long confirmationMs, long cooldownMs, float hysteresisKmh) {
            if (bands == null || bands.isEmpty() || confirmationMs < 0 || cooldownMs < 0) throw new IllegalArgumentException("Invalid stabilizer settings");
            if (!Float.isFinite(hysteresisKmh) || hysteresisKmh < 0f) throw new IllegalArgumentException("Invalid hysteresis");
            this.bands = new ArrayList<>(bands); this.bands.sort(Comparator.comparingDouble(b -> b.minKmh)); this.confirmationMs = confirmationMs; this.cooldownMs = cooldownMs; this.hysteresisKmh = hysteresisKmh;
        }
        public Band current() { return current; }
        public Band update(float speedMs, long nowMs) {
            Band next = classify(speedMs, bands); if (next == null || next == current) { candidate = null; return current; }
            if (current != null && !outsideHysteresis(speedMs, next)) { candidate = null; return current; }
            if (current != null && nowMs - lastChange < cooldownMs) return current;
            if (candidate != next) {
                candidate = next; candidateSince = nowMs;
                if (confirmationMs > 0L) return current;
            }
            if (nowMs - candidateSince < confirmationMs) return current;
            current = next; lastChange = nowMs; candidate = null; return current;
        }
        public void force(Band band, long nowMs) { current = band; candidate = null; lastChange = nowMs; }
        public void reset() { current = null; candidate = null; lastChange = 0L; }
        private boolean outsideHysteresis(float speedMs, Band next) {
            if (hysteresisKmh == 0f) return true;
            SpeedBand currentBand = find(current), nextBand = find(next);
            if (currentBand == null || nextBand == null) return true;
            float kmh = speedMs * KMH_PER_MS;
            return nextBand.minKmh > currentBand.minKmh
                    ? kmh >= nextBand.minKmh + hysteresisKmh
                    : kmh < currentBand.minKmh - hysteresisKmh;
        }
        private SpeedBand find(Band band) { for (SpeedBand item : bands) if (item.band == band) return item; return null; }
    }
    private static final class Sample { final float speedMs; final long timeMs; Sample(float s, long t) { speedMs = s; timeMs = t; } }
}
