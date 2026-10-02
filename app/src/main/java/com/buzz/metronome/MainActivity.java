package com.buzz.metronome;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.Vibrator;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity implements MetronomeService.BeatListener {

    private SharedPreferences prefs;
    private Vibrator vibrator;

    private TextView bpmText, intensityText, pulseText, timerText, remainingText;
    private SeekBar bpmBar, intensityBar, pulseBar, timerBar;
    private Button startStop;
    private View beatDot;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable countdown = new Runnable() {
        @Override public void run() {
            updateRemaining();
            ui.postDelayed(this, 500);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = Prefs.get(this);
        vibrator = Pulse.vibrator(this);

        bpmText = findViewById(R.id.bpm_value);
        intensityText = findViewById(R.id.intensity_value);
        pulseText = findViewById(R.id.pulse_value);
        timerText = findViewById(R.id.timer_value);
        remainingText = findViewById(R.id.remaining);
        bpmBar = findViewById(R.id.bpm_bar);
        intensityBar = findViewById(R.id.intensity_bar);
        pulseBar = findViewById(R.id.pulse_bar);
        timerBar = findViewById(R.id.timer_bar);
        startStop = findViewById(R.id.start_stop);
        beatDot = findViewById(R.id.beat_dot);

        setupBar(bpmBar, Prefs.MIN_BPM, Prefs.MAX_BPM, Prefs.bpm(prefs), Prefs.KEY_BPM);
        setupBar(intensityBar, Prefs.MIN_INTENSITY, Prefs.MAX_INTENSITY, Prefs.intensity(prefs), Prefs.KEY_INTENSITY);
        setupBar(pulseBar, Prefs.MIN_PULSE, Prefs.MAX_PULSE, Prefs.pulseMs(prefs), Prefs.KEY_PULSE);
        setupBar(timerBar, Prefs.MIN_TIMER, Prefs.MAX_TIMER, Prefs.timerMin(prefs), Prefs.KEY_TIMER);

        int[] timerIds = {R.id.timer_off, R.id.timer_5, R.id.timer_10, R.id.timer_20};
        int[] timerMins = {0, 5, 10, 20};
        for (int i = 0; i < timerIds.length; i++) {
            final int value = timerMins[i];
            findViewById(timerIds[i]).setOnClickListener(v -> timerBar.setProgress(value));
        }

        findViewById(R.id.bpm_minus).setOnClickListener(v -> bpmBar.setProgress(bpmBar.getProgress() - 1));
        findViewById(R.id.bpm_plus).setOnClickListener(v -> bpmBar.setProgress(bpmBar.getProgress() + 1));

        int[] presetIds = {R.id.preset_60, R.id.preset_80, R.id.preset_100, R.id.preset_120};
        int[] presetBpm = {60, 80, 100, 120};
        for (int i = 0; i < presetIds.length; i++) {
            final int value = presetBpm[i];
            findViewById(presetIds[i]).setOnClickListener(v -> bpmBar.setProgress(value));
        }

        findViewById(R.id.test_pulse).setOnClickListener(v ->
                Pulse.buzz(vibrator, Prefs.pulseMs(prefs), Prefs.intensity(prefs)));

        startStop.setOnClickListener(v -> toggle());

        TextView note = findViewById(R.id.amplitude_note);
        boolean amp = vibrator != null && vibrator.hasAmplitudeControl();
        note.setVisibility(amp ? View.GONE : View.VISIBLE);

        updateLabels();
    }

    @Override protected void onStart() {
        super.onStart();
        MetronomeService.setBeatListener(this);
        refreshButton();
        ui.post(countdown);
    }

    @Override protected void onStop() {
        ui.removeCallbacks(countdown);
        MetronomeService.setBeatListener(null);
        super.onStop();
    }

    private void updateRemaining() {
        long end = MetronomeService.endsAt();
        if (!MetronomeService.isRunning() || end <= 0) {
            remainingText.setVisibility(View.GONE);
            return;
        }
        long secs = Math.max(0, (end - SystemClock.elapsedRealtime() + 999) / 1000);
        remainingText.setText(getString(R.string.remaining,
                String.format(Locale.US, "%d:%02d", secs / 60, secs % 60)));
        remainingText.setVisibility(View.VISIBLE);
    }

    private void setupBar(SeekBar bar, int min, int max, int value, String key) {
        bar.setMin(min);
        bar.setMax(max);
        bar.setProgress(value);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int progress, boolean fromUser) {
                prefs.edit().putInt(key, progress).apply(); // a running service picks this up live
                updateLabels();
            }
            @Override public void onStartTrackingTouch(SeekBar sb) {}
            @Override public void onStopTrackingTouch(SeekBar sb) {}
        });
    }

    private void updateLabels() {
        bpmText.setText(String.valueOf(bpmBar.getProgress()));
        intensityText.setText(getString(R.string.percent, Math.round(intensityBar.getProgress() * 100f / 255f)));
        pulseText.setText(getString(R.string.millis, pulseBar.getProgress()));
        int t = timerBar.getProgress();
        timerText.setText(t == 0 ? getString(R.string.timer_off) : getString(R.string.minutes, t));
    }

    private void toggle() {
        Intent i = new Intent(this, MetronomeService.class);
        if (MetronomeService.isRunning()) {
            stopService(i);
        } else {
            if (Build.VERSION.SDK_INT >= 33
                    && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                // Needed so the Stop control shows on the lock screen. The metronome runs either way.
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
            startForegroundService(i);
        }
    }

    private void refreshButton() {
        boolean on = MetronomeService.isRunning();
        startStop.setText(on ? R.string.stop : R.string.start);
        startStop.setActivated(on);
        if (!on) beatDot.setAlpha(0.2f);
    }

    @Override public void onBeat() {
        beatDot.animate().cancel();
        beatDot.setAlpha(1f);
        beatDot.animate().alpha(0.2f).setDuration(180).start();
    }

    @Override public void onStateChanged(boolean running) {
        refreshButton();
    }
}
