package app.morphe.extension.youtube.music;

import android.app.AlertDialog;
import android.content.Context;
import android.util.AttributeSet;
import android.preference.Preference;

import app.morphe.extension.shared.Utils;
import app.morphe.extension.youtube.patches.VideoInformation;

@SuppressWarnings("deprecation")
public final class SpeedLinkedMusicRegisterPreference extends Preference {
    public SpeedLinkedMusicRegisterPreference(Context context, AttributeSet attrs) { super(context, attrs); }
    @Override protected void onClick() {
        String videoId = VideoInformation.getVideoId();
        if (videoId == null || videoId.isEmpty()) {
            Utils.showSnackbarShort("No video is currently playing");
            return;
        }
        String[] labels = {"停止", "徒歩", "ランニング", "中速移動", "高速移動", "超高速移動"};
        SpeedLinkedMusic.Band[] bands = SpeedLinkedMusic.Band.values();
        new AlertDialog.Builder(getContext())
                .setTitle("Register current video")
                .setItems(labels, (dialog, which) -> SpeedLinkedMusicRuntime.registerVideo(bands[which], videoId))
                .show();
    }
}
