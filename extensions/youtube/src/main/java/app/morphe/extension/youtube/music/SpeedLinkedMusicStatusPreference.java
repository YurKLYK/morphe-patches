package app.morphe.extension.youtube.music;

import android.content.Context;
import android.preference.Preference;
import android.util.AttributeSet;

@SuppressWarnings("deprecation")
public final class SpeedLinkedMusicStatusPreference extends Preference {
    public SpeedLinkedMusicStatusPreference(Context context, AttributeSet attrs) { super(context, attrs); }

    @Override protected void onBindView(android.view.View view) {
        super.onBindView(view);
        SpeedLinkedMusicController controller = SpeedLinkedMusicRuntime.controller();
        if (controller == null || controller.currentSpeedMs() == null) {
            setSummary("速度不明");
            return;
        }
        float kmh = controller.currentSpeedMs() * SpeedLinkedMusic.KMH_PER_MS;
        setSummary(String.format(java.util.Locale.ROOT, "%.1f km/h · %s", kmh,
                SpeedLinkedMusic.displayName(controller.currentBand())));
    }
}
