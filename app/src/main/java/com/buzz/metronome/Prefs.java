package com.buzz.metronome;

import android.content.Context;
import android.content.SharedPreferences;

/** Saved settings, shared by the screen and the background service. */
final class Prefs {
    static final String KEY_BPM = "bpm";
    static final String KEY_INTENSITY = "intensity";
    static final String KEY_PULSE = "pulse_ms";
    static final String KEY_TIMER = "timer_min";

    static final int MIN_BPM = 30, MAX_BPM = 220, DEF_BPM = 90;
    static final int MIN_INTENSITY = 1, MAX_INTENSITY = 255, DEF_INTENSITY = 170;
    static final int MIN_PULSE = 20, MAX_PULSE = 300, DEF_PULSE = 60;
    /** Auto-stop timer in minutes; 0 means off (run until stopped). */
    static final int MIN_TIMER = 0, MAX_TIMER = 60, DEF_TIMER = 0;

    private Prefs() {}

    static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE);
    }

    static int bpm(SharedPreferences p) {
        return clamp(p.getInt(KEY_BPM, DEF_BPM), MIN_BPM, MAX_BPM);
    }

    static int intensity(SharedPreferences p) {
        return clamp(p.getInt(KEY_INTENSITY, DEF_INTENSITY), MIN_INTENSITY, MAX_INTENSITY);
    }

    static int pulseMs(SharedPreferences p) {
        return clamp(p.getInt(KEY_PULSE, DEF_PULSE), MIN_PULSE, MAX_PULSE);
    }

    static int timerMin(SharedPreferences p) {
        return clamp(p.getInt(KEY_TIMER, DEF_TIMER), MIN_TIMER, MAX_TIMER);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
