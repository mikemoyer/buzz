package com.buzz.metronome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.Vibrator;

/**
 * Runs the metronome as a foreground service so it keeps pulsing when the
 * phone is locked. A partial wake lock keeps the CPU awake; beats are
 * scheduled against an absolute clock so the tempo doesn't drift.
 */
public class MetronomeService extends Service {

    public static final String ACTION_STOP = "com.buzz.metronome.STOP";

    private static final String CHANNEL_ID = "metronome";
    private static final int NOTIF_ID = 1;
    private static final long WAKE_LOCK_MAX_MS = 4L * 60 * 60 * 1000; // safety cap: 4 hours

    /** Lets the on-screen beat indicator flash in sync. */
    public interface BeatListener {
        void onBeat();
        void onStateChanged(boolean running);
    }

    private static volatile BeatListener listener;
    private static volatile boolean running;
    /** When the auto-stop timer ends, in SystemClock.elapsedRealtime() terms; 0 = no timer. */
    private static volatile long endsAt;

    public static void setBeatListener(BeatListener l) { listener = l; }
    public static boolean isRunning() { return running; }
    public static long endsAt() { return endsAt; }

    private final Handler main = new Handler(Looper.getMainLooper());
    private HandlerThread thread;
    private Handler handler;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences prefs;

    private volatile int bpm, intensity, pulseMs, timerMin;
    private long nextBeatAt;
    private long startedAt;

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            long interval = 60000L / bpm;
            // Keep each pulse comfortably shorter than the gap between beats.
            int len = (int) Math.min(pulseMs, interval * 6 / 10);
            Pulse.buzz(vibrator, len, intensity);

            BeatListener l = listener;
            if (l != null) main.post(l::onBeat);

            nextBeatAt += interval;
            long now = SystemClock.uptimeMillis();
            if (nextBeatAt < now) nextBeatAt = now; // fell behind; resync instead of bursting
            handler.postAtTime(this, nextBeatAt);
        }
    };

    /** Timer ran out: stop beating, give the "time's up" signal, then shut down. */
    private final Runnable finish = new Runnable() {
        @Override public void run() {
            handler.removeCallbacks(tick);
            Pulse.endSignal(vibrator, intensity);
            handler.postDelayed(MetronomeService.this::stopSelf, 1600);
        }
    };

    private final Runnable refreshNotification = () -> {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null && running) nm.notify(NOTIF_ID, buildNotification());
    };

    private final SharedPreferences.OnSharedPreferenceChangeListener prefListener = (p, key) -> {
        int oldBpm = bpm, oldTimer = timerMin;
        readSettings();
        if (oldTimer != timerMin) {
            // Timer changed mid-run: measure the new length from when this session started.
            computeEnd();
            scheduleFinish();
        }
        if (oldBpm != bpm || oldTimer != timerMin) {
            main.removeCallbacks(refreshNotification);
            main.postDelayed(refreshNotification, 400);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        prefs = Prefs.get(this);
        readSettings();
        vibrator = Pulse.vibrator(this);
        thread = new HandlerThread("metronome", android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
        thread.start();
        handler = new Handler(thread.getLooper());
        createChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!running) {
            startedAt = SystemClock.elapsedRealtime();
            computeEnd();
        }

        Notification n = buildNotification();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }

        if (!running) {
            running = true;
            PowerManager pm = getSystemService(PowerManager.class);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Buzz:metronome");
            wakeLock.setReferenceCounted(false);
            wakeLock.acquire(WAKE_LOCK_MAX_MS);

            prefs.registerOnSharedPreferenceChangeListener(prefListener);
            nextBeatAt = SystemClock.uptimeMillis();
            handler.postAtTime(tick, nextBeatAt);
            scheduleFinish();
            notifyState();
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        running = false;
        endsAt = 0;
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener);
        handler.removeCallbacksAndMessages(null);
        main.removeCallbacks(refreshNotification);
        thread.quitSafely();
        if (vibrator != null) vibrator.cancel();
        if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        notifyState();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void readSettings() {
        bpm = Prefs.bpm(prefs);
        intensity = Prefs.intensity(prefs);
        pulseMs = Prefs.pulseMs(prefs);
        timerMin = Prefs.timerMin(prefs);
    }

    private void computeEnd() {
        endsAt = timerMin > 0 ? startedAt + timerMin * 60_000L : 0;
    }

    private void scheduleFinish() {
        handler.removeCallbacks(finish);
        long end = endsAt;
        if (end > 0) {
            // The wake lock keeps the CPU awake, so the handler clock tracks real time.
            handler.postDelayed(finish, Math.max(0, end - SystemClock.elapsedRealtime()));
        }
    }

    private void notifyState() {
        main.post(() -> {
            BeatListener l = listener;
            if (l != null) l.onStateChanged(running);
        });
    }

    private void createChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW);
        ch.setDescription(getString(R.string.channel_desc));
        ch.setSound(null, null);
        ch.enableVibration(false);
        ch.setShowBadge(false);
        ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification() {
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        PendingIntent stop = PendingIntent.getService(this, 1,
                new Intent(this, MetronomeService.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder b = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_pulse)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text, bpm))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open)
                .addAction(new Notification.Action.Builder(
                        Icon.createWithResource(this, R.drawable.ic_stop),
                        getString(R.string.stop), stop).build());
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        long end = endsAt;
        if (end > 0) {
            // Live countdown on the lock screen, e.g. "12:34".
            b.setContentText(getString(R.string.notif_text_timer, bpm))
                    .setWhen(System.currentTimeMillis() + (end - SystemClock.elapsedRealtime()))
                    .setShowWhen(true)
                    .setUsesChronometer(true)
                    .setChronometerCountDown(true);
        }
        return b.build();
    }
}
