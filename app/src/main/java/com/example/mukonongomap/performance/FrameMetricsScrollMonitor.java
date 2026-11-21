package com.example.mukonongomap.performance;

import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Choreographer;
import android.view.FrameMetrics;
import android.view.View;
import android.view.Window;

import androidx.annotation.RequiresApi;

/**
 * Records frame timings into {@link FrameStats} only while the manifest list is
 * actually scrolling, so idle frames don't dilute the jank percentage.
 *
 * <ul>
 *   <li>API 24+: {@link Window#addOnFrameMetricsAvailableListener} - full phase breakdown.</li>
 *   <li>API 23: {@link Choreographer} frame deltas - total duration only.</li>
 * </ul>
 *
 * Usage (side sheet shown):
 * <pre>
 * monitor = new FrameMetricsScrollMonitor(getWindow(), journey.frameStats());
 * monitor.attachTo(manifestScrollView);   // or call setScrolling() from a RecyclerView.OnScrollListener
 * ...
 * monitor.detach();                       // side sheet closed, before journey.onClosed()
 * </pre>
 */
public final class FrameMetricsScrollMonitor {

    private static final long IDLE_AFTER_MS = 150;

    private final Window window;
    private final FrameStats stats;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable markIdle = () -> setScrolling(false);

    private volatile boolean scrolling;
    private HandlerThread metricsThread;
    private Object frameMetricsListener; // Window.OnFrameMetricsAvailableListener on API 24+
    private Choreographer.FrameCallback choreographerCallback;
    private long lastFrameTimeNanos;
    private View attachedView;

    public FrameMetricsScrollMonitor(Window window, FrameStats stats) {
        this.window = window;
        this.stats = stats;
    }

    /**
     * Detects scrolling from {@link View#setOnScrollChangeListener}. Works for
     * ScrollView / NestedScrollView / RecyclerView. Note this replaces any existing
     * OnScrollChangeListener; for RecyclerView prefer calling {@link #setScrolling}
     * from {@code onScrollStateChanged(state != SCROLL_STATE_IDLE)}.
     */
    public void attachTo(View scrollingView) {
        attachedView = scrollingView;
        scrollingView.setOnScrollChangeListener((v, x, y, oldX, oldY) -> {
            setScrolling(true);
            main.removeCallbacks(markIdle);
            main.postDelayed(markIdle, IDLE_AFTER_MS);
        });
        start();
    }

    /** Manual scroll-state feed (RecyclerView). Call {@link #start()} first if not using attachTo. */
    public void setScrolling(boolean nowScrolling) {
        if (nowScrolling && !scrolling) {
            stats.onScrollSessionStarted();
            lastFrameTimeNanos = 0;
        }
        scrolling = nowScrolling;
    }

    public void start() {
        if (frameMetricsListener != null || choreographerCallback != null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            startFrameMetrics();
        } else {
            startChoreographer();
        }
    }

    public void detach() {
        main.removeCallbacks(markIdle);
        scrolling = false;
        if (attachedView != null) {
            attachedView.setOnScrollChangeListener(null);
            attachedView = null;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && frameMetricsListener != null) {
            try {
                window.removeOnFrameMetricsAvailableListener(
                        (Window.OnFrameMetricsAvailableListener) frameMetricsListener);
            } catch (IllegalArgumentException ignored) {
                // already removed
            }
            frameMetricsListener = null;
        }
        if (metricsThread != null) {
            metricsThread.quitSafely();
            metricsThread = null;
        }
        if (choreographerCallback != null) {
            Choreographer.getInstance().removeFrameCallback(choreographerCallback);
            choreographerCallback = null;
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    private void startFrameMetrics() {
        metricsThread = new HandlerThread("perf-frame-metrics");
        metricsThread.start();
        Window.OnFrameMetricsAvailableListener listener = (w, m, dropCount) -> {
            if (!scrolling) return;
            if (m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 1) return; // layout inflation frame
            stats.recordFrame(
                    m.getMetric(FrameMetrics.TOTAL_DURATION),
                    m.getMetric(FrameMetrics.INPUT_HANDLING_DURATION),
                    m.getMetric(FrameMetrics.ANIMATION_DURATION),
                    m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                    m.getMetric(FrameMetrics.DRAW_DURATION),
                    m.getMetric(FrameMetrics.SYNC_DURATION)
                            + m.getMetric(FrameMetrics.COMMAND_ISSUE_DURATION)
                            + m.getMetric(FrameMetrics.SWAP_BUFFERS_DURATION),
                    dropCount);
        };
        window.addOnFrameMetricsAvailableListener(listener, new Handler(metricsThread.getLooper()));
        frameMetricsListener = listener;
    }

    private void startChoreographer() {
        choreographerCallback = new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                if (scrolling) {
                    if (lastFrameTimeNanos != 0) stats.recordFrame(frameTimeNanos - lastFrameTimeNanos);
                    lastFrameTimeNanos = frameTimeNanos;
                } else {
                    lastFrameTimeNanos = 0;
                }
                if (choreographerCallback != null) Choreographer.getInstance().postFrameCallback(this);
            }
        };
        Choreographer.getInstance().postFrameCallback(choreographerCallback);
    }
}
