package com.example.mukonongomap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

/** Displays the supplied Figma map artwork, preserving its zones, roads, labels and stop pins. */
public class DeliveryMapView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap mapBitmap;

    public DeliveryMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mapBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.kampala_map_reference);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (mapBitmap == null) {
            canvas.drawColor(0xFFE6E4DA);
            return;
        }
        Rect destination = new Rect(0, 0, getWidth(), getHeight());
        canvas.drawBitmap(mapBitmap, null, destination, paint);
    }
}
