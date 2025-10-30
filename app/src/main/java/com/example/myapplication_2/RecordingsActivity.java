package com.example.myapplication_2;

import android.content.Intent;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class RecordingsActivity extends AppCompatActivity implements RecordingAdapter.OnItemAction {
    private RecyclerView recyclerView;
    private RecordingAdapter adapter;
    private MediaPlayer mediaPlayer;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private Runnable tick;
    private int playingPosition = RecyclerView.NO_POSITION;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recordings);

        recyclerView = findViewById(R.id.recList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        mediaPlayer = new MediaPlayer();

        adapter = new RecordingAdapter(loadM4aFiles(), this);
        recyclerView.setAdapter(adapter);

        findViewById(R.id.backBtn).setOnClickListener(v -> finish());
    }

    private List<RecordingItem> loadM4aFiles() {
        List<RecordingItem> items = new ArrayList<>();

        File extMusic = getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        File internal = getFilesDir();

        scanDirForM4a(extMusic, items);
        scanDirForM4a(internal, items);

        return items;
    }

    private void scanDirForM4a(File dir, List<RecordingItem> out) {
        if (dir == null || !dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                scanDirForM4a(f, out);
            } else if (f.getName().toLowerCase().endsWith(".m4a")) {
                long dur = readDurationMs(f);
                out.add(new RecordingItem(f.getName(), f.getAbsolutePath(), dur));
            }
        }
    }

    private long readDurationMs(File f) {
        try {
            MediaMetadataRetriever mmr = new MediaMetadataRetriever();
            mmr.setDataSource(f.getAbsolutePath());
            String d = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            mmr.release();
            return d == null ? 0 : Long.parseLong(d);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public void onPlayClicked(int position, View playButton, View pauseButton, RecordingAdapter.Holder holder) {
        RecordingItem item = adapter.getItem(position);

        try {
            if (mediaPlayer.isPlaying() || playingPosition != RecyclerView.NO_POSITION) {
                stopPlayback();
            }

            mediaPlayer.reset();
            mediaPlayer.setDataSource(item.path);
            mediaPlayer.prepare();
            mediaPlayer.start();

            playingPosition = position;
            holder.seekBar.setMax(mediaPlayer.getDuration());
            toggle(playButton, pauseButton, true);

            tick = new Runnable() {
                @Override public void run() {
                    if (mediaPlayer == null) return;
                    if (mediaPlayer.isPlaying()) {
                        holder.seekBar.setProgress(mediaPlayer.getCurrentPosition());
                        ui.postDelayed(this, 500);
                    }
                }
            };
            ui.post(tick);

            mediaPlayer.setOnCompletionListener(mp -> {
                holder.seekBar.setProgress(0);
                toggle(playButton, pauseButton, false);
                playingPosition = RecyclerView.NO_POSITION;
                ui.removeCallbacks(tick);
            });

        } catch (Exception e) {
            Toast.makeText(this, "Не вдалося відтворити файл", Toast.LENGTH_SHORT).show();
            toggle(playButton, pauseButton, false);
        }
    }

    @Override
    public void onPauseClicked(int position, View playButton, View pauseButton) {
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            toggle(playButton, pauseButton, false);
        }
    }

    @Override
    public void onSeekRequested(int position, int ms) {
        if (position == playingPosition && mediaPlayer != null) {
            mediaPlayer.seekTo(ms);
        }
    }

    private void toggle(View play, View pause, boolean playing) {
        play.setVisibility(playing ? View.GONE : View.VISIBLE);
        pause.setVisibility(playing ? View.VISIBLE : View.GONE);
    }

    private void stopPlayback() {
        try {
            if (mediaPlayer.isPlaying()) mediaPlayer.stop();
        } catch (Exception ignored) {}
        ui.removeCallbacks(tick);
        int old = playingPosition;
        playingPosition = RecyclerView.NO_POSITION;
        if (old != RecyclerView.NO_POSITION) {
            RecyclerView.ViewHolder vh = recyclerView.findViewHolderForAdapterPosition(old);
            if (vh instanceof RecordingAdapter.Holder) {
                RecordingAdapter.Holder h = (RecordingAdapter.Holder) vh;
                h.seekBar.setProgress(0);
                h.playBtn.setVisibility(View.VISIBLE);
                h.pauseBtn.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPlayback();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }
}
