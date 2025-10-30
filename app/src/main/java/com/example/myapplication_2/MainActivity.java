package com.example.myapplication_2;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_AUDIO = 2001;
    private static final int REQ_NOTIF = 2002;

    private TextView tvTimer;
    private long startedAt = 0L;
    private boolean running = false;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (running && startedAt > 0) {
                long sec = (System.currentTimeMillis() - startedAt) / 1000;
                long mm = sec / 60;
                long ss = sec % 60;
                tvTimer.setText(getString(R.string.timer_format, mm, ss));
                handler.postDelayed(this, 1000);
            }
        }
    };

    private final BroadcastReceiver stateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (MediaRecorder.BROADCAST.equals(intent.getAction())) {
                String state = intent.getStringExtra("state");
                if ("STARTED".equals(state)) {
                    startedAt = intent.getLongExtra("startedAt", System.currentTimeMillis());
                    running = true;
                    handler.removeCallbacks(tick);
                    handler.post(tick);
                } else if ("STOPPED".equals(state)) {
                    running = false;
                    handler.removeCallbacks(tick);
                    tvTimer.setText(getString(R.string.timer_format, 0, 0));
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvTimer = findViewById(R.id.tvTimer);
        Button btnStart = findViewById(R.id.btnStart);
        Button btnStop = findViewById(R.id.btnStop);
        Button btnSettings = findViewById(R.id.btnSettings);
        Button btnMyRecordings = findViewById(R.id.btnMyRecordings);

        btnStart.setOnClickListener(v -> {
            if (checkPerms()) {
                Intent i = new Intent(this, MediaRecorder.class)
                        .setAction(MediaRecorder.ACTION_START);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    ContextCompat.startForegroundService(this, i);
                } else {
                    startService(i);
                }
            }
        });

        btnStop.setOnClickListener(v -> {
            Intent i = new Intent(this, MediaRecorder.class)
                    .setAction(MediaRecorder.ACTION_STOP);
            startService(i);
        });
        btnSettings.setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        btnMyRecordings.setOnClickListener(v ->
                startActivity(new Intent(this, RecordingsActivity.class)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(MediaRecorder.BROADCAST);
        ContextCompat.registerReceiver(
                this,
                stateReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );
    }

    @Override
    protected void onStop() {
        super.onStop();
        try {
            unregisterReceiver(stateReceiver);
        } catch (Exception ignored) {
        }
        handler.removeCallbacks(tick);
    }

    private boolean checkPerms() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQ_AUDIO
            );
            return false;
        }
        if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIF
            );
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (checkPerms()) {
            Intent i = new Intent(this, MediaRecorder.class)
                    .setAction(MediaRecorder.ACTION_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(this, i);
            } else {
                startService(i);
            }
        }
    }
}
