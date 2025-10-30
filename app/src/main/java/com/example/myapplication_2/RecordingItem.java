package com.example.myapplication_2;

public class RecordingItem {
    public final String name;
    public final String path;
    public final long durationMs;

    public RecordingItem(String name, String path, long durationMs) {
        this.name = name;
        this.path = path;
        this.durationMs = durationMs;
    }
}
