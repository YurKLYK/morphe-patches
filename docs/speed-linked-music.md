# Speed-linked music patch

This patch is opt-in. It keeps speed samples in memory only and uses the
Android `Location` speed in m/s. If `Location.hasSpeed()` is false, it derives
speed from the distance and timestamp of the previous valid location.

## Configuration

Enable `Morphe settings > Miscellaneous > Speed-linked music` and grant
foreground location permission while YouTube is visible. The default bands are
`[0,2)`, `[2,7)`, `[7,15)`, `[15,30)`, `[30,80)`, and `[80, infinity)` km/h.

The band field accepts one band per line:

```text
WALKING|徒歩|2|7=VIDEO_ID_1,VIDEO_ID_2
```

The six band names are `STOPPED`, `WALKING`, `RUNNING`, `MEDIUM`, `FAST`, and
`VERY_FAST`. Video IDs must be the normal 11-character YouTube IDs.

The default smoothing window is 20 seconds, state confirmation is 10 seconds,
and automatic switch cooldown is 60 seconds. Smoothing is clamped to 15–30
seconds. A negative developer simulated speed disables simulation; a nonnegative
value is interpreted as m/s and is sent only to the classifier.

## Build and apply

From the `morphe-patches` directory:

```powershell
./gradlew :extensions:youtube:compileReleaseJavaWithJavac
./gradlew :patches:build :patches:generatePatchesList
```

Use the generated patch bundle with the official Morphe Manager. Patch a clean,
compatible YouTube APK, select `Speed-linked music mode`, and install the
resulting APK through the normal Morphe flow. Do not patch an already patched
APK.

The implementation uses Morphe's existing in-process watch intent bridge
(`LoadVideoPatch.deliverVideoIntentInProcess`) and does not alter normal
YouTube playback when the setting is disabled.

## Background limits

When enabled, the patch requests a location Foreground Service and shows an
ongoing notification. Android may still deny location if the user revokes the
permission, disables location services, or the device restricts the app. The
patch does not silently request background location or bypass Android policy.
