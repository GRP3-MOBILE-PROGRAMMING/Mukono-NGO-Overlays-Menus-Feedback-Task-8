package com.example.mukonongomap.fooddistribution.ui.verification;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.mukonongomap.fooddistribution.domain.model.Beneficiary;

import com.example.mukonongomap.fooddistribution.domain.model.BottomSheetUiState;
import com.example.mukonongomap.fooddistribution.domain.model.ReportReason;
import com.example.mukonongomap.fooddistribution.domain.repository.DeliveryRepository;

/**
 * Holds all UI state for the QR → Bottom-sheet flow.
 * Completely testable with a Fake repository.
 */
public class DeliveryVerificationViewModel extends ViewModel {

    private final DeliveryRepository repository;

    private final MutableLiveData<BottomSheetUiState> uiState =
            new MutableLiveData<>(BottomSheetUiState.hidden());

    public DeliveryVerificationViewModel(@NonNull DeliveryRepository repository) {
        this.repository = repository;
    }

    public LiveData<BottomSheetUiState> getUiState() {
        return uiState;
    }

    // ------------------------------------------------------------------
    // Public actions (called from Fragment / BottomSheet)
    // ------------------------------------------------------------------

    public void onQrScanned(String qrPayload) {
        Beneficiary beneficiary = repository.findBeneficiaryByQr(qrPayload);
        if (beneficiary != null) {
            uiState.setValue(BottomSheetUiState.beneficiaryProfile(beneficiary));
        } else {
            // In a real app we would emit an error state.
            // For now, we keep the sheet hidden.
            uiState.setValue(BottomSheetUiState.hidden());
        }
    }

    public void onConfirmDelivery() {
        BottomSheetUiState current = uiState.getValue();
        if (current != null && current.getType() == BottomSheetUiState.Type.BENEFICIARY_PROFILE) {
            repository.confirmDelivery(current.getBeneficiary().getId());
            uiState.setValue(BottomSheetUiState.hidden());
        }
    }

    public void onReportIssueClicked() {
        BottomSheetUiState current = uiState.getValue();
        if (current != null && current.getType() == BottomSheetUiState.Type.BENEFICIARY_PROFILE) {
            uiState.setValue(BottomSheetUiState.reportIssue(current.getBeneficiary()));
        }
    }

    public void onReportReasonSelected(ReportReason reason) {
        BottomSheetUiState current = uiState.getValue();
        if (current != null && current.getType() == BottomSheetUiState.Type.REPORT_ISSUE) {
            uiState.setValue(current.withSelectedReason(reason));
        }
    }

    public void onReportNotesChanged(String notes) {
        BottomSheetUiState current = uiState.getValue();
        if (current != null && current.getType() == BottomSheetUiState.Type.REPORT_ISSUE) {
            // Key requirement: while the user is typing, the sheet must not be dismissible
            uiState.setValue(current.withNotes(notes));
        }
    }

    public void onSubmitReport() {
        BottomSheetUiState current = uiState.getValue();
        if (current != null
                && current.getType() == BottomSheetUiState.Type.REPORT_ISSUE
                && current.getSelectedReason() != null) {

            repository.reportIssue(
                    current.getBeneficiary().getId(),
                    current.getSelectedReason().name(),
                    current.getNotes()
            );
            uiState.setValue(BottomSheetUiState.hidden());
        }
    }

    public void onDismissRequested() {
        BottomSheetUiState current = uiState.getValue();
        if (current != null && current.isDismissible()) {
            uiState.setValue(BottomSheetUiState.hidden());
        }
        // If not dismissible → ignore the request (prevents accidental dismiss)
    }
}