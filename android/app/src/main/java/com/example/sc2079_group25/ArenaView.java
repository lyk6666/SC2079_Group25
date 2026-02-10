package com.example.sc2079_group25;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class ArenaView extends View {

    private final Paint arenaPaint = new Paint();
    private final Paint gridPaint = new Paint();
    private final Paint obstaclePaint = new Paint();
    private final Paint textPaint = new Paint();
    private final Paint robotPaint = new Paint();
    private final Paint labelPaint = new Paint();
    private final Paint facePaint = new Paint();

    private int gridCountX = 20;
    private int gridCountY = 20;

    private float robotX = 1, robotY = 1; // Center of 3x3 robot
    private float robotRotation = 0; // 0: N, 90: E, 180: S, 270: W
    
    private List<Obstacle> obstacles = new ArrayList<>();
    
    // Undo/Redo stacks for obstacles
    private final Stack<List<Obstacle>> undoStack = new Stack<>();
    private final Stack<List<Obstacle>> redoStack = new Stack<>();

    // Dragging state
    private float dragCurrentX, dragCurrentY;
    private int draggingId = -1;

    // Layout constants
    private float startX, startY, sideLength, cellWidth, cellHeight;
    private float bankY, bankItemWidth;
    private final float labelPadding = 40f;
    private final float bankPadding = 60f;

    public static class Obstacle {
        public int id;
        public String value;
        public float x, y;
        public int direction; // 0: N, 1: E, 2: S, 3: W

        public Obstacle(int id, float x, float y) {
            this.id = id;
            this.value = "none";
            this.x = x;
            this.y = y;
            this.direction = 0;
        }

        public Obstacle copy() {
            Obstacle o = new Obstacle(id, x, y);
            o.value = this.value;
            o.direction = this.direction;
            return o;
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
        arenaPaint.setStrokeWidth(2f);

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
        robotPaint.setAlpha(180);

        labelPaint.setColor(Color.BLACK);
        labelPaint.setTextSize(18f);
        labelPaint.setTextAlign(Paint.Align.CENTER);

        facePaint.setColor(Color.RED);
        facePaint.setStyle(Paint.Style.FILL);
    }

    private void saveState() {
        List<Obstacle> state = new ArrayList<>();
        for (Obstacle o : obstacles) state.add(o.copy());
        undoStack.push(state);
        redoStack.clear();
    }

    public List<Obstacle> getObstacles() {
        return obstacles;
    }

    public void revert() {
        if (undoStack.isEmpty()) return;
        List<Obstacle> current = new ArrayList<>();
        for (Obstacle o : obstacles) current.add(o.copy());
        redoStack.push(current);
        obstacles = undoStack.pop();
        invalidate();
    }

    public void deRevert() {
        if (redoStack.isEmpty()) return;
        List<Obstacle> current = new ArrayList<>();
        for (Obstacle o : obstacles) current.add(o.copy());
        undoStack.push(current);
        obstacles = redoStack.pop();
        invalidate();
    }

    public void updateRobot(float x, float y, float r) {
        this.robotX = x;
        this.robotY = y;
        this.robotRotation = r;
        invalidate();
    }

    public void moveRobotForward() {
        if (robotRotation == 0) robotY++;
        else if (robotRotation == 90) robotX++;
        else if (robotRotation == 180) robotY--;
        else if (robotRotation == 270) robotX--;
        constrainRobot();
        invalidate();
    }

    public void moveRobotBackward() {
        if (robotRotation == 0) robotY--;
        else if (robotRotation == 90) robotX--;
        else if (robotRotation == 180) robotY++;
        else if (robotRotation == 270) robotX++;
        constrainRobot();
        invalidate();
    }

    public void turnRobotLeft() {
        moveRobotForward();
        robotRotation = (robotRotation - 90 + 360) % 360;
        moveRobotForward();
    }

    public void turnRobotRight() {
        moveRobotForward();
        robotRotation = (robotRotation + 90) % 360;
        moveRobotForward();
    }

    private void constrainRobot() {
        if (robotX < 1) robotX = 1;
        if (robotX > 18) robotX = 18;
        if (robotY < 1) robotY = 1;
        if (robotY > 18) robotY = 18;
    }

    public void updateObstacleValue(int id, String value) {
        for (Obstacle o : obstacles) {
            if (o.id == id) {
                o.value = value;
                invalidate();
                return;
            }
        }
    }

    public void addObstacle(int id, float x, float y) {
        saveState();
        obstacles.removeIf(o -> o.id == id);
        obstacles.add(new Obstacle(id, x, y));
        invalidate();
    }

    public void clearMap() {
        saveState();
        obstacles.clear();
        robotX = 1;
        robotY = 1;
        robotRotation = 0;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float viewWidth = getWidth();
        float viewHeight = getHeight();
        
        sideLength = Math.min(viewWidth - labelPadding * 2, viewHeight - labelPadding - bankPadding - 20f);
        cellWidth = sideLength / gridCountX;
        cellHeight = sideLength / gridCountY;

        startX = (viewWidth - (sideLength + labelPadding)) / 2f + labelPadding;
        startY = (viewHeight - (sideLength + labelPadding + bankPadding)) / 2f + bankPadding;
        
        // Bank
        bankY = startY - bankPadding + 10f;
        bankItemWidth = viewWidth / 10f;
        for (int i = 0; i < 10; i++) {
            boolean isPlaced = false;
            for (Obstacle o : obstacles) if (o.id == i) { isPlaced = true; break; }
            if (isPlaced && draggingId != i) continue;
            float bx = i * bankItemWidth + (bankItemWidth - cellWidth) / 2f;
            drawObstacle(canvas, bx, bankY, cellWidth, cellHeight, String.valueOf(i), 0, (draggingId == i));
        }

        // Grid
        for (int i = 0; i <= gridCountX; i++) {
            float x = startX + i * cellWidth;
            canvas.drawLine(x, startY, x, startY + sideLength, gridPaint);
        }
        for (int j = 0; j <= gridCountY; j++) {
            float y = startY + j * cellHeight;
            canvas.drawLine(startX, y, startX + sideLength, y, gridPaint);
        }
        canvas.drawRect(startX, startY, startX + sideLength, startY + sideLength, arenaPaint);

        // Labels
        for (int i = 0; i < gridCountX; i++) {
            canvas.drawText(String.valueOf(i), startX + (i + 0.5f) * cellWidth, startY + sideLength + 25f, labelPaint);
        }
        for (int j = 0; j < gridCountY; j++) {
            canvas.drawText(String.valueOf(j), startX - 20f, startY + (gridCountY - 1 - j + 0.65f) * cellHeight, labelPaint);
        }

        // Obstacles
        for (Obstacle obs : obstacles) {
            float ox = startX + obs.x * cellWidth;
            float oy = startY + (gridCountY - 1 - obs.y) * cellHeight;
            String display = obs.value.equals("none") ? String.valueOf(obs.id) : obs.value;
            drawObstacle(canvas, ox, oy, cellWidth, cellHeight, display, obs.direction, false);
        }

        // Dragging
        if (draggingId != -1) {
            drawObstacle(canvas, dragCurrentX - cellWidth/2, dragCurrentY - cellHeight/2, cellWidth, cellHeight, String.valueOf(draggingId), 0, true);
        }

        // Robot (3x3)
        if (robotX >= 0 && robotY >= 0) {
            float rx = startX + (robotX + 0.5f) * cellWidth;
            float ry = startY + (gridCountY - 1 - robotY + 0.5f) * cellHeight;
            drawRobot(canvas, rx, ry, robotRotation, cellWidth, cellHeight);
        }
    }

    private void drawObstacle(Canvas canvas, float x, float y, float w, float h, String label, int dir, boolean isDragging) {
        canvas.drawRect(x, y, x + w, y + h, obstaclePaint);
        canvas.drawText(label, x + w/2, y + h/2 + 7f, textPaint);
        
        float p = 2f; 
        float thickness = h * 0.15f;
        facePaint.setColor(Color.RED);
        switch(dir) {
            case 0: canvas.drawRect(x+p, y+p, x+w-p, y+p+thickness, facePaint); break; // N
            case 1: canvas.drawRect(x+w-p-thickness, y+p, x+w-p, y+h-p, facePaint); break; // E
            case 2: canvas.drawRect(x+p, y+h-p-thickness, x+w-p, y+h-p, facePaint); break; // S
            case 3: canvas.drawRect(x+p, y+p, x+p+thickness, y+h-p, facePaint); break; // W
        }
        
        if (isDragging) {
            Paint border = new Paint();
            border.setColor(Color.BLUE);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(3f);
            canvas.drawRect(x, y, x + w, y + h, border);
        }
    }

    private void drawRobot(Canvas canvas, float x, float y, float rotation, float cellW, float cellH) {
        canvas.save();
        canvas.translate(x, y);
        canvas.rotate(rotation);
        
        float bodySize = cellW * 3.0f;
        canvas.drawRect(-bodySize/2, -bodySize/2, bodySize/2, bodySize/2, robotPaint);
        
        // Direction indicator
        Paint ind = new Paint();
        ind.setColor(Color.YELLOW);
        canvas.drawCircle(0, -bodySize/3, bodySize/10, ind);
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                if (y >= bankY && y <= bankY + cellHeight) {
                    int id = (int) (x / bankItemWidth);
                    if (id >= 0 && id < 10) {
                        boolean alreadyPlaced = false;
                        for (Obstacle o : obstacles) if (o.id == id) { alreadyPlaced = true; break; }
                        if (!alreadyPlaced) {
                            draggingId = id;
                            dragCurrentX = x;
                            dragCurrentY = y;
                            invalidate();
                            return true;
                        }
                    }
                }
                if (x >= startX && x <= startX + sideLength && y >= startY && y <= startY + sideLength) {
                    int gx = (int) ((x - startX) / cellWidth);
                    int gy = (gridCountY - 1) - (int) ((y - startY) / cellHeight);
                    for (Obstacle o : obstacles) {
                        if ((int)o.x == gx && (int)o.y == gy) {
                            saveState();
                            o.direction = (o.direction + 1) % 4;
                            invalidate();
                            return true;
                        }
                    }
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (draggingId != -1) {
                    dragCurrentX = x;
                    dragCurrentY = y;
                    invalidate();
                    return true;
                }
                break;

            case MotionEvent.ACTION_UP:
                if (draggingId != -1) {
                    if (x >= startX && x <= startX + sideLength && y >= startY && y <= startY + sideLength) {
                        int gx = (int) ((x - startX) / cellWidth);
                        int gy = (gridCountY - 1) - (int) ((y - startY) / cellHeight);

                        // Overlap check (3x3 robot)
                        boolean overlap = false;
                        if (gx >= robotX-1 && gx <= robotX+1 && gy >= robotY-1 && gy <= robotY+1) overlap = true;
                        for (Obstacle o : obstacles) if ((int)o.x == gx && (int)o.y == gy) overlap = true;
                        
                        if (!overlap) {
                            saveState();
                            obstacles.add(new Obstacle(draggingId, gx, gy));
                        }
                    }
                    draggingId = -1;
                    invalidate();
                    return true;
                }
                break;
        }
        return super.onTouchEvent(event);
    }
}
