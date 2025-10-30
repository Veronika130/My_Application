package com.example.myapplication_2;

import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class RecordingAdapter extends RecyclerView.Adapter<RecordingAdapter.Holder> {

    public interface OnItemAction {
        void onPlayClicked(int position, View playButton, View pauseButton, Holder holder);
        void onPauseClicked(int position, View playButton, View pauseButton);
        void onSeekRequested(int position, int ms);
    }

    private final List<RecordingItem> data;
    private final OnItemAction action;

    public RecordingAdapter(List<RecordingItem> data, OnItemAction action) {
        this.data = data;
        this.action = action;
    }

    public RecordingItem getItem(int pos) { return data.get(pos); }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_recording, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        RecordingItem it = data.get(position);
        h.title.setText(it.name);

        long sec = it.durationMs / 1000L;
        h.duration.setText(DateUtils.formatElapsedTime(sec));

        long size = new File(it.path).length();
        h.size.setText(android.text.format.Formatter.formatShortFileSize(h.itemView.getContext(), size));

        h.playBtn.setOnClickListener(v -> action.onPlayClicked(h.getAdapterPosition(), h.playBtn, h.pauseBtn, h));
        h.pauseBtn.setOnClickListener(v -> action.onPauseClicked(h.getAdapterPosition(), h.playBtn, h.pauseBtn));

        h.seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            boolean fromUserDrag = false;
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {}
            @Override public void onStartTrackingTouch(SeekBar seekBar) { fromUserDrag = true; }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                if (fromUserDrag) {
                    action.onSeekRequested(h.getAdapterPosition(), seekBar.getProgress());
                }
                fromUserDrag = false;
            }
        });
    }

    @Override
    public int getItemCount() { return data.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        TextView title, duration, size;
        ImageButton playBtn, pauseBtn;
        SeekBar seekBar;

        Holder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.recTitle);
            duration = itemView.findViewById(R.id.recDuration);
            size = itemView.findViewById(R.id.recSize);
            playBtn = itemView.findViewById(R.id.btnPlay);
            pauseBtn = itemView.findViewById(R.id.btnPause);
            seekBar = itemView.findViewById(R.id.seek);
        }
    }
}
