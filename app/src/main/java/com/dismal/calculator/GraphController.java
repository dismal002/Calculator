package com.dismal.calculator;

import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;
import android.view.ViewTreeObserver;
import com.dismal.calculator.view.GraphView;
import com.dismal.calculator.math.GraphModule;
import com.dismal.calculator.math.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GraphController implements GraphView.PanListener, GraphView.ZoomListener {
    private static final int[][] GRAPH_COLORS = {
            new int[]{-10720320}, new int[]{-13022805}, new int[]{-14142061}, new int[]{-15064194}, new int[]{-15393437}
    };
    private static final int MAX_CACHE_SIZE = 10;
    private static final String TAG = "GraphController";
    private final Map<String, List<Point>> mCachedEquations = new LinkedHashMap<String, List<Point>>(MAX_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(java.util.Map.Entry<String, List<Point>> eldest) {
            return size() > MAX_CACHE_SIZE;
        }
    };
    private final GraphModule mGraphModule;
    private final List<AsyncTask> mGraphTasks = new ArrayList<>();
    private final Handler mHandler = new Handler();
    private final GraphView mMainGraphView;
    private GraphView.Graph mMostRecentGraph;
    private AsyncTask mMostRecentGraphTask;
    private int mNumberOfGraphs = 0;

    public GraphController(GraphModule graphModule, GraphView graphView) {
        this.mGraphModule = graphModule;
        this.mMainGraphView = graphView;
        graphView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                mMainGraphView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                invalidateGraph();
            }
        });
        graphView.addPanListener(this);
        graphView.addZoomListener(this);
    }

    private AsyncTask drawGraph(final GraphView.Graph graph) {
        if (!graph.isVisible()) return null;
        
        if (mCachedEquations.containsKey(graph.getFormula())) {
            graph.setData(mCachedEquations.get(graph.getFormula()));
            this.mMainGraphView.postInvalidate();
        }
        invalidateModule();
        return this.mGraphModule.updateGraph(graph.getFormula(), new GraphModule.OnGraphUpdatedListener() {
            @Override
            public void onGraphUpdated(List<Point> list) {
                if (list != null) {
                    mCachedEquations.put(graph.getFormula(), list);
                    graph.setData(list);
                    mMainGraphView.postInvalidate();
                }
            }
        });
    }

    private synchronized void invalidateGraph() {
        invalidateModule();
        for (AsyncTask task : mGraphTasks) {
            task.cancel(true);
        }
        mGraphTasks.clear();
        for (GraphView.Graph graph : mMainGraphView.getGraphs()) {
            if (graph.isVisible()) {
                AsyncTask task = drawGraph(graph);
                if (task != null) {
                    mGraphTasks.add(task);
                }
            }
        }
        this.mMainGraphView.postInvalidate();
    }

    private boolean mIsDegreeMode;

    public void setIsDegreeMode(boolean isDegreeMode) {
        this.mIsDegreeMode = isDegreeMode;
        invalidateGraph();
    }

    private void invalidateModule() {
        this.mGraphModule.setDomain(this.mMainGraphView.getXAxisMin(), this.mMainGraphView.getXAxisMax());
        this.mGraphModule.setRange(this.mMainGraphView.getYAxisMin(), this.mMainGraphView.getYAxisMax());
        this.mGraphModule.setZoomLevel(this.mMainGraphView.getZoomLevel());
        this.mGraphModule.setIsDegreeMode(mIsDegreeMode);
    }

    public void addNewGraph(String formula) {
        int color = GRAPH_COLORS[mMainGraphView.getGraphs().size() % GRAPH_COLORS.length][0];
        GraphView.Graph graph = new GraphView.Graph(formula, color, new ArrayList<Point>());
        this.mMainGraphView.addGraph(graph);
        invalidateGraph();
    }

    public void removeGraph(GraphView.Graph graph) {
        mMainGraphView.getGraphs().remove(graph);
        mCachedEquations.remove(graph.getFormula());
        invalidateGraph();
    }

    public void clearGraphs() {
        mMainGraphView.getGraphs().clear();
        mCachedEquations.clear();
        invalidateGraph();
    }

    public void redraw() {
        mMainGraphView.postInvalidate();
    }

    @Override
    public void panApplied() {
        invalidateGraph();
    }

    @Override
    public void zoomApplied(float level) {
        invalidateGraph();
    }

    public void destroy() {
        for (AsyncTask task : mGraphTasks) {
            task.cancel(true);
        }
        mGraphTasks.clear();
    }
}
