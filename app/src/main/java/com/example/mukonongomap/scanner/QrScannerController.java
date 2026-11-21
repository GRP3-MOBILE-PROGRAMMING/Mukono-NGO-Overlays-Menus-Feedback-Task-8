package com.example.mukonongomap.scanner;

import android.media.Image;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.View;

import androidx.activity.ComponentActivity;
import androidx.annotation.OptIn;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.example.mukonongomap.performance.QrScanJourney;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Real QR scanning: CameraX preview + ML Kit barcode decoding (on-device, works offline).
 *
 * Reports to QrScanJourney:
 * <ul>
 *   <li>onCameraOpenRequested: just before the camera provider is requested</li>
 *   <li>onCameraPreviewStarted: PreviewView reports STREAMING (first frames on screen)</li>
 *   <li>onFrameAnalyzed(ms): each frame ML Kit processes (CPU cost per frame)</li>
 *   <li>onQrDecoded: first frame that contains a QR code</li>
 * </ul>
 */
public final class QrScannerController {

    public interface Listener {
        void onQrDecoded(String rawValue);

        void onScannerError(String message);
    }

    private final ComponentActivity activity;
    private final PreviewView previewView;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final BarcodeScanner barcodeScanner = BarcodeScanning.getClient(
            new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());

    private ProcessCameraProvider provider;
    private QrScanJourney journey;
    private volatile boolean running;
    private volatile boolean decoded;

    public QrScannerController(ComponentActivity activity, PreviewView previewView, Listener listener) {
        this.activity = activity;
        this.previewView = previewView;
        this.listener = listener;
        previewView.getPreviewStreamState().observe(activity, state -> {
            if (running && state == PreviewView.StreamState.STREAMING && journey != null) {
                journey.onCameraPreviewStarted();
            }
        });
    }

    public boolean isRunning() {
        return running;
    }

    /** Opens the camera and starts decoding. Camera permission must already be granted. */
    public void start(QrScanJourney scanJourney) {
        if (running) return;
        journey = scanJourney;
        running = true;
        decoded = false;
        previewView.setVisibility(View.VISIBLE);

        journey.onCameraOpenRequested();
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(activity);
        future.addListener(() -> {
            try {
                provider = future.get();
                bind();
            } catch (Exception e) {
                stop();
                listener.onScannerError("Camera could not start: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(activity));
    }

    private void bind() {
        if (!running || provider == null) return;

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        ImageAnalysis analysis = new ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();
        analysis.setAnalyzer(analysisExecutor, this::analyze);

        CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
        try {
            if (!provider.hasCamera(selector)) selector = CameraSelector.DEFAULT_FRONT_CAMERA;
        } catch (Exception ignored) {
            // keep the back camera
        }

        provider.unbindAll();
        provider.bindToLifecycle(activity, selector, preview, analysis);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyze(ImageProxy image) {
        Image media = image.getImage();
        if (!running || decoded || media == null) {
            image.close();
            return;
        }
        long started = SystemClock.elapsedRealtime();
        InputImage input = InputImage.fromMediaImage(media, image.getImageInfo().getRotationDegrees());
        barcodeScanner.process(input).addOnCompleteListener(task -> {
            QrScanJourney j = journey;
            if (j != null) j.onFrameAnalyzed(SystemClock.elapsedRealtime() - started);
            String value = task.isSuccessful() ? firstValue(task.getResult()) : null;
            image.close();
            if (value != null && running && !decoded) {
                decoded = true;
                if (j != null) j.onQrDecoded();
                main.post(() -> {
                    stop();
                    listener.onQrDecoded(value);
                });
            }
        });
    }

    private static String firstValue(List<Barcode> codes) {
        if (codes == null) return null;
        for (Barcode b : codes) {
            if (b.getRawValue() != null) return b.getRawValue();
        }
        return null;
    }

    /** Stops the camera (the journey is finished by the caller). */
    public void stop() {
        running = false;
        if (provider != null) provider.unbindAll();
        previewView.setVisibility(View.GONE);
    }

    public void shutdown() {
        stop();
        analysisExecutor.shutdown();
        barcodeScanner.close();
    }
}
