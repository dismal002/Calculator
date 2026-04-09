package com.dismal.calculator.math;

import android.os.AsyncTask;
import com.dismal.calculator.math.Point;
import com.dismal.calculator.CalculatorExpressionEvaluator;
import org.javia.arity.Function;
import org.javia.arity.Symbols;
import org.javia.arity.SyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class GraphModule {
    private static final char VAR_X = 'x';
    private static final char VAR_Y = 'y';
    private double mMaxX;
    private double mMaxY;
    private double mMinX;
    private double mMinY;
    private float mZoomLevel = 1.0f;
    private boolean mIsDegreeMode;
    private final Symbols mSymbols;

    private class GraphTask extends AsyncTask<String, String, List<Point>> {
        private final OnGraphUpdatedListener mListener;
        private final double mMaxX;
        private final double mMaxY;
        private final double mMinX;
        private final double mMinY;
        private final float mZoomLevel;
        private final boolean mIsDegreeMode;

        GraphTask(double minY, double maxY, double minX, double maxX, float zoomLevel, boolean isDegreeMode, OnGraphUpdatedListener onGraphUpdatedListener) {
            this.mListener = onGraphUpdatedListener;
            this.mMinY = minY;
            this.mMaxY = maxY;
            this.mMinX = minX;
            this.mMaxX = maxX;
            this.mZoomLevel = zoomLevel;
            this.mIsDegreeMode = isDegreeMode;
        }

        @Override
        protected List<Point> doInBackground(String... strArr) {
            String expr = strArr[0];
            expr = CalculatorExpressionEvaluator.expandAbsoluteValues(expr);
            
            // Handle Ordered Pairs (e.g. "(5, 10)")
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("^\\s*\\((.+),(.+)\\)\\s*$");
            java.util.regex.Matcher matcher = pattern.matcher(expr);
            if (matcher.matches()) {
                try {
                    double x = mSymbols.eval(matcher.group(1));
                    double y = mSymbols.eval(matcher.group(2));
                    List<Point> pts = new ArrayList<>();
                    pts.add(new Point(x, y));
                    return pts;
                } catch (SyntaxException e) {
                    return null;
                }
            }

            if (mIsDegreeMode) {
                expr = expr.replace("sin(", "sin((pi/180)*(");
                expr = expr.replace("cos(", "cos((pi/180)*(");
                expr = expr.replace("tan(", "tan((pi/180)*(");
                expr = expr.replace("asin(", "(180/pi)*asin(");
                expr = expr.replace("acos(", "(180/pi)*acos(");
                expr = expr.replace("atan(", "(180/pi)*atan(");
            }
            try {
                final int eq = expr.indexOf('=');
                if (eq >= 0) {
                    final String left = expr.substring(0, eq).trim();
                    final String right = expr.substring(eq + 1).trim();
                    if (!left.isEmpty() && !right.isEmpty()) {
                        // Special-case explicit function form "y = f(x)".
                        if ("y".equalsIgnoreCase(left)) {
                            final List<Character> rhsVars = extractSingleLetterVariables(right);
                            if (!rhsVars.contains(VAR_Y) && rhsVars.size() <= 1) {
                                return graphExplicit(right, rhsVars);
                            }
                        }
                        return graphImplicit(left, right);
                    }
                }

                final List<Character> vars = extractSingleLetterVariables(expr);
                if (vars.size() <= 1) {
                    return graphExplicit(expr, vars);
                } else if (vars.size() == 2) {
                    // Interpret bare 2-variable expressions like "x^2+y^2-1" as "=0".
                    return graphImplicit(expr, "0");
                }
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        private List<Point> graphExplicit(String expr, List<Character> vars) {
            LinkedList<Point> linkedList = new LinkedList<>();
            try {
                final char independent = vars.isEmpty() ? VAR_X : vars.get(0);
                final Map<Character, Character> mapping = new HashMap<>();
                mapping.put(independent, VAR_X);

                final Function function = mSymbols.compile(remapSingleLetterVariables(expr, mapping));
                final int arity = function.arity();
                
                double step = mZoomLevel * (mMaxX - mMinX) / 500.0; // Dynamic step based on range
                if (step <= 0) step = 0.1;
                
                for (double x = mMinX; x <= mMaxX; x += step) {
                    if (isCancelled()) return null;
                    try {
                        double y;
                        if (arity == 0) {
                            y = function.eval();
                        } else if (arity == 1) {
                            y = function.eval(x);
                        } else {
                            // Needs 2+ dimensions; not plottable as y(x) without extra UI.
                            continue;
                        }
                        if (!Double.isNaN(y) && !Double.isInfinite(y)) {
                            linkedList.add(new Point(x, y));
                        }
                    } catch (Exception ignored) {}
                }
            } catch (SyntaxException ignored) {}
            return Collections.unmodifiableList(linkedList);
        }

        private List<Point> graphImplicit(String side1, String side2) {
            List<Point> linkedList = new LinkedList<>();
            try {
                final List<Character> vars = extractSingleLetterVariables(side1 + " " + side2);
                if (vars.size() > 2) {
                    return null;
                }

                final Map<Character, Character> mapping = new HashMap<>();
                if (vars.size() == 2) {
                    final char v0 = vars.get(0);
                    final char v1 = vars.get(1);
                    // Prefer keeping x/y if they're present.
                    if ((v0 == VAR_X && v1 == VAR_Y) || (v0 == VAR_Y && v1 == VAR_X)) {
                        mapping.put(VAR_X, VAR_X);
                        mapping.put(VAR_Y, VAR_Y);
                    } else if (v0 == VAR_X) {
                        mapping.put(VAR_X, VAR_X);
                        mapping.put(v1, VAR_Y);
                    } else if (v1 == VAR_X) {
                        mapping.put(VAR_X, VAR_X);
                        mapping.put(v0, VAR_Y);
                    } else {
                        mapping.put(v0, VAR_X);
                        mapping.put(v1, VAR_Y);
                    }
                } else if (vars.size() == 1) {
                    mapping.put(vars.get(0), VAR_X);
                }

                final Function f1 = mSymbols.compile(remapSingleLetterVariables(side1, mapping));
                final Function f2 = mSymbols.compile(remapSingleLetterVariables(side2, mapping));

                // If both sides only depend on one variable, interpret "f(x)=g(x)" as
                // intersection points and plot those points (x, f(x)).
                if (f1.arity() <= 1 && f2.arity() <= 1) {
                    return graphEqualityIntersections(f1, f2);
                }

                double step = mZoomLevel * 0.2; // Coarser step for implicit
                for (double x = mMinX; x <= mMaxX; x += step) {
                    for (double y = mMinY; y <= mMaxY; y += step) {
                        if (isCancelled()) return null;
                        try {
                            // This is a very rough approach to implicit functions
                            // In a real app, you'd use a more sophisticated algorithm
                            final double v1 = eval2D(f1, x, y);
                            final double v2 = eval2D(f2, x, y);
                            if (Double.isFinite(v1) && Double.isFinite(v2)
                                    && Math.abs(v1 - v2) < 0.1 * mZoomLevel) {
                                linkedList.add(new Point(x, y));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            } catch (SyntaxException ignored) {}
            return Collections.unmodifiableList(linkedList);
        }

        private double eval2D(Function f, double x, double y) {
            final int arity = f.arity();
            if (arity == 0) return f.eval();
            if (arity == 1) return f.eval(x);
            if (arity == 2) return f.eval(x, y);
            return Double.NaN;
        }

        private List<Point> graphEqualityIntersections(Function f1, Function f2) {
            final LinkedList<Point> points = new LinkedList<>();

            double step = mZoomLevel * (mMaxX - mMinX) / 500.0;
            if (step <= 0) step = 0.1;

            double prevX = Double.NaN;
            double prevD = Double.NaN;
            for (double x = mMinX; x <= mMaxX; x += step) {
                if (isCancelled()) return null;

                double d;
                try {
                    d = eval1D(f1, x) - eval1D(f2, x);
                } catch (Exception ignored) {
                    prevX = Double.NaN;
                    prevD = Double.NaN;
                    continue;
                }

                if (!Double.isFinite(d)) {
                    prevX = Double.NaN;
                    prevD = Double.NaN;
                    continue;
                }

                if (Double.isFinite(prevD)) {
                    final boolean signChanged = (prevD > 0 && d < 0) || (prevD < 0 && d > 0);
                    final boolean nearZero = Math.abs(d) < 1e-6 || Math.abs(prevD) < 1e-6;
                    if (signChanged || nearZero) {
                        final double root = refineRootBisection(f1, f2, prevX, x);
                        if (Double.isFinite(root)) {
                            try {
                                final double y = eval1D(f1, root);
                                if (Double.isFinite(y)) {
                                    points.add(new Point(root, y));
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                prevX = x;
                prevD = d;
            }

            return Collections.unmodifiableList(points);
        }

        private double refineRootBisection(Function f1, Function f2, double a, double b) {
            double fa = eval1DQuiet(f1, f2, a);
            double fb = eval1DQuiet(f1, f2, b);
            if (!Double.isFinite(fa) || !Double.isFinite(fb)) return Double.NaN;
            if (fa == 0.0) return a;
            if (fb == 0.0) return b;
            if ((fa > 0 && fb > 0) || (fa < 0 && fb < 0)) {
                return (Math.abs(fa) < Math.abs(fb)) ? a : b;
            }

            double lo = a;
            double hi = b;
            for (int i = 0; i < 30; i++) {
                double mid = 0.5 * (lo + hi);
                double fm = eval1DQuiet(f1, f2, mid);
                if (!Double.isFinite(fm)) break;
                if (Math.abs(fm) < 1e-10) return mid;
                if ((fa > 0 && fm > 0) || (fa < 0 && fm < 0)) {
                    lo = mid;
                    fa = fm;
                } else {
                    hi = mid;
                    fb = fm;
                }
            }
            return 0.5 * (lo + hi);
        }

        private double eval1DQuiet(Function f1, Function f2, double x) {
            try {
                return eval1D(f1, x) - eval1D(f2, x);
            } catch (Exception e) {
                return Double.NaN;
            }
        }

        private double eval1D(Function f, double x) {
            final int arity = f.arity();
            if (arity == 0) return f.eval();
            if (arity == 1) return f.eval(x);
            return Double.NaN;
        }

        private List<Character> extractSingleLetterVariables(String expr) {
            // Arity supports x/y as arguments. Treat 'e' as Euler's constant, not a variable.
            boolean[] present = new boolean[26];
            for (int i = 0; i < expr.length(); i++) {
                final char c = expr.charAt(i);
                if (!isAsciiLetter(c)) continue;

                final char lower = (char) (c >= 'A' && c <= 'Z' ? (c - 'A' + 'a') : c);
                if (lower == 'e') continue;

                final char prev = i > 0 ? expr.charAt(i - 1) : '\0';
                final char next = i + 1 < expr.length() ? expr.charAt(i + 1) : '\0';

                if (isAsciiLetterOrDigit(prev) || prev == '_') continue;
                if (isAsciiLetterOrDigit(next) || next == '_') continue;

                present[lower - 'a'] = true;
            }

            final List<Character> out = new ArrayList<>(2);
            for (int i = 0; i < present.length; i++) {
                if (present[i]) out.add((char) ('a' + i));
            }

            if (out.size() > 1) {
                Character[] arr = out.toArray(new Character[0]);
                Arrays.sort(arr, (a, b) -> {
                    if (a == b) return 0;
                    if (a == VAR_X) return -1;
                    if (b == VAR_X) return 1;
                    if (a == VAR_Y) return -1;
                    if (b == VAR_Y) return 1;
                    return a.compareTo(b);
                });
                out.clear();
                out.addAll(Arrays.asList(arr));
            }

            return out;
        }

        private String remapSingleLetterVariables(String expr, Map<Character, Character> mapping) {
            if (mapping.isEmpty()) return expr;

            final StringBuilder out = new StringBuilder(expr.length() + 8);
            for (int i = 0; i < expr.length(); i++) {
                final char c = expr.charAt(i);
                if (!isAsciiLetter(c)) {
                    out.append(c);
                    continue;
                }

                final char lower = (char) (c >= 'A' && c <= 'Z' ? (c - 'A' + 'a') : c);
                final char prev = i > 0 ? expr.charAt(i - 1) : '\0';
                final char next = i + 1 < expr.length() ? expr.charAt(i + 1) : '\0';

                if (isAsciiLetterOrDigit(prev) || prev == '_' || isAsciiLetterOrDigit(next) || next == '_') {
                    out.append(c);
                    continue;
                }

                final Character mapped = mapping.get(lower);
                out.append(mapped != null ? mapped : c);
            }
            return out.toString();
        }

        private boolean isAsciiLetter(char c) {
            return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
        }

        private boolean isAsciiLetterOrDigit(char c) {
            return isAsciiLetter(c) || (c >= '0' && c <= '9');
        }

        @Override
        protected void onPostExecute(List<Point> list) {
            if (list != null) {
                this.mListener.onGraphUpdated(list);
            }
        }
    }

    public interface OnGraphUpdatedListener {
        void onGraphUpdated(List<Point> list);
    }

    public GraphModule(Symbols symbols) {
        this.mSymbols = symbols;
    }

    public void setDomain(float minX, float maxX) {
        this.mMinX = minX;
        this.mMaxX = maxX;
    }

    public void setRange(float minY, float maxY) {
        this.mMinY = minY;
        this.mMaxY = maxY;
    }

    public void setZoomLevel(float zoomLevel) {
        this.mZoomLevel = zoomLevel;
    }

    public void setIsDegreeMode(boolean isDegreeMode) {
        this.mIsDegreeMode = isDegreeMode;
    }

    public AsyncTask updateGraph(String expr, OnGraphUpdatedListener listener) {
        if (expr == null || expr.isEmpty()) return null;
        GraphTask task = new GraphTask(mMinY, mMaxY, mMinX, mMaxX, mZoomLevel, mIsDegreeMode, listener);
        task.execute(expr);
        return task;
    }
}
