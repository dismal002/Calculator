/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dismal.calculator;

import org.javia.arity.Symbols;
import org.javia.arity.SyntaxException;
import org.javia.arity.Util;

public class CalculatorExpressionEvaluator {

    /**
     * The maximum number of significant digits to display.
     */
    private static final int MAX_DIGITS = 12;

    /**
     * A {@link Double} has at least 17 significant digits, we show the first {@link #MAX_DIGITS}
     * and use the remaining digits as guard digits to hide floating point precision errors.
     */
    private static final int ROUNDING_DIGITS = Math.max(17 - MAX_DIGITS, 0);

    private final Symbols mSymbols;
    private final CalculatorExpressionTokenizer mTokenizer;

    public CalculatorExpressionEvaluator(CalculatorExpressionTokenizer tokenizer) {
        mSymbols = new Symbols();
        mTokenizer = tokenizer;
    }

    public void evaluate(CharSequence expr, boolean isDegreeMode, EvaluateCallback callback) {
        evaluate(expr.toString(), isDegreeMode, callback);
    }

    public void evaluate(String expr, boolean isDegreeMode, EvaluateCallback callback) {
        expr = mTokenizer.getNormalizedExpression(expr);

        // remove any trailing operators
        while (expr.length() > 0 && "+-/*".indexOf(expr.charAt(expr.length() - 1)) != -1) {
            expr = expr.substring(0, expr.length() - 1);
        }

        try {
            if (expr.length() == 0) {
                callback.onEvaluate(expr, 0.0, null, Calculator.INVALID_RES_ID);
                return;
            }
            double val = Double.parseDouble(expr);
            callback.onEvaluate(expr, val, mTokenizer.getLocalizedExpression(expr), Calculator.INVALID_RES_ID);
            return;
        } catch (NumberFormatException e) {
            // expr is not a simple number
        }

        try {
            expr = expandAbsoluteValues(expr);
            expr = expandPercents(expr);
            if (isDegreeMode) {
                // To handle degree mode in arity, we pre-process the expression string.
                // We wrap trigonometric functions with conversion factors:
                // sin(x) -> sin((pi/180)*(x))
                // asin(x) -> (180/pi)*asin(x)
                // We use simple replacements here assuming the user uses parentheses after functions.
                expr = expr.replace("sin(", "sin((pi/180)*(");
                expr = expr.replace("cos(", "cos((pi/180)*(");
                expr = expr.replace("tan(", "tan((pi/180)*(");
                expr = expr.replace("asin(", "(180/pi)*asin(");
                expr = expr.replace("acos(", "(180/pi)*acos(");
                expr = expr.replace("atan(", "(180/pi)*atan(");
            }

            double result = mSymbols.eval(expr);
            if (Double.isNaN(result)) {
                callback.onEvaluate(expr, Double.NaN, null, R.string.error_nan);
            } else {
                // The arity library uses floating point arithmetic when evaluating the expression
                // leading to precision errors in the result. The method doubleToString hides these
                // errors; rounding the result by dropping N digits of precision.
                final String resultString = mTokenizer.getLocalizedExpression(
                        Util.doubleToString(result, MAX_DIGITS, ROUNDING_DIGITS));
                callback.onEvaluate(expr, result, resultString, Calculator.INVALID_RES_ID);
            }
        } catch (SyntaxException e) {
            // If the expression is syntactically valid but contains variables, it can't be
            // evaluated to a single number here. Treat it as "not evaluatable" instead of error.
            try {
                if (mSymbols.compile(expr).arity() > 0) {
                    callback.onEvaluate(expr, Double.NaN, null, Calculator.INVALID_RES_ID);
                    return;
                }
            } catch (SyntaxException ignored) {
                // Fall through to syntax error.
            }
            callback.onEvaluate(expr, Double.NaN, null, R.string.error_syntax);
        }
    }

    private static String expandPercents(String expr) {
        if (expr.indexOf('%') < 0) {
            return expr;
        }
        final StringBuilder out = new StringBuilder(expr.length() + 8);
        for (int i = 0; i < expr.length(); i++) {
            final char c = expr.charAt(i);
            if (c == '%') {
                out.append("/100");
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Expands pairs of '|' into 'abs(' and ')'.
     * Auto-closes unmatched bounds.
     */
    public static String expandAbsoluteValues(String expr) {
        if (expr.indexOf('|') < 0) {
            return expr;
        }
        StringBuilder out = new StringBuilder(expr.length() + 8);
        boolean isOpen = false;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '|') {
                if (isOpen) {
                    out.append(')');
                } else {
                    out.append("abs(");
                }
                isOpen = !isOpen;
            } else {
                out.append(c);
            }
        }
        if (isOpen) {
            out.append(')');
        }
        return out.toString();
    }

    public interface EvaluateCallback {
        public void onEvaluate(String expr, double rawResult, String result, int errorResourceId);
    }
}
