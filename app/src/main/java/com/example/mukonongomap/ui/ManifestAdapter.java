package com.example.mukonongomap.ui;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mukonongomap.R;
import com.example.mukonongomap.data.ManifestItem;

import java.util.Collections;
import java.util.List;

/**
 * Delivery manifest in the warehouse side sheet.
 *
 * RecyclerView instead of the original LinearLayout of TextViews: with 500
 * stops a LinearLayout inflates and measures every row up front, which is the
 * jank the side_sheet_manifest_scroll trace is designed to catch.
 */
public final class ManifestAdapter extends RecyclerView.Adapter<ManifestAdapter.Row> {

    public interface OnSelect {
        void onSelected(ManifestItem item);
    }

    private static final int SELECTED_BG = Color.parseColor("#E3F2EE");

    private List<ManifestItem> items = Collections.emptyList();
    private int selected = RecyclerView.NO_POSITION;
    private final OnSelect onSelect;

    public ManifestAdapter(OnSelect onSelect) {
        this.onSelect = onSelect;
        setHasStableIds(false);
    }

    public void setItems(List<ManifestItem> newItems) {
        items = newItems;
        selected = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public ManifestItem selectedItem() {
        return selected == RecyclerView.NO_POSITION ? null : items.get(selected);
    }

    @NonNull
    @Override
    public Row onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manifest, parent, false);
        return new Row(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Row row, int position) {
        ManifestItem item = items.get(position);
        row.headline.setText(item.headline);
        row.detail.setText(item.detail);
        row.assignee.setText(item.assignee);
        row.zoneBar.setBackgroundColor(zoneColor(item.zone));
        row.itemView.setBackgroundColor(position == selected ? SELECTED_BG : Color.TRANSPARENT);
        row.itemView.setOnClickListener(v -> {
            int pos = row.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            int old = selected;
            selected = pos;
            if (old != RecyclerView.NO_POSITION) notifyItemChanged(old);
            notifyItemChanged(pos);
            onSelect.onSelected(items.get(pos));
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private static int zoneColor(String zone) {
        switch (zone) {
            case "A": return 0xFF2E7D6B;
            case "B": return 0xFF3F6FB5;
            case "C": return 0xFFC77B22;
            default:  return 0xFF9C4D8F;
        }
    }

    static final class Row extends RecyclerView.ViewHolder {
        final TextView headline;
        final TextView detail;
        final TextView assignee;
        final View zoneBar;

        Row(View v) {
            super(v);
            headline = v.findViewById(R.id.manifestHeadline);
            detail = v.findViewById(R.id.manifestDetail);
            assignee = v.findViewById(R.id.manifestAssignee);
            zoneBar = v.findViewById(R.id.zoneBar);
        }
    }
}
