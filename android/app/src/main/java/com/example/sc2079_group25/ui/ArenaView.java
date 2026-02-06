package com.example.sc2079_group25.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ArenaView extends View {

    private final Paint arenaPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint obstaclePaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint robotPaint = new Paint();

    private int gridCountX = 20;
    private int gridCountY = 20;

    private float robotX = -1, robotY = -1, robotRotation = 0;
    private final List<Obstacle> obstacles = new ArrayList<>();

    public static class Obstacle {
        public int id;
        public float x, y;

        public Obstacle(int id, float x, float y) {
            this.id = id;
            this.x = x;
            this.y = y;
        }
    }

    public ArenaView(Context context) {
        super(context);
        init();
    }

    public ArenaView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ArenaView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        arenaPaint.setColor(Color.BLACK);
        arenaPaint.setStyle(Paint.Style.STROKE);
        arenaPaint.setStrokeWidth(5f);

        gridPaint.setColor(Color.LTGRAY);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(1f);

        obstaclePaint.setColor(Color.DKGRAY);
        obstaclePaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(20f);
        textPaint.setTextAlign(Paint.Align.CENTER);

        robotPaint.setColor(Color.RED);
        robotPaint.setStyle(Paint.Style.FILL);
    }

    public void setGridSize(int x, int y) {
        this.gridCountX = x;
        this.gridCountY = y;
        invalidate();
    }

    public void updateRobot(float x, float y, float r) {
        this.robotX = x;
        this.robotY = y;
        this.robotRotation = r;
        invalidate();
    }

    public void addObstacle(int id, float x, float y) {
        obstacles.add(new Obstacle(id, x, y));
        invalidate();
    }

    public void clearMap() {
        obstacles.clear();
        robotX = -1;
        robotY = -1;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        float cellWidth = viewWidth / gridCountX;
        float cellHeight = viewHeight / gridCountY;

        // Draw Grid Lines
        for (int i = 0; i <= gridCountX; i++) {
            float x = i * cellWidth;
            canvas.drawLine(x, 0, x, viewHeight, gridPaint);
        }
        for (int j = 0; j <= gridCountY; j++) {
            float y = j * cellHeight;
            canvas.drawLine(0, y, viewWidth, y, gridPaint);
        }

        // Draw Arena Boundary
        canvas.drawRect(0, 0, viewWidth, viewHeight, arenaPaint);

        // Draw Obstacles (Coordinates are assumed to be in Grid Units)
        float obsWidth = cellWidth * 0.8f;
        float obsHeight = cellHeight * 0.8f;
        for (Obstacle obs : obstacles) {
            float centerX = (obs.x + 0.5f) * cellWidth;
            float centerY = (obs.y + 0.5f) * cellHeight;
            canvas.drawRect(centerX - obsWidth/2, centerY - obsHeight/2,
                           centerX + obsWidth/2, centerY + obsHeight/2, obstaclePaint);
            canvas.drawText(String.valueOf(obs.id), centerX, centerY + (textPaint.getTextSize()/3), textPaint);
        }

        // Draw Robot (Coordinates are assumed to be in Grid Units)
        if (robotX >= 0 && robotY >= 0) {
            float centerX = (robotX + 0.5f) * cellWidth;
            float centerY = (robotY + 0.5f) * cellHeight;
            drawRobot(canvas, centerX, centerY, robotRotation, cellWidth, cellHeight);
        }
    }

    private void drawRobot(Canvas canvas, float x, float y, float rotation, float cellW, float cellH) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);

        float size = Math.min(cellW, cellH) * 0.8f;

        // Draw a simple robot shape (triangle pointing front)
        Path path = new Path();
        path.moveTo(0, -size/2); // Front
        path.lineTo(-size/2.5f, size/2.5f); // Back Left
        path.lineTo(size/2.5f, size/2.5f);  // Back Right
        path.close();

        canvas.drawPath(path, robotPaint);
        
        // Direction indicator (small circle at front)
        Paint indicator = new Paint();
        indicator.setColor(Color.YELLOW);
        canvas.drawCircle(0, -size/3, size/10, indicator);

        canvas.restore();
    }
}
