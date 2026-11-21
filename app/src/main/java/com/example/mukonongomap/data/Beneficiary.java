package com.example.mukonongomap.data;

import org.json.JSONException;
import org.json.JSONObject;

/** A food-aid beneficiary, as returned by field-data/beneficiaries/{id}.json. */
public final class Beneficiary {

    public final String id;
    public final String name;
    public final String zone;
    public final String status;
    public final int householdSize;
    public final String dietary;

    public Beneficiary(String id, String name, String zone, String status, int householdSize, String dietary) {
        this.id = id;
        this.name = name;
        this.zone = zone;
        this.status = status;
        this.householdSize = householdSize;
        this.dietary = dietary;
    }

    public static Beneficiary fromJson(String json) throws JSONException {
        JSONObject o = new JSONObject(json);
        return new Beneficiary(
                o.getString("id"),
                o.getString("name"),
                o.optString("zone", ""),
                o.optString("status", "Active record"),
                o.optInt("householdSize", 0),
                o.optString("dietary", "None recorded"));
    }

    /** Beneficiary IDs printed on the QR cards look like BNF-2048. */
    public static String normaliseId(String raw) {
        if (raw == null) return null;
        String id = raw.trim().toUpperCase(java.util.Locale.US);
        return id.matches("BNF-\\d{1,8}") ? id : null;
    }
}
