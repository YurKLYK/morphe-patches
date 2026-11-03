package app.morphe.extension.youtube.music;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Generic priority resolver for speed, weather, time, terrain and future compound rules. */
public final class EnvironmentConditionResolver {
    public enum Source { MANUAL, SPEED, WEATHER, TIME, TERRAIN, DEFAULT }
    public static final class Candidate {
        public final Source source;
        public final String videoId;
        public Candidate(Source source, String videoId) { this.source = source; this.videoId = videoId; }
    }

    private final List<Source> priority = new ArrayList<>();
    private final Map<String, String> compoundRules = new java.util.HashMap<>();

    public EnvironmentConditionResolver() {
        Collections.addAll(priority, Source.MANUAL, Source.SPEED, Source.WEATHER, Source.TIME, Source.TERRAIN, Source.DEFAULT);
    }

    public synchronized void setPriority(List<Source> order) {
        if (order == null || order.size() != Source.values().length) throw new IllegalArgumentException("All sources must be ordered");
        java.util.HashSet<Source> unique = new java.util.HashSet<>(order);
        if (unique.size() != Source.values().length) throw new IllegalArgumentException("Duplicate source");
        priority.clear(); priority.addAll(order);
    }

    public synchronized List<Source> priority() { return new ArrayList<>(priority); }

    public synchronized void setCompoundRule(String conditionKey, String videoId) {
        if (conditionKey == null || conditionKey.trim().isEmpty()) return;
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) compoundRules.remove(conditionKey);
        else compoundRules.put(conditionKey, videoId);
    }

    public synchronized Candidate resolve(String conditionKey, Map<Source, String> candidates) {
        String compound = conditionKey == null ? null : compoundRules.get(conditionKey);
        if (compound != null) return new Candidate(Source.SPEED, compound);
        for (Source source : priority) {
            String id = candidates == null ? null : candidates.get(source);
            if (id != null && id.matches("[A-Za-z0-9_-]{11}")) return new Candidate(source, id);
        }
        return null;
    }
}
