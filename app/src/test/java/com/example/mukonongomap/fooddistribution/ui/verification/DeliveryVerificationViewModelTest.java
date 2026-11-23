package com.example.mukonongomap.fooddistribution.ui.verification;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.Observer;

import com.example.mukonongomap.fooddistribution.domain.model.Beneficiary;
import com.example.mukonongomap.fooddistribution.domain.model.BottomSheetUiState;
import com.example.mukonongomap.fooddistribution.domain.model.ReportReason;
import com.example.mukonongomap.fooddistribution.domain.repository.DeliveryRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pure unit tests – no Android framework, no real backend.
 * These tests will keep passing after the real DeliveryRepository is implemented.
 */
@RunWith(MockitoJUnitRunner.class)
public class DeliveryVerificationViewModelTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Mock
    private DeliveryRepository repository;

    private DeliveryVerificationViewModel viewModel;

    private final Beneficiary sampleBeneficiary = new Beneficiary(
            "BEN-001",
            "Amina Nakato",
            5,
            Arrays.asList("Halal", "No peanuts")
    );

    @Before
    public void setUp() {
        viewModel = new DeliveryVerificationViewModel(repository);
    }

    // ------------------------------------------------------------------
    // 1. QR Scan → Beneficiary profile sheet
    // ------------------------------------------------------------------
    @Test
    public void onQrScanned_validQr_showsBeneficiaryProfile() {
        when(repository.findBeneficiaryByQr("QR-VALID")).thenReturn(sampleBeneficiary);

        viewModel.onQrScanned("QR-VALID");

        BottomSheetUiState state = viewModel.getUiState().getValue();
        assertThat(state.getType()).isEqualTo(BottomSheetUiState.Type.BENEFICIARY_PROFILE);
        assertThat(state.getBeneficiary()).isEqualTo(sampleBeneficiary);
        assertThat(state.isDismissible()).isTrue();
    }

    @Test
    public void onQrScanned_unknownQr_keepsSheetHidden() {
        when(repository.findBeneficiaryByQr("QR-UNKNOWN")).thenReturn(null);

        viewModel.onQrScanned("QR-UNKNOWN");

        assertThat(viewModel.getUiState().getValue().getType())
                .isEqualTo(BottomSheetUiState.Type.HIDDEN);
    }

    // ------------------------------------------------------------------
    // 2. Confirm Delivery
    // ------------------------------------------------------------------
    @Test
    public void onConfirmDelivery_callsRepositoryAndHidesSheet() {
        when(repository.findBeneficiaryByQr(anyString())).thenReturn(sampleBeneficiary);
        viewModel.onQrScanned("QR-VALID");

        viewModel.onConfirmDelivery();

        verify(repository).confirmDelivery("BEN-001");
        assertThat(viewModel.getUiState().getValue().getType())
                .isEqualTo(BottomSheetUiState.Type.HIDDEN);
    }

    // ------------------------------------------------------------------
    // 3. Report Issue flow
    // ------------------------------------------------------------------
    @Test
    public void onReportIssueClicked_opensReportIssueSheet() {
        when(repository.findBeneficiaryByQr(anyString())).thenReturn(sampleBeneficiary);
        viewModel.onQrScanned("QR-VALID");

        viewModel.onReportIssueClicked();

        BottomSheetUiState state = viewModel.getUiState().getValue();
        assertThat(state.getType()).isEqualTo(BottomSheetUiState.Type.REPORT_ISSUE);
        assertThat(state.getBeneficiary()).isEqualTo(sampleBeneficiary);
        assertThat(state.isDismissible()).isTrue();
    }

    @Test
    public void onReportReasonSelected_updatesSelectedReason() {
        openReportIssueSheet();

        viewModel.onReportReasonSelected(ReportReason.NO_SHOW);

        assertThat(viewModel.getUiState().getValue().getSelectedReason())
                .isEqualTo(ReportReason.NO_SHOW);
    }

    // ------------------------------------------------------------------
    // 4. Critical requirement: prevent accidental dismiss while typing
    // ------------------------------------------------------------------
    @Test
    public void onReportNotesChanged_withText_makesSheetNotDismissible() {
        openReportIssueSheet();

        viewModel.onReportNotesChanged("The person was not at home");

        BottomSheetUiState state = viewModel.getUiState().getValue();
        assertThat(state.getNotes()).isEqualTo("The person was not at home");
        assertThat(state.isDismissible()).isFalse();          // ← key assertion
    }

    @Test
    public void onReportNotesChanged_emptyText_makesSheetDismissibleAgain() {
        openReportIssueSheet();
        viewModel.onReportNotesChanged("some text");
        viewModel.onReportNotesChanged("");                   // user clears the field

        assertThat(viewModel.getUiState().getValue().isDismissible()).isTrue();
    }

    @Test
    public void onDismissRequested_whenNotDismissible_ignoresRequest() {
        openReportIssueSheet();
        viewModel.onReportNotesChanged("Important notes – do not lose");

        viewModel.onDismissRequested();                       // user tries to swipe away

        // Sheet must stay open
        assertThat(viewModel.getUiState().getValue().getType())
                .isEqualTo(BottomSheetUiState.Type.REPORT_ISSUE);
    }

    @Test
    public void onDismissRequested_whenDismissible_hidesSheet() {
        openReportIssueSheet();                               // notes still empty → dismissible

        viewModel.onDismissRequested();

        assertThat(viewModel.getUiState().getValue().getType())
                .isEqualTo(BottomSheetUiState.Type.HIDDEN);
    }

    // ------------------------------------------------------------------
    // 5. Submit report
    // ------------------------------------------------------------------
    @Test
    public void onSubmitReport_withReason_callsRepositoryAndHides() {
        openReportIssueSheet();
        viewModel.onReportReasonSelected(ReportReason.ADDRESS_CHANGED);
        viewModel.onReportNotesChanged("Moved to Zone B");

        viewModel.onSubmitReport();

        verify(repository).reportIssue("BEN-001", "ADDRESS_CHANGED", "Moved to Zone B");
        assertThat(viewModel.getUiState().getValue().getType())
                .isEqualTo(BottomSheetUiState.Type.HIDDEN);
    }

    @Test
    public void onSubmitReport_withoutReason_doesNothing() {
        openReportIssueSheet();
        // no reason selected

        viewModel.onSubmitReport();

        verify(repository, never()).reportIssue(anyString(), anyString(), anyString());
        assertThat(viewModel.getUiState().getValue().getType())
                .isEqualTo(BottomSheetUiState.Type.REPORT_ISSUE);
    }

    // ------------------------------------------------------------------
    // Helper
    // ------------------------------------------------------------------
    private void openReportIssueSheet() {
        when(repository.findBeneficiaryByQr(anyString())).thenReturn(sampleBeneficiary);
        viewModel.onQrScanned("QR-VALID");
        viewModel.onReportIssueClicked();
    }
}