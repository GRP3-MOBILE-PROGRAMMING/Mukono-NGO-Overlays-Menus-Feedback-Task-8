package com.example.mukonongomap.fooddistribution.domain.model;

import java.util.Objects;

/**
 * Represents the current state of the bottom-sheet flow.
 * Immutable. ViewModel only emits new instances.
 */
public final class BottomSheetUiState {

    public enum Type {
        HIDDEN,
        BENEFICIARY_PROFILE,
        REPORT_ISSUE
    }

    private final Type type;
    private final Beneficiary beneficiary;          // only present when Type == BENEFICIARY_PROFILE or REPORT_ISSUE
    private final ReportReason selectedReason;      // only relevant in REPORT_ISSUE
    private final String notes;                     // free-text notes in REPORT_ISSUE
    private final boolean dismissible;              // false while user is typing notes

    private BottomSheetUiState(Type type,
                               Beneficiary beneficiary,
                               ReportReason selectedReason,
                               String notes,
                               boolean dismissible) {
        this.type = type;
        this.beneficiary = beneficiary;
        this.selectedReason = selectedReason;
        this.notes = notes == null ? "" : notes;
        this.dismissible = dismissible;
    }

    // ---------- Factory methods ----------

    public static BottomSheetUiState hidden() {
        return new BottomSheetUiState(Type.HIDDEN, null, null, "", true);
    }

    public static BottomSheetUiState beneficiaryProfile(Beneficiary beneficiary) {
        return new BottomSheetUiState(Type.BENEFICIARY_PROFILE, beneficiary, null, "", true);
    }

    public static BottomSheetUiState reportIssue(Beneficiary beneficiary) {
        return new BottomSheetUiState(Type.REPORT_ISSUE, beneficiary, null, "", true);
    }

    // ---------- Copy helpers (immutable updates) ----------

    public BottomSheetUiState withSelectedReason(ReportReason reason) {
        return new BottomSheetUiState(this.type, this.beneficiary, reason, this.notes, this.dismissible);
    }

    public BottomSheetUiState withNotes(String notes) {
        boolean stillDismissible = notes == null || notes.trim().isEmpty();
        return new BottomSheetUiState(this.type, this.beneficiary, this.selectedReason, notes, stillDismissible);
    }

    public BottomSheetUiState withDismissible(boolean dismissible) {
        return new BottomSheetUiState(this.type, this.beneficiary, this.selectedReason, this.notes, dismissible);
    }

    // ---------- Getters ----------

    public Type getType() { return type; }
    public Beneficiary getBeneficiary() { return beneficiary; }
    public ReportReason getSelectedReason() { return selectedReason; }
    public String getNotes() { return notes; }
    public boolean isDismissible() { return dismissible; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BottomSheetUiState)) return false;
        BottomSheetUiState that = (BottomSheetUiState) o;
        return dismissible == that.dismissible
                && type == that.type
                && Objects.equals(beneficiary, that.beneficiary)
                && selectedReason == that.selectedReason
                && Objects.equals(notes, that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, beneficiary, selectedReason, notes, dismissible);
    }
}