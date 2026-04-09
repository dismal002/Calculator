/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.dismal.calculator;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

public class CalculatorExpressionTokenizer {

    private final Map<String, String> mNormalizedToLocalized;
    private final Map<String, String> mLocalizedToNormalized;

    public CalculatorExpressionTokenizer(Context context) {
        mNormalizedToLocalized = new HashMap<>();
        mLocalizedToNormalized = new HashMap<>();

        addReplacement(".", context.getString(R.string.dec_point));

        addReplacement("0", context.getString(R.string.digit_0));
        addReplacement("1", context.getString(R.string.digit_1));
        addReplacement("2", context.getString(R.string.digit_2));
        addReplacement("3", context.getString(R.string.digit_3));
        addReplacement("4", context.getString(R.string.digit_4));
        addReplacement("5", context.getString(R.string.digit_5));
        addReplacement("6", context.getString(R.string.digit_6));
        addReplacement("7", context.getString(R.string.digit_7));
        addReplacement("8", context.getString(R.string.digit_8));
        addReplacement("9", context.getString(R.string.digit_9));

        // Multiple UI symbols can represent the same normalized token (e.g. ÷ and ⁄ both mean "/").
        // Keep a single preferred localized form for display, but accept all known UI forms when
        // normalizing.
        addReplacement("/", context.getString(R.string.op_div));
        addNormalizedOnly(context.getString(R.string.op_frac), "/");
        addReplacement("*", context.getString(R.string.op_mul));
        addReplacement("-", context.getString(R.string.op_sub));
        addReplacement("%", context.getString(R.string.op_pct));

        addReplacement("cos", context.getString(R.string.fun_cos));
        addReplacement("ln", context.getString(R.string.fun_ln));
        addReplacement("log", context.getString(R.string.fun_log));
        addReplacement("sin", context.getString(R.string.fun_sin));
        addReplacement("tan", context.getString(R.string.fun_tan));

        addReplacement("Infinity", context.getString(R.string.inf));
    }

    public String getNormalizedExpression(String expr) {
        for (Entry<String, String> replacementEntry : mLocalizedToNormalized.entrySet()) {
            expr = expr.replace(replacementEntry.getKey(), replacementEntry.getValue());
        }
        return expr;
    }

    public String getLocalizedExpression(String expr) {
        for (Entry<String, String> replacementEntry : mNormalizedToLocalized.entrySet()) {
            expr = expr.replace(replacementEntry.getKey(), replacementEntry.getValue());
        }
        return expr;
    }

    private void addReplacement(String normalized, String localized) {
        mNormalizedToLocalized.put(normalized, localized);
        mLocalizedToNormalized.put(localized, normalized);
    }

    private void addNormalizedOnly(String localized, String normalized) {
        mLocalizedToNormalized.put(localized, normalized);
    }
}
