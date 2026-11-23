package com.example.mukonongomap.fooddistribution.domain.repository;

import com.example.mukonongomap.fooddistribution.domain.model.Beneficiary;

/**
 * Contract only. Backend engineer will implement this later.
 * ViewModel depends on this interface, never on a concrete class.
 */
public interface DeliveryRepository {

    /**
     * Called after a successful QR scan.
     * @param qrPayload raw content of the scanned QR code
     * @return the matching Beneficiary or null if not found
     */
    Beneficiary findBeneficiaryByQr(String qrPayload);

    /**
     * Confirms that the ration was delivered.
     */
    void confirmDelivery(String beneficiaryId);

    /**
     * Reports an issue for the given beneficiary.
     */
    void reportIssue(String beneficiaryId, String reason, String notes);
}