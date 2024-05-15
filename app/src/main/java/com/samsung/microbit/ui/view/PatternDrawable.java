package com.samsung.microbit.ui.view;

import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;

public class PatternDrawable extends Drawable {
    private final Paint paintOffLine;
    private final Paint paintOffFill;
    private final Paint paintOnLine;
    private final Paint paintOnFill;
    public int [] mPattern = new int [5];

    public void setDeviceName( String name) {
        for ( int col = 0; col < 5; col++) {
            mPattern[col] = -1;
            if ( col < name.length()) {
                String s = ( col % 2) != 0 ? "AEIOU" : "TPGVZ";
                String c = name.substring(col, col + 1);
                int idx = s.indexOf( c);
                if ( idx < 5) {
                    mPattern[col] = 5 - idx;
                }
            }

        }
    }

    public PatternDrawable() {
        // Set up color and text size
        paintOffLine = new Paint();
        paintOffFill = new Paint();
        paintOnLine = new Paint();
        paintOnFill = new Paint();

        paintOffLine.setARGB(255, 85, 85, 85);
        paintOffFill.setARGB(255, 217, 217, 217);
        paintOnLine.setARGB(255, 0xfc, 0xee, 0x21);
        paintOnFill.setARGB(255, 255, 0, 0);

        paintOffFill.setStyle(Paint.Style.FILL);
        paintOnFill.setStyle(Paint.Style.FILL);

        paintOffLine.setStyle(Paint.Style.STROKE);
        paintOnLine.setStyle(Paint.Style.STROKE);

        paintOffLine.setStrokeWidth(10);
        paintOnLine.setStrokeWidth(10);
    }

    @Override
    public void setAlpha(int alpha) {
        // This method is required
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        // This method is required
    }

    @Override
    public int getOpacity() {
        // Must be PixelFormat.UNKNOWN, TRANSLUCENT, TRANSPARENT, or OPAQUE
        return PixelFormat.OPAQUE;
    }

    @Override
    public void draw(Canvas canvas) {
        // Get the drawable's bounds
        int width = getBounds().width();
        int height = getBounds().height();

        int x0 = 0;
        int y0 = 0;
        if ( width > height) {
            x0 = ( width - height) / 2;
            width = height;
        } else {
            y0 = ( height - width) / 2;
            height = width;
        }

        int w   = width / 20;
        int h   = height / 20;
        if ( w < 1) w = 1;
        if ( h < 1) w = 1;
        int w2  = w * 2;
        int h2  = h * 2;
        int w4  = w * 4;
        int h4  = h * 4;
        int w0  = ( width - 5 * w4) / 2;
        int h0  = ( height - 5 * h4) / 2;

        int linewidth = w2 < h2 ? w2 / 10 : h2 / 10;
        if ( linewidth < 1) linewidth = 1;

        paintOffLine.setStrokeWidth(linewidth);
        paintOnLine.setStrokeWidth(linewidth);

        for ( int col = 0; col < 5; col++)
        {
            for ( int row = 0; row < 5; row++)
            {
                int left = x0 + w0 + col * w4 + w;
                int top  = y0 + h0 + row * h4 + h;
                RectF rect = new RectF(left, top, left + w2, top + h2);
                if ( row >= 5 - mPattern[col]) {
                    canvas.drawRect( rect, paintOnFill);
                    canvas.drawRect( rect, paintOnLine);
                } else {
                    canvas.drawRect( rect, paintOffFill);
                    canvas.drawRect( rect, paintOffLine);
                }
            }
        }
    }
}