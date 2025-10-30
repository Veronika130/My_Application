package com.example.myapplication_2;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MediaRecorder extends Service {

    private static final String TAG = "REC_SVC";
    private static final String CH_ID = "recorder";
    private static final int NOTIF_ID = 1001;

    public static final String ACTION_START = "ACTION_START";
    public static final String ACTION_STOP  = "ACTION_STOP";
    public static final String BROADCAST   = "com.example.myapplication_2.STATE";

    private android.media.MediaRecorder recorder;
    private ParcelFileDescriptor pfd;
    private long startedAt;

    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        final String act = intent != null ? intent.getAction() : null;
        Log.d(TAG, "onStartCommand action=" + act);

        if (ACTION_START.equals(act)) {
            startForeground(NOTIF_ID, buildNotification());
            startRecording();
        } else if (ACTION_STOP.equals(act)) {
            stopRecording();
        }
        return START_NOT_STICKY;
    }

    private void startRecording() {
        if (recorder != null) return;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        String fileName = "REC_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(new Date()) + ".m4a";
        String treeStr = sp.getString(SettingsActivity.KEY_TREE_URI, null);
        String outPath = null;
        Uri safFileUri = null;

        try {
            if (treeStr != null) {
                Uri tree = Uri.parse(treeStr);
                DocumentFile dir = DocumentFile.fromTreeUri(this, tree);
                if (dir != null && dir.canWrite()) {
                    DocumentFile out = dir.createFile("audio/mp4", fileName);
                    if (out != null) {
                        pfd = getContentResolver().openFileDescriptor(out.getUri(), "w");
                        safFileUri = out.getUri();
                        Log.d(TAG, "Output: SAF " + safFileUri);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "SAF output failed, fallback to internal", e);
            closeQuietlyPfd();
        }

        if (pfd == null) {
            File base = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
            if (base != null && !base.exists()) base.mkdirs();
            File out = new File(base, fileName);
            outPath = out.getAbsolutePath();
            Log.d(TAG, "Output: internal " + outPath);
        }
        int maxMin = 10;
        try {
            String v = sp.getString(SettingsActivity.KEY_MAX_MINUTES, "10");
            maxMin = Math.max(1, Integer.parseInt(v));
        } catch (Exception ignored) {}
        int[] sources = new int[] {
                android.media.MediaRecorder.AudioSource.MIC,
                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                android.media.MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                android.media.MediaRecorder.AudioSource.DEFAULT
        };
        int[][] params = new int[][] {
                {44100, 128_000}, {16000, 64_000}, {8000, 32_000}
        };

        Exception last = null;
        for (int src : sources) {
            for (int[] p : params) {
                try {
                    recorder = new android.media.MediaRecorder();
                    recorder.setAudioSource(src);
                    recorder.setOutputFormat(android.media.MediaRecorder.OutputFormat.MPEG_4);
                    recorder.setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AAC);
                    recorder.setAudioChannels(1);
                    recorder.setAudioSamplingRate(p[0]);
                    recorder.setAudioEncodingBitRate(p[1]);

                    if (pfd != null) {
                        recorder.setOutputFile(pfd.getFileDescriptor());
                    } else {
                        recorder.setOutputFile(outPath);
                    }

                    recorder.setMaxDuration(maxMin * 60 * 1000);
                    recorder.setOnInfoListener((mr, what, extra) -> {
                        if (what == android.media.MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) {
                            stopRecording();
                        }
                    });

                    recorder.prepare();
                    recorder.start();
                    startedAt = System.currentTimeMillis();
                    Log.d(TAG, "Started: src=" + src + " sr=" + p[0] + " br=" + p[1] +
                            (safFileUri != null ? (" -> " + safFileUri) : (" -> " + outPath)));
                    sendState("STARTED");
                    return;
                } catch (Exception e) {
                    last = e;
                    Log.w(TAG, "Start fail src=" + src + " sr=" + p[0] + " br=" + p[1], e);
                    safeRelease();
                }
            }
        }

        Log.e(TAG, "All MediaRecorder attempts failed", last);
        stopForeground(true);
        releaseAll();
        stopSelf();
    }

    private void stopRecording() {
        try {
            if (recorder != null) {
                recorder.stop();
                Log.d(TAG, "Stopped");
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "stopRecording RuntimeException", e);
        } finally {
            releaseAll();
            stopForeground(true);
            stopSelf();
            sendState("STOPPED");
        }
    }

    private void safeRelease() {
        try { if (recorder != null) recorder.reset(); } catch (Exception ignored) {}
        try { if (recorder != null) recorder.release(); } catch (Exception ignored) {}
        recorder = null;
    }

    private void closeQuietlyPfd() {
        if (pfd != null) {
            try { pfd.close(); } catch (IOException ignored) {}
            pfd = null;
        }
    }

    private void releaseAll() {
        safeRelease();
        closeQuietlyPfd();
    }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "Recording Channel", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Постійне сповіщення диктофона");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }

        PendingIntent pOpen = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE
        );

        PendingIntent pStop = PendingIntent.getService(
                this, 1,
                new Intent(this, MediaRecorder.class).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(android.R.drawable.presence_audio_online)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_subtitle))
                .setContentIntent(pOpen)
                .addAction(android.R.drawable.ic_media_pause, getString(R.string.action_stop), pStop)
                .setOngoing(true)
                .build();
    }

    private void sendState(String state) {
        Intent i = new Intent(BROADCAST);
        i.putExtra("state", state);
        i.putExtra("startedAt", startedAt);
        i.setPackage(getPackageName());
        sendBroadcast(i);
        Log.d(TAG, "sendState: " + state);
    }
}
