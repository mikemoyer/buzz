package com.buzz.metronome;

import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

/** Fires a single haptic pulse. Silent: vibration only, no sound. */
final class Pulse {
    private Pulse() {}

    static Vibrator vibrator(Context c) {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager vm = c.getSystemService(VibratorManager.class);
            return vm != null ? vm.getDefaultVibrator() : null;
        }
        return (Vibrator) c.getSystemService(Context.VIBRATOR_SERVICE);
    }

    /**
     * @param ms        pulse length in milliseconds
     * @param intensity 1..255 vibration strength
     */
    static void buzz(Vibrator v, int ms, int intensity) {
        if (v == null || !v.hasVibrator()) return;

        VibrationEffect effect;
        if (v.hasAmplitudeControl()) {
            effect = VibrationEffect.createOneShot(ms, intensity);
        } else {
            // Phones without strength control: approximate "intensity" with pulse length.
            int scaled = Math.max(12, ms * intensity / 255);
            effect = VibrationEffect.createOneShot(scaled, VibrationEffect.DEFAULT_AMPLITUDE);
        }
        play(v, effect);
    }

    /** Three long buzzes, clearly different from a beat: "time's up". Lasts about 1.4 s. */
    static void endSignal(Vibrator v, int intensity) {
        if (v == null || !v.hasVibrator()) return;
        long[] timings = {0, 300, 150, 300, 150, 500};
        VibrationEffect effect;
        if (v.hasAmplitudeControl()) {
            int a = Math.max(intensity, 120);
            effect = VibrationEffect.createWaveform(timings, new int[]{0, a, 0, a, 0, a}, -1);
        } else {
            effect = VibrationEffect.createWaveform(timings, -1);
        }
        play(v, effect);
    }

    private static void play(Vibrator v, VibrationEffect effect) {
        // "Alarm" usage so Android keeps delivering pulses while the screen is off.
        if (Build.VERSION.SDK_INT >= 33) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM));
        } else {
            v.vibrate(effect, new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build());
        }
    }
}
