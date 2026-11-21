package com.example.mukonongomap.performance;

/**
 * Single source of truth for every custom trace, metric and attribute name.
 *
 * Firebase rules (enforced by TraceNamesTest in CI):
 *  - trace / metric names: 1-100 chars, no leading underscore, no surrounding whitespace
 *  - attribute names: 1-40 chars, start with a letter, letters/digits/underscore only
 *  - at most 5 custom attributes per trace
 */
public final class TraceNames {
    private TraceNames() { }

    // ---- Journey 1: QR scan -> beneficiary profile bottom sheet -------------------
    /** End-to-end: "Scan" tapped until the profile bottom sheet is expanded and drawn. */
    public static final String QR_SCAN_TO_PROFILE = "qr_scan_to_profile";
    /** Camera open requested until first preview frame. */
    public static final String QR_CAMERA_INIT = "qr_camera_init";
    /** First preview frame until a QR code is successfully decoded (includes aiming). */
    public static final String QR_DECODE = "qr_decode";
    /** Beneficiary lookup request until response parsed. */
    public static final String QR_BENEFICIARY_FETCH = "qr_beneficiary_fetch";
    /** Bottom sheet expand started until STATE_EXPANDED and first frame drawn. */
    public static final String QR_PROFILE_SHEET_RENDER = "qr_profile_sheet_render";

    // ---- Journey 2: Confirm Delivery ---------------------------------------------
    public static final String CONFIRM_DELIVERY = "confirm_delivery";

    // ---- Journey 3: Report Issue bottom sheets ----------------------------------
    /** "Report Issue" tapped on the profile sheet until the Report Issue sheet is drawn. */
    public static final String REPORT_ISSUE_SHEET_TRANSITION = "report_issue_sheet_transition";

    // ---- Journey 4: Warehouse side sheet manifest --------------------------------
    /** Side sheet open requested until the manifest list is bound and drawn. */
    public static final String SIDE_SHEET_MANIFEST_LOAD = "side_sheet_manifest_load";
    /** Lives while the side sheet is open; carries scroll rendering metrics. */
    public static final String SIDE_SHEET_MANIFEST_SCROLL = "side_sheet_manifest_scroll";

    // ---- Metrics -----------------------------------------------------------------
    public static final String M_CAMERA_INIT_MS = "camera_init_ms";
    public static final String M_DECODE_MS = "decode_ms";
    public static final String M_DECODE_FRAMES = "decode_frames_analyzed";
    public static final String M_DECODE_CPU_MS = "decode_cpu_ms_total";
    public static final String M_DECODE_CPU_MAX_MS = "decode_cpu_ms_max";
    public static final String M_FETCH_MS = "fetch_ms";
    public static final String M_RESPONSE_BYTES = "response_bytes";
    public static final String M_SHEET_RENDER_MS = "sheet_render_ms";
    public static final String M_RETRIES = "retries";
    public static final String M_TRANSITION_MS = "transition_ms";
    public static final String M_MANIFEST_ITEMS = "manifest_items";
    public static final String M_BIND_MS = "bind_ms";

    // Rendering metrics for the side sheet scroll trace
    public static final String M_FRAMES_TOTAL = "frames_total";
    public static final String M_FRAMES_SLOW = "frames_slow";
    public static final String M_FRAMES_FROZEN = "frames_frozen";
    public static final String M_FRAMES_DROPPED = "frames_dropped";
    public static final String M_SLOW_FRAME_PCT_X100 = "slow_frame_pct_x100";
    public static final String M_MAX_FRAME_MS = "max_frame_ms";
    public static final String M_SCROLL_SESSIONS = "scroll_sessions";
    // Where slow frames spend their time (sum over slow frames, ms) - API 24+ only
    public static final String M_SLOW_INPUT_MS = "slow_input_ms";
    public static final String M_SLOW_ANIMATION_MS = "slow_animation_ms";
    public static final String M_SLOW_LAYOUT_MEASURE_MS = "slow_layout_measure_ms";
    public static final String M_SLOW_DRAW_MS = "slow_draw_ms";
    public static final String M_SLOW_GPU_MS = "slow_sync_gpu_ms";

    // ---- Attributes (max 5 per trace) --------------------------------------------
    public static final String A_NETWORK = "network_type";
    public static final String A_DEVICE_CLASS = "device_class";
    public static final String A_OUTCOME = "outcome";
    public static final String A_DATA_SOURCE = "data_source";
    public static final String A_LIST_SIZE = "list_size_bucket";
}
