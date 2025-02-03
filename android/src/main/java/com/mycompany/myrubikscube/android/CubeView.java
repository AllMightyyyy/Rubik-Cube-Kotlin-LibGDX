package com.mycompany.myrubikscube.android;

import static java.lang.Math.min;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;

public class CubeView extends View {

    private final Paint paint = new Paint();
    private Canvas canvas;

    // frontColors[row][col]
    private String[][] frontColors = {
        {"X", "X", "X"},
        {"X", "X", "X"},
        {"X", "X", "X"}
    };
    // sideColors: [top, left, bottom, right] or any consistent scheme
    private String[] sideColors = {"X", "X", "X", "X"};
    private final int padding = 4;

    public CubeView(Context context) {
        this(context, null);
    }

    public CubeView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CubeView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        this.canvas = canvas;

        int w = getWidth();
        int h = getHeight();
        paint.setColor(Color.DKGRAY);
        canvas.drawRect(0, 0, w, h, paint);

        // Each face is a 3x3
        double cubeLen = min(w, h) * 0.8;
        int boxLen = (int) (cubeLen / 3);
        int startX = (int) ((w - cubeLen) / 2);
        int startY = (int) ((h - cubeLen) / 2);

        paint.setStrokeWidth(3);

        // Draw the front face
        // row => y direction, col => x direction
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                paint.setColor(getColor(frontColors[row][col]));
                int boxStartX = startX + boxLen * col;
                int boxStartY = startY + boxLen * row;

                // optional corner “paddings”
                if (col == 0) boxStartX -= padding;
                else if (col == 2) boxStartX += padding;
                if (row == 0) boxStartY -= padding;
                else if (row == 2) boxStartY += padding;

                canvas.drawRect(boxStartX, boxStartY,
                    boxStartX + boxLen,
                    boxStartY + boxLen,
                    paint);
            }
        }

        // Draw side color bars (example usage)
        int thickness = (int) (boxLen / 6);
        // top
        paint.setColor(getColor(sideColors[0]));
        int boxStartX = startX + boxLen; // middle of top 3 boxes
        int boxStartY = startY - (3 * padding) - thickness;
        canvas.drawRect(new Rect(boxStartX, boxStartY,
            boxStartX + boxLen,
            boxStartY + thickness), paint);

        // bottom
        paint.setColor(getColor(sideColors[2]));
        boxStartY = startY + 3 * boxLen + 3 * padding;
        canvas.drawRect(new Rect(boxStartX, boxStartY,
            boxStartX + boxLen,
            boxStartY + thickness), paint);

        // left
        paint.setColor(getColor(sideColors[1]));
        boxStartX = startX - (3 * padding) - thickness;
        boxStartY = startY + boxLen; // center vertically
        canvas.drawRect(new Rect(boxStartX, boxStartY,
            boxStartX + thickness,
            boxStartY + boxLen), paint);

        // right
        paint.setColor(getColor(sideColors[3]));
        boxStartX = startX + (3 * boxLen) + (3 * padding);
        canvas.drawRect(new Rect(boxStartX, boxStartY,
            boxStartX + thickness,
            boxStartY + boxLen), paint);
    }

    private int getColor(String color) {
        // Single-letter color codes
        if (color.length() != 1) {
            return Color.DKGRAY;
        }
        switch (color) {
            case "Y":
                return Color.rgb(255, 215, 0);
            case "W":
                return Color.rgb(255, 255, 255);
            case "O":
                return Color.rgb(254, 80, 0);
            case "R":
                return Color.rgb(186, 23, 47);
            case "B":
                return Color.rgb(0, 61, 165);
            case "G":
                return Color.rgb(0, 154, 68);
            default:
                return Color.DKGRAY;
        }
    }

    // ----- PUBLIC SETTERS ----- //

    /** Set entire 3x3 from int[][] (row-major) */
    public void setFrontColors(int[][] colors) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                frontColors[row][col] = ImageUtil.colorLabel[colors[row][col]];
            }
        }
        invalidate();
    }

    /** Set entire 3x3 from String[][] (row-major) */
    public void setFrontColors(String[][] colors) {
        // just copy in row-major
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                frontColors[row][col] = colors[row][col];
            }
        }
        invalidate();
    }

    /** Set entire 3x3 from a 9-char string in row-major order */
    public void setFrontColors(String colors) {
        if (colors.length() == 9) {
            int idx = 0;
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    frontColors[row][col] = String.valueOf(colors.charAt(idx));
                    idx++;
                }
            }
        }
        invalidate();
    }

    /** Set only the center square color */
    public void setCenterColor(String color) {
        if (color.length() == 1) {
            frontColors[1][1] = color;
            invalidate();
        }
    }

    /** Overload for char center color */
    public void setCenterColor(char color) {
        frontColors[1][1] = String.valueOf(color);
        invalidate();
    }

    /** Set the four side “bars” color codes (top, left, bottom, right). */
    public void setSideColors(String[] colors) {
        if (colors.length == 4) {
            sideColors = colors;
            invalidate();
        }
    }

    public void setSideColors(String colors) {
        // e.g., "YOBW"
        if (colors.length() == 4) {
            for (int i = 0; i < 4; i++) {
                sideColors[i] = String.valueOf(colors.charAt(i));
            }
            invalidate();
        }
    }
}
