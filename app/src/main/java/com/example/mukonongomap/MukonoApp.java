package com.example.mukonongomap;

import android.app.Application;

import com.example.mukonongomap.performance.PerfMonitor;

/** Starts Firebase Performance Monitoring before any activity is created. */
public class MukonoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        PerfMonitor.init(this, BuildConfig.PERF_COLLECTION_ENABLED);
    }
}
