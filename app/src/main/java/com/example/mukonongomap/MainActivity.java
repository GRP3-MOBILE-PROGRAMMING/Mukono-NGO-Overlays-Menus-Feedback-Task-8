package com.example.mukonongomap;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        View scanArea = findViewById(R.id.scanArea);
        Button manual = findViewById(R.id.manualIdButton);

        if (scanArea != null) {
            scanArea.setOnClickListener(v -> showBeneficiarySheet());
        }

        if (manual != null) {
            manual.setOnClickListener(v -> showBeneficiarySheet());
        }

        setupTabletManifest();
    }

    private void showBeneficiarySheet() {

        Dialog dialog = new Dialog(this, R.style.BottomSheetDialogTheme);
        dialog.setContentView(R.layout.dialog_beneficiary);

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

        Button confirm = dialog.findViewById(R.id.confirmDelivery);
        Button report = dialog.findViewById(R.id.reportIssue);

        confirm.setOnClickListener(v -> {
            Toast.makeText(this,
                    R.string.delivery_confirmed_demo,
                    Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        report.setOnClickListener(v -> {
            dialog.dismiss();
            showIssueSheet();
        });

        dialog.show();

        if (window != null) {
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getResources().getDisplayMetrics().heightPixels * 2 / 3
            );
        }
    }

    private void showIssueSheet() {

        Dialog dialog = new Dialog(this, R.style.BottomSheetDialogTheme);
        dialog.setContentView(R.layout.dialog_report_issue);

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

        Button submit = dialog.findViewById(R.id.submitIssue);
        Button cancel = dialog.findViewById(R.id.cancelIssue);

        submit.setOnClickListener(v -> {

            RadioGroup reasons = dialog.findViewById(R.id.issueReasons);

            if (reasons.getCheckedRadioButtonId() == -1) {
                Toast.makeText(this,
                        R.string.select_reason_toast,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Toast.makeText(this,
                    R.string.issue_submitted_demo,
                    Toast.LENGTH_SHORT).show();

            dialog.dismiss();
        });

        cancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        if (window != null) {

            window.setSoftInputMode(
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            );

            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    getResources().getDisplayMetrics().heightPixels * 9 / 10
            );
        }
    }

    private void setupTabletManifest() {

        int[] ids = {
                R.id.filterAll,
                R.id.filterA,
                R.id.filterB,
                R.id.filterC,
                R.id.filterD
        };

        int[] zoneIds = {
                R.id.zoneAItems,
                R.id.zoneBItems,
                R.id.zoneCItems,
                R.id.zoneDItems
        };

        int[] zoneStrings = {
                R.string.filter_all,
                R.string.filter_zone_a,
                R.string.filter_zone_b,
                R.string.filter_zone_c,
                R.string.filter_zone_d
        };

        for (int i = 0; i < ids.length; i++) {

            Button button = findViewById(ids[i]);

            if (button != null) {

                final int selected = i;

                button.setOnClickListener(v -> {

                    for (int j = 0; j < zoneIds.length; j++) {

                        TextView item = findViewById(zoneIds[j]);

                        if (item != null) {
                            item.setVisibility(
                                    selected == 0 || selected == j + 1
                                            ? View.VISIBLE
                                            : View.GONE
                            );
                        }
                    }

                    Toast.makeText(this,
                            zoneStrings[selected],
                            Toast.LENGTH_SHORT).show();
                });
            }
        }

        Button reassign = findViewById(R.id.reassignButton);

        if (reassign != null) {
            reassign.setOnClickListener(v ->
                    Toast.makeText(this,
                            R.string.reassign_demo,
                            Toast.LENGTH_SHORT).show()
            );
        }
    }
}
