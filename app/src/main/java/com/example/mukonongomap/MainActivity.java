package com.example.mukonongomap;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mukonongomap.data.Beneficiary;
import com.example.mukonongomap.data.DeliveryRepository;
import com.example.mukonongomap.data.FieldDataSource;
import com.example.mukonongomap.data.ManifestItem;
import com.example.mukonongomap.performance.ConfirmDeliveryJourney;
import com.example.mukonongomap.performance.FrameMetricsScrollMonitor;
import com.example.mukonongomap.performance.ManifestSideSheetJourney;
import com.example.mukonongomap.performance.PerfMonitor;
import com.example.mukonongomap.performance.QrScanJourney;
import com.example.mukonongomap.performance.ReportIssueSheetJourney;
import com.example.mukonongomap.scanner.QrScannerController;
import com.example.mukonongomap.ui.ManifestAdapter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Phone: volunteer QR scanner, beneficiary profile sheet, Report Issue sheet.
 * Tablet (sw600dp landscape): warehouse map with the delivery manifest side sheet.
 *
 * Every critical journey is measured with Firebase Performance Monitoring
 * (see the performance package):
 *   qr_scan_to_profile (+ camera init, decode, fetch, sheet render)
 *   confirm_delivery
 *   report_issue_sheet_transition
 *   side_sheet_manifest_load / side_sheet_manifest_scroll
 */
public class MainActivity extends ComponentActivity implements QrScannerController.Listener {

    private FieldDataSource fieldData;
    private DeliveryRepository deliveries;

    // phone
    private QrScannerController scanner;
    private QrScanJourney currentScan;
    private ReportIssueSheetJourney currentIssue;
    private TextView scanStatus;
    private TextView scanHint;

    // tablet
    private ManifestSideSheetJourney manifestJourney;
    private FrameMetricsScrollMonitor scrollMonitor;
    private ManifestAdapter manifestAdapter;
    private List<ManifestItem> manifestItems;
    private boolean manifestFromCache;

    private final ActivityResultLauncher<String> cameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    startScan();
                } else {
                    QrScanJourney denied = PerfMonitor.newQrScan();
                    denied.start();
                    denied.finish(QrScanJourney.Outcome.PERMISSION_DENIED);
                    Toast.makeText(this, R.string.camera_permission_needed, Toast.LENGTH_LONG).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fieldData = new FieldDataSource(this);
        deliveries = new DeliveryRepository(this);

        View scanArea = findViewById(R.id.scanArea);
        Button manual = findViewById(R.id.manualIdButton);
        PreviewView preview = findViewById(R.id.cameraPreview);
        scanStatus = findViewById(R.id.scanStatus);
        scanHint = findViewById(R.id.scanHint);

        if (scanArea != null && preview != null) {
            scanner = new QrScannerController(this, preview, this);
            scanArea.setOnClickListener(v -> onScanAreaTapped());
        }
        if (manual != null) {
            manual.setOnClickListener(v -> askForBeneficiaryId());
        }

        setupTabletManifest();
    }

    // =================================================================== QR scanning

    private void onScanAreaTapped() {
        if (scanner.isRunning()) {
            cancelScan();
            Toast.makeText(this, R.string.scan_cancelled, Toast.LENGTH_SHORT).show();
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startScan();
        } else {
            cameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void startScan() {
        if (currentScan != null && !currentScan.isFinished()) {
            currentScan.finish(QrScanJourney.Outcome.CANCELLED);
        }
        currentScan = PerfMonitor.newQrScan();
        currentScan.start();                       // journey starts when the volunteer taps Scan
        setScanText(R.string.scan_starting);
        scanner.start(currentScan);                // camera init + decode are reported by the controller
        setScanText(R.string.scan_scanning);
    }

    private void cancelScan() {
        if (scanner != null && scanner.isRunning()) scanner.stop();
        if (currentScan != null && !currentScan.isFinished()) {
            currentScan.finish(QrScanJourney.Outcome.CANCELLED);
        }
        setScanText(R.string.scan_ready);
    }

    @Override
    public void onQrDecoded(String rawValue) {
        String id = Beneficiary.normaliseId(rawValue);
        if (id == null) {
            currentScan.finish(QrScanJourney.Outcome.DECODE_FAILED);
            setScanText(R.string.scan_ready);
            Toast.makeText(this, R.string.scan_not_beneficiary, Toast.LENGTH_SHORT).show();
            return;
        }
        if (scanStatus != null) scanStatus.setText(getString(R.string.scan_loading_profile, id));
        loadAndShowBeneficiary(id, currentScan);
    }

    @Override
    public void onScannerError(String message) {
        if (currentScan != null) currentScan.finish(QrScanJourney.Outcome.CANCELLED);
        setScanText(R.string.scan_ready);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    /** "Enter beneficiary ID": same profile flow without the camera (no QR trace). */
    private void askForBeneficiaryId() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        input.setText("BNF-2048");
        input.setSelection(input.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.manual_id_title)
                .setView(input)
                .setPositiveButton(R.string.manual_id_open, (d, w) -> {
                    String id = Beneficiary.normaliseId(input.getText().toString());
                    if (id == null) {
                        Toast.makeText(this, R.string.scan_not_beneficiary, Toast.LENGTH_SHORT).show();
                    } else {
                        loadAndShowBeneficiary(id, null);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadAndShowBeneficiary(String id, QrScanJourney scan) {
        fieldData.fetchBeneficiary(id, scan, (beneficiary, fromCache) -> {
            if (isFinishing()) return;
            setScanText(R.string.scan_ready);
            if (beneficiary == null) {
                if (scan != null) scan.finish(QrScanJourney.Outcome.NOT_FOUND);
                Toast.makeText(this, getString(R.string.beneficiary_not_found, id), Toast.LENGTH_SHORT).show();
                return;
            }
            if (fromCache) Toast.makeText(this, R.string.beneficiary_offline, Toast.LENGTH_SHORT).show();
            showBeneficiarySheet(beneficiary, scan);
        });
    }

    // =================================================================== bottom sheets

    private Dialog newBottomSheet(int layout) {
        Dialog dialog = new Dialog(this, R.style.BottomSheetDialogTheme);
        dialog.setContentView(layout);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.BOTTOM);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.28f;
            window.setAttributes(lp);
        }
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);
        return dialog;
    }

    /**
     * Shows a sheet at the given fraction of screen height. onDrawn (may be null) runs
     * after the sheet's first frame is drawn, so render traces stop at "visible on screen".
     */
    private void showSheet(Dialog dialog, int num, int den, Runnable onDrawn) {
        dialog.setOnShowListener(d -> {
            Window w = dialog.getWindow();
            if (w == null) {
                if (onDrawn != null) onDrawn.run();
                return;
            }
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    getResources().getDisplayMetrics().heightPixels * num / den);
            if (onDrawn != null) afterNextDraw(w.getDecorView(), onDrawn);
        });
        dialog.show();
    }

    private void showBeneficiarySheet(Beneficiary b, QrScanJourney scan) {
        Dialog dialog = newBottomSheet(R.layout.dialog_beneficiary);

        setText(dialog, R.id.beneficiaryId, "BENEFICIARY ID: " + b.id);
        setText(dialog, R.id.beneficiaryName, b.name);
        setText(dialog, R.id.beneficiaryZone, b.zone + " • " + b.status);
        setText(dialog, R.id.beneficiaryHousehold, b.householdSize + " members");
        setText(dialog, R.id.beneficiaryDietary, b.dietary);

        Button confirm = dialog.findViewById(R.id.confirmDelivery);
        Button report = dialog.findViewById(R.id.reportIssue);

        confirm.setOnClickListener(v -> {
            ConfirmDeliveryJourney journey = PerfMonitor.newConfirmDelivery();
            journey.start();
            confirm.setEnabled(false);
            report.setEnabled(false);
            confirm.setText(R.string.confirming);
            deliveries.confirm(b.id, new DeliveryRepository.Callback() {
                @Override
                public void onRetry() {
                    journey.onRetry();
                }

                @Override
                public void onComplete(ConfirmDeliveryJourney.Outcome outcome) {
                    Toast.makeText(MainActivity.this,
                            outcome == ConfirmDeliveryJourney.Outcome.SYNCED
                                    ? R.string.delivery_synced : R.string.delivery_queued,
                            Toast.LENGTH_SHORT).show();
                    journey.complete(outcome);    // stops when the volunteer sees the result
                    dialog.dismiss();
                }
            });
        });

        report.setOnClickListener(v -> {
            currentIssue = PerfMonitor.newReportIssue();
            currentIssue.onReportIssueTapped();   // first sheet -> second sheet starts here
            dialog.dismiss();
            showIssueSheet(b, currentIssue);
        });

        if (scan != null) scan.onProfileSheetExpandStarted();   // bottom-sheet render trace starts
        showSheet(dialog, 2, 3, scan == null ? null : scan::onProfileSheetExpanded);
    }

    private void showIssueSheet(Beneficiary b, ReportIssueSheetJourney journey) {
        Dialog dialog = newBottomSheet(R.layout.dialog_report_issue);

        setText(dialog, R.id.issueBeneficiaryName, b.name);
        setText(dialog, R.id.issueBeneficiaryZone, b.zone + " • " + b.status);
        setText(dialog, R.id.issueBeneficiaryDetails, b.householdSize + " members • " + b.dietary);
        setText(dialog, R.id.issueBeneficiaryId, b.id);

        Button submit = dialog.findViewById(R.id.submitIssue);
        Button cancel = dialog.findViewById(R.id.cancelIssue);

        submit.setOnClickListener(v -> {
            RadioGroup reasons = dialog.findViewById(R.id.issueReasons);
            if (reasons.getCheckedRadioButtonId() == -1) {
                Toast.makeText(this, R.string.select_reason_toast, Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(this, R.string.issue_submitted_demo, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        cancel.setOnClickListener(v -> dialog.dismiss());

        Window w = dialog.getWindow();
        if (w != null) w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        showSheet(dialog, 9, 10, journey::onSecondSheetShown);
    }

    // =================================================================== tablet manifest

    private void setupTabletManifest() {
        RecyclerView list = findViewById(R.id.manifestList);
        if (list == null) return; // phone layout

        manifestAdapter = new ManifestAdapter(item -> { });
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setHasFixedSize(true);
        list.setAdapter(manifestAdapter);

        manifestJourney = PerfMonitor.newManifestSideSheet();
        manifestJourney.onOpenRequested();

        scrollMonitor = new FrameMetricsScrollMonitor(getWindow(), manifestJourney.frameStats());
        scrollMonitor.start();
        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
                scrollMonitor.setScrolling(newState != RecyclerView.SCROLL_STATE_IDLE);
            }
        });

        fieldData.fetchManifest((items, fromCache) -> {
            if (isFinishing()) return;
            if (items == null) {
                manifestJourney.onLoadFailed();
                setText(R.id.manifestSummary, getString(R.string.manifest_failed));
                return;
            }
            manifestItems = items;
            manifestFromCache = fromCache;
            manifestJourney.onManifestFetched(items.size(), fromCache);
            showManifest(null);
            afterNextDraw(list, manifestJourney::onManifestRendered);
        });

        int[] filters = {R.id.filterAll, R.id.filterA, R.id.filterB, R.id.filterC, R.id.filterD};
        String[] zones = {null, "A", "B", "C", "D"};
        for (int i = 0; i < filters.length; i++) {
            Button button = findViewById(filters[i]);
            String zone = zones[i];
            if (button != null) button.setOnClickListener(v -> showManifest(zone));
        }

        Button reassign = findViewById(R.id.reassignButton);
        if (reassign != null) {
            reassign.setOnClickListener(v -> {
                ManifestItem selected = manifestAdapter.selectedItem();
                Toast.makeText(this, selected == null
                                ? getString(R.string.reassign_demo)
                                : getString(R.string.reassign_selected, selected.beneficiary),
                        Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void showManifest(String zone) {
        if (manifestItems == null) return;
        List<ManifestItem> shown = ManifestItem.filter(manifestItems, zone);
        manifestAdapter.setItems(shown);
        int done = 0;
        for (ManifestItem item : shown) if (item.completed) done++;
        setText(R.id.manifestSummary, getString(R.string.manifest_summary, shown.size(), done,
                manifestFromCache ? getString(R.string.manifest_offline_suffix) : ""));
    }

    // =================================================================== lifecycle

    @Override
    protected void onPause() {
        super.onPause();
        cancelScan();
        if (currentIssue != null) currentIssue.onAbandoned();
        if (scrollMonitor != null) {
            scrollMonitor.detach();      // stop counting frames ...
            scrollMonitor = null;
        }
        if (manifestJourney != null) manifestJourney.onClosed();   // ... and send the scroll metrics
    }

    @Override
    protected void onDestroy() {
        if (scanner != null) scanner.shutdown();
        fieldData.shutdown();
        deliveries.shutdown();
        super.onDestroy();
    }

    // =================================================================== helpers

    /**
     * Runs action once, after the view's next frame has been drawn. Used to stop
     * render traces when the sheet is actually on screen, not when show() is called.
     */
    static void afterNextDraw(View view, Runnable action) {
        AtomicBoolean fired = new AtomicBoolean(false);
        ViewTreeObserver.OnDrawListener[] holder = new ViewTreeObserver.OnDrawListener[1];
        holder[0] = () -> {
            if (fired.compareAndSet(false, true)) {
                view.post(() -> {
                    view.getViewTreeObserver().removeOnDrawListener(holder[0]);
                    action.run();
                });
            }
        };
        view.getViewTreeObserver().addOnDrawListener(holder[0]);
        view.invalidate();
    }

    private void setScanText(int res) {
        if (scanStatus != null) scanStatus.setText(res);
        if (scanHint != null) scanHint.setText(res == R.string.scan_scanning
                ? "Scanning automatically..." : "Tap to start the camera");
    }

    private void setText(int id, String text) {
        TextView tv = findViewById(id);
        if (tv != null) tv.setText(text);
    }

    private static void setText(Dialog dialog, int id, String text) {
        TextView tv = dialog.findViewById(id);
        if (tv != null) tv.setText(text);
    }
}
