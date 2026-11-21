package com.example.mukonongomap.data;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One delivery stop in the warehouse manifest.
 *
 * Display strings are built once here, not in onBindViewHolder: string work
 * during a fling is a classic cause of scroll jank on low-end tablets.
 */
public final class ManifestItem {

    public final String time;
    public final String zone;          // "A".."D"
    public final String beneficiary;
    public final String area;
    public final String volunteer;
    public final boolean completed;

    public final String headline;      // "08:30 • Zone A"
    public final String detail;        // "Amina N. – Kisenyi"
    public final String assignee;      // "Volunteer: Sarah K."

    public ManifestItem(String time, String zone, String beneficiary, String area, String volunteer,
                        boolean completed) {
        this.time = time;
        this.zone = zone;
        this.beneficiary = beneficiary;
        this.area = area;
        this.volunteer = volunteer;
        this.completed = completed;
        this.headline = time + " • Zone " + zone + (completed ? "  ✓" : "");
        this.detail = beneficiary + " – " + area;
        this.assignee = "Volunteer: " + volunteer;
    }

    public static List<ManifestItem> listFromJson(String json) throws JSONException {
        JSONArray items = new JSONObject(json).getJSONArray("items");
        List<ManifestItem> out = new ArrayList<>(items.length());
        for (int i = 0; i < items.length(); i++) {
            JSONObject o = items.getJSONObject(i);
            out.add(new ManifestItem(
                    o.getString("time"),
                    o.getString("zone"),
                    o.getString("beneficiary"),
                    o.getString("area"),
                    o.getString("volunteer"),
                    "completed".equals(o.optString("status"))));
        }
        return Collections.unmodifiableList(out);
    }

    /** Items for one zone ("A".."D"), or all items when zone is null. */
    public static List<ManifestItem> filter(List<ManifestItem> all, String zone) {
        if (zone == null) return all;
        List<ManifestItem> out = new ArrayList<>();
        for (ManifestItem item : all) {
            if (zone.equals(item.zone)) out.add(item);
        }
        return out;
    }
}
