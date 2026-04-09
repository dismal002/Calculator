package com.dismal.calculator.view;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import com.dismal.calculator.R;
import com.dismal.calculator.math.Point;
import androidx.core.content.ContextCompat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class GraphView extends View {
    private static final float AXIS_WIDTH = 2.0f;
    private static final float GRAPH_WIDTH = 3.0f;
    private static final float GRID_WIDTH = 1.0f;
    
    private Paint mAxisPaint;
    private Paint mBackgroundPaint;
    private Paint mGraphPaint;
    private Paint mTextPaint;
    private Paint mTooltipBgPaint;
    private Paint mTooltipTextPaint;
    private Paint mTooltipPointPaint;
    
    private final List<Graph> mData = new ArrayList<>();
    private final DecimalFormat mFormat = new DecimalFormat("#.###");
    private final Rect mTempRect = new Rect();
    private final RectF mTempRectF = new RectF();
    
    // View state
    private double mCenterWorldX = 0;
    private double mCenterWorldY = 0;
    private float mZoomLevel = 1.0f; // Lower is more zoomed in
    private float mPixelsPerUnit = 100.0f; // Base pixels per unit

    // Interaction state
    private float mStartX, mStartY;
    private double mStartWorldX, mStartWorldY;
    private float mDownX, mDownY;
    private boolean mExceededTapSlop;
    private int mMode = 0;
    private static final int DRAG = 1;
    private static final int ZOOM = 2;
    private double mZoomInitDistance;
    private float mZoomInitLevel;

    // Tooltip state
    private boolean mTooltipVisible = false;
    private double mTooltipWorldX;
    private double mTooltipWorldY;
    private int mTooltipColor = Color.BLACK;
    private final Runnable mHideTooltipRunnable = new Runnable() {
        @Override public void run() {
            mTooltipVisible = false;
            invalidate();
        }
    };

    // Config
    private boolean mShowGrid = true;
    private boolean mShowOutline = true;
    private boolean mPanEnabled = true;
    private boolean mZoomEnabled = true;
    private int mLineMargin;
    private int mTextMargin;
    private int mTextPaintSize;
    private int mTouchSlop;
    private float mPointHitRadiusPx;
    private float mLineHitRadiusPx;
    private float mTooltipPaddingPx;
    private float mTooltipCornerRadiusPx;
    private float mTooltipOffsetPx;
    private float mTooltipMarkerRadiusPx;

    private final List<PanListener> mPanListeners = new ArrayList<>();
    private final List<ZoomListener> mZoomListeners = new ArrayList<>();

    public static class Graph {
        public int color;
        public List<Point> data;
        private String formula;
        public boolean visible = true;

        public Graph(String str, int i, List<Point> list) {
            this.formula = str;
            this.color = i;
            this.data = list;
        }

        public int getColor() { return color; }
        public List<Point> getData() { return data; }
        public String getFormula() { return formula; }
        public boolean isVisible() { return visible; }
        public void setData(List<Point> list) { this.data = list; }
    }

    public interface PanListener { void panApplied(); }
    public interface ZoomListener { void zoomApplied(float zoom); }

    public GraphView(Context context) { super(context); setup(context, null); }
    public GraphView(Context context, AttributeSet attributeSet) { super(context, attributeSet); setup(context, attributeSet); }
    public GraphView(Context context, AttributeSet attributeSet, int i) { super(context, attributeSet, i); setup(context, attributeSet); }

    private void setup(Context context, AttributeSet attributeSet) {
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setColor(ContextCompat.getColor(context, R.color.graph_background));
        mBackgroundPaint.setStyle(Paint.Style.FILL);

        mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTextPaint.setColor(ContextCompat.getColor(context, R.color.graph_text));
        mTextPaintSize = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12, getResources().getDisplayMetrics());
        mTextPaint.setTextSize(mTextPaintSize);

        mAxisPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mAxisPaint.setColor(ContextCompat.getColor(context, R.color.graph_axis));
        mAxisPaint.setStyle(Paint.Style.STROKE);

        mGraphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGraphPaint.setStyle(Paint.Style.STROKE);
        mGraphPaint.setStrokeCap(Paint.Cap.ROUND);
        mGraphPaint.setStrokeJoin(Paint.Join.ROUND);

        mTooltipBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTooltipBgPaint.setColor(0xCC000000);
        mTooltipBgPaint.setStyle(Paint.Style.FILL);

        mTooltipTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTooltipTextPaint.setColor(Color.WHITE);
        mTooltipTextPaint.setTextSize(mTextPaintSize);

        mTooltipPointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mTooltipPointPaint.setStyle(Paint.Style.FILL);

        mLineMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 25, getResources().getDisplayMetrics());
        mTextMargin = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mPointHitRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        mLineHitRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        mTooltipPaddingPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
        mTooltipCornerRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, getResources().getDisplayMetrics());
        mTooltipOffsetPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
        mTooltipMarkerRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 4, getResources().getDisplayMetrics());

        if (attributeSet != null) {
            TypedArray a = context.obtainStyledAttributes(attributeSet, R.styleable.GraphView);
            mShowGrid = a.getBoolean(R.styleable.GraphView_gv_show_grid, true);
            mShowOutline = a.getBoolean(R.styleable.GraphView_gv_show_outline, true);
            mPanEnabled = a.getBoolean(R.styleable.GraphView_gv_pan_enabled, true);
            mZoomEnabled = a.getBoolean(R.styleable.GraphView_gv_zoom_enabled, true);
            a.recycle();
        }
    }

    public void zoomReset() {
        mCenterWorldX = 0;
        mCenterWorldY = 0;
        mZoomLevel = 1.0f;
        invalidate();
    }

    private float getEffectivePixelsPerUnit() {
        return mPixelsPerUnit / mZoomLevel;
    }

    public float getRawX(double worldX) {
        float centerX = getWidth() / 2f;
        return (float) (centerX + (worldX - mCenterWorldX) * getEffectivePixelsPerUnit());
    }

    public float getRawY(double worldY) {
        float centerY = getHeight() / 2f;
        return (float) (centerY - (worldY - mCenterWorldY) * getEffectivePixelsPerUnit());
    }

    public double getWorldX(float rawX) {
        float centerX = getWidth() / 2f;
        return mCenterWorldX + (rawX - centerX) / getEffectivePixelsPerUnit();
    }

    public double getWorldY(float rawY) {
        float centerY = getHeight() / 2f;
        return mCenterWorldY - (rawY - centerY) / getEffectivePixelsPerUnit();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawPaint(mBackgroundPaint);

        float width = getWidth();
        float height = getHeight();
        float ppu = getEffectivePixelsPerUnit();

        // Draw grid
        if (mShowGrid) {
            drawGrid(canvas, width, height, ppu);
        }

        // Draw axes
        drawAxes(canvas, width, height);

        // Draw graphs
        mGraphPaint.setStrokeWidth(GRAPH_WIDTH);
        for (Graph graph : mData) {
            if (graph.visible && graph.data != null && !graph.data.isEmpty()) {
                mGraphPaint.setColor(graph.color);
                drawGraphLine(canvas, graph.data);
            }
        }

        // Draw outline
        if (mShowOutline) {
            mAxisPaint.setStrokeWidth(AXIS_WIDTH);
            canvas.drawRect(0, 0, width, height, mAxisPaint);
        }

        // Draw tooltip last so it stays on top.
        if (mTooltipVisible) {
            drawTooltip(canvas);
        }
    }

    private void drawGrid(Canvas canvas, float width, float height, float ppu) {
        mAxisPaint.setStrokeWidth(GRID_WIDTH);
        
        // Calculate grid spacing based on zoom
        double spacing = 1.0;
        while (spacing * ppu < 50) spacing *= 2;
        while (spacing * ppu > 200) spacing /= 2;

        double startX = Math.floor(getWorldX(0) / spacing) * spacing;
        double endX = getWorldX(width);
        for (double x = startX; x <= endX; x += spacing) {
            float rx = getRawX(x);
            canvas.drawLine(rx, 0, rx, height, mAxisPaint);
            
            // Draw labels
            String label = mFormat.format(x);
            mTextPaint.getTextBounds(label, 0, label.length(), mTempRect);
            canvas.drawText(label, rx + mTextMargin, height - mTextMargin, mTextPaint);
        }

        double startY = Math.floor(getWorldY(height) / spacing) * spacing;
        double endY = getWorldY(0);
        for (double y = startY; y <= endY; y += spacing) {
            float ry = getRawY(y);
            canvas.drawLine(0, ry, width, ry, mAxisPaint);
            
            // Draw labels
            String label = mFormat.format(y);
            mTextPaint.getTextBounds(label, 0, label.length(), mTempRect);
            canvas.drawText(label, mTextMargin, ry - mTextMargin, mTextPaint);
        }
    }

    private void drawAxes(Canvas canvas, float width, float height) {
        mAxisPaint.setStrokeWidth(AXIS_WIDTH * 2);
        float originX = getRawX(0);
        float originY = getRawY(0);

        if (originX >= 0 && originX <= width) {
            canvas.drawLine(originX, 0, originX, height, mAxisPaint);
        }
        if (originY >= 0 && originY <= height) {
            canvas.drawLine(0, originY, width, originY, mAxisPaint);
        }
    }

    private void drawGraphLine(Canvas canvas, List<Point> points) {
        float width = (float) getWidth();
        float height = (float) getHeight();
        if (width <= 0 || height <= 0) return;

        // Use dynamic margins based on screen size to stay within GPU texture limits (typically 2048/4096)
        float marginX = width;
        float marginY = height;
        
        if (points.size() == 1) {
            Point p = points.get(0);
            float x = getRawX(p.getX());
            float y = getRawY(p.getY());
            canvas.drawCircle(Math.max(-marginX, Math.min(width + marginX, x)), 
                              Math.max(-marginY, Math.min(height + marginY, y)), 
                              GRAPH_WIDTH * 3, mGraphPaint);
            return;
        }

        Path path = new Path();
        boolean first = true;
        float lastY = Float.NaN;

        for (Point p : points) {
            float x = getRawX(p.getX());
            float y = getRawY(p.getY());
            
            if (Float.isNaN(y) || Float.isInfinite(y) || Float.isNaN(x) || Float.isInfinite(x)) {
                first = true;
                continue;
            }

            // Detect extreme jumps (asymptotes) to break the path
            if (!first && !Float.isNaN(lastY)) {
                if (Math.abs(y - lastY) > height * 2.5f) {
                    first = true;
                }
            }

            // Clamp coordinates to safe limits for the GPU
            float clampedX = Math.max(-marginX, Math.min(width + marginX, x));
            float clampedY = Math.max(-marginY, Math.min(height + marginY, y));

            if (first) {
                path.moveTo(clampedX, clampedY);
                first = false;
            } else {
                path.lineTo(clampedX, clampedY);
            }
            lastY = y;
        }
        canvas.drawPath(path, mGraphPaint);
    }

    private void drawTooltip(Canvas canvas) {
        float px = getRawX(mTooltipWorldX);
        float py = getRawY(mTooltipWorldY);

        mTooltipPointPaint.setColor(mTooltipColor);
        canvas.drawCircle(px, py, mTooltipMarkerRadiusPx, mTooltipPointPaint);

        String label = "(" + mFormat.format(mTooltipWorldX) + ", " + mFormat.format(mTooltipWorldY) + ")";
        mTooltipTextPaint.getTextBounds(label, 0, label.length(), mTempRect);

        float textW = mTempRect.width();
        float textH = mTempRect.height();
        float boxW = textW + mTooltipPaddingPx * 2;
        float boxH = textH + mTooltipPaddingPx * 2;

        float left = px + mTooltipOffsetPx;
        float top = py - boxH - mTooltipOffsetPx;

        // Keep tooltip in-bounds.
        if (left + boxW > getWidth()) left = getWidth() - boxW - mTooltipOffsetPx;
        if (left < 0) left = mTooltipOffsetPx;
        if (top < 0) top = py + mTooltipOffsetPx;
        if (top + boxH > getHeight()) top = getHeight() - boxH - mTooltipOffsetPx;

        mTempRectF.set(left, top, left + boxW, top + boxH);
        canvas.drawRoundRect(mTempRectF, mTooltipCornerRadiusPx, mTooltipCornerRadiusPx, mTooltipBgPaint);

        float tx = left + mTooltipPaddingPx;
        float ty = top + mTooltipPaddingPx - mTempRect.top; // baseline
        canvas.drawText(label, tx, ty, mTooltipTextPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!mPanEnabled && !mZoomEnabled) return super.onTouchEvent(event);

        int action = event.getActionMasked();
        int pointerCount = event.getPointerCount();

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mMode = DRAG;
                mStartX = event.getX();
                mStartY = event.getY();
                mDownX = mStartX;
                mDownY = mStartY;
                mExceededTapSlop = false;
                mStartWorldX = mCenterWorldX;
                mStartWorldY = mCenterWorldY;
                break;

            case MotionEvent.ACTION_POINTER_DOWN:
                if (pointerCount == 2) {
                    mMode = ZOOM;
                    mZoomInitDistance = getDistance(event);
                    mZoomInitLevel = mZoomLevel;
                    hideTooltip();
                }
                break;

            case MotionEvent.ACTION_MOVE:
                if (mMode == DRAG && mPanEnabled) {
                    float dx = event.getX() - mStartX;
                    float dy = event.getY() - mStartY;
                    if (!mExceededTapSlop) {
                        float totalDx = event.getX() - mDownX;
                        float totalDy = event.getY() - mDownY;
                        if ((totalDx * totalDx + totalDy * totalDy) > (mTouchSlop * mTouchSlop)) {
                            mExceededTapSlop = true;
                            hideTooltip();
                        }
                    }
                    if (mExceededTapSlop) {
                        mCenterWorldX = mStartWorldX - (dx / getEffectivePixelsPerUnit());
                        mCenterWorldY = mStartWorldY + (dy / getEffectivePixelsPerUnit());
                        invalidate();
                    }
                } else if (mMode == ZOOM && mZoomEnabled && pointerCount == 2) {
                    double dist = getDistance(event);
                    if (dist > 10) {
                        mZoomLevel = (float) (mZoomInitLevel * (mZoomInitDistance / dist));
                        invalidate();
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                if (action == MotionEvent.ACTION_UP) {
                    if (mMode == DRAG && !mExceededTapSlop) {
                        handleTap(event.getX(), event.getY());
                    } else {
                        mMode = 0;
                        notifyPan();
                    }
                    mMode = 0;
                } else if (pointerCount == 2) {
                    mMode = DRAG; // Fall back to drag if one finger lifted
                    // Re-anchor drag start to current position to avoid jumps
                    mStartX = event.getX(event.getActionIndex() == 0 ? 1 : 0);
                    mStartY = event.getY(event.getActionIndex() == 0 ? 1 : 0);
                    mStartWorldX = mCenterWorldX;
                    mStartWorldY = mCenterWorldY;
                    notifyZoom();
                }
                break;
        }
        return true;
    }

    private void hideTooltip() {
        removeCallbacks(mHideTooltipRunnable);
        mTooltipVisible = false;
        invalidate();
    }

    private void handleTap(float rawX, float rawY) {
        HitResult hit = findClosestHit(rawX, rawY);
        if (hit == null) {
            hideTooltip();
            invalidate();
            return;
        }

        mTooltipWorldX = hit.worldX;
        mTooltipWorldY = hit.worldY;
        mTooltipColor = hit.color;
        mTooltipVisible = true;
        invalidate();

        removeCallbacks(mHideTooltipRunnable);
        postDelayed(mHideTooltipRunnable, 2500);
    }

    private static final class HitResult {
        final double worldX;
        final double worldY;
        final int color;
        final float distSq;

        HitResult(double worldX, double worldY, int color, float distSq) {
            this.worldX = worldX;
            this.worldY = worldY;
            this.color = color;
            this.distSq = distSq;
        }
    }

    private HitResult findClosestHit(float rawX, float rawY) {
        if (mData.isEmpty()) return null;

        float bestDistSq = Float.POSITIVE_INFINITY;
        HitResult best = null;

        float pointRadiusSq = mPointHitRadiusPx * mPointHitRadiusPx;
        float lineRadiusSq = mLineHitRadiusPx * mLineHitRadiusPx;
        float height = getHeight();

        for (Graph graph : mData) {
            if (!graph.visible || graph.data == null || graph.data.isEmpty()) continue;

            List<Point> pts = graph.data;

            // Point hits
            for (Point p : pts) {
                float px = getRawX(p.getX());
                float py = getRawY(p.getY());
                if (Float.isNaN(px) || Float.isInfinite(px) || Float.isNaN(py) || Float.isInfinite(py)) continue;
                float dx = rawX - px;
                float dy = rawY - py;
                float d2 = dx * dx + dy * dy;
                if (d2 <= pointRadiusSq && d2 < bestDistSq) {
                    bestDistSq = d2;
                    best = new HitResult(p.getX(), p.getY(), graph.color, d2);
                }
            }

            // Line/segment hits
            if (pts.size() < 2) continue;

            Point prev = null;
            float prevRx = Float.NaN;
            float prevRy = Float.NaN;
            for (Point cur : pts) {
                float curRx = getRawX(cur.getX());
                float curRy = getRawY(cur.getY());
                if (Float.isNaN(curRx) || Float.isInfinite(curRx) || Float.isNaN(curRy) || Float.isInfinite(curRy)) {
                    prev = null;
                    continue;
                }

                if (prev != null) {
                    // Break at asymptotes/extreme jumps to match rendering behavior.
                    if (Math.abs(curRy - prevRy) <= height * 2.5f) {
                        Projection proj = projectPointToSegment(rawX, rawY, prevRx, prevRy, curRx, curRy);
                        if (proj != null && proj.distSq <= lineRadiusSq && proj.distSq < bestDistSq) {
                            double wx = prev.getX() + (cur.getX() - prev.getX()) * proj.t;
                            double wy = prev.getY() + (cur.getY() - prev.getY()) * proj.t;
                            bestDistSq = proj.distSq;
                            best = new HitResult(wx, wy, graph.color, proj.distSq);
                        }
                    }
                }

                prev = cur;
                prevRx = curRx;
                prevRy = curRy;
            }
        }

        return best;
    }

    private static final class Projection {
        final float t;
        final float distSq;

        Projection(float t, float distSq) {
            this.t = t;
            this.distSq = distSq;
        }
    }

    private static Projection projectPointToSegment(
            float px, float py,
            float ax, float ay,
            float bx, float by
    ) {
        float vx = bx - ax;
        float vy = by - ay;
        float wx = px - ax;
        float wy = py - ay;

        float vv = vx * vx + vy * vy;
        if (vv <= 1e-6f) return null;

        float t = (wx * vx + wy * vy) / vv;
        if (t < 0f) t = 0f;
        else if (t > 1f) t = 1f;

        float cx = ax + t * vx;
        float cy = ay + t * vy;
        float dx = px - cx;
        float dy = py - cy;
        return new Projection(t, dx * dx + dy * dy);
    }

    private double getDistance(MotionEvent event) {
        float dx = event.getX(0) - event.getX(1);
        float dy = event.getY(0) - event.getY(1);
        return Math.sqrt(dx * dx + dy * dy);
    }

    private void notifyPan() {
        for (PanListener l : mPanListeners) l.panApplied();
    }

    private void notifyZoom() {
        for (ZoomListener l : mZoomListeners) l.zoomApplied(mZoomLevel);
    }

    public void addPanListener(PanListener l) { mPanListeners.add(l); }
    public void addZoomListener(ZoomListener l) { mZoomListeners.add(l); }
    public float getXAxisMin() { return (float) getWorldX(0); }
    public float getXAxisMax() { return (float) getWorldX(getWidth()); }
    public float getYAxisMin() { return (float) getWorldY(getHeight()); }
    public float getYAxisMax() { return (float) getWorldY(0); }
    public float getZoomLevel() { return mZoomLevel; }
    public void addGraph(Graph g) { mData.add(g); invalidate(); }
    public List<Graph> getGraphs() { return mData; }
    public void setZoomLevel(float level) {
        mZoomLevel = level;
        hideTooltip();
        invalidate();
        notifyZoom();
    }

    public void zoomIn() {
        setZoomLevel(mZoomLevel / 1.5f);
    }

    public void zoomOut() {
        setZoomLevel(mZoomLevel * 1.5f);
    }

    public void setPanEnabled(boolean e) { mPanEnabled = e; }
    public void setZoomEnabled(boolean e) { mZoomEnabled = e; }
}
