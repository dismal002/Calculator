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

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;

public class CalculatorExpressionBuilder extends SpannableStringBuilder {

    private final CalculatorExpressionTokenizer mTokenizer;
    private boolean mIsEdited;

    public CalculatorExpressionBuilder(
            CharSequence text, CalculatorExpressionTokenizer tokenizer, boolean isEdited) {
        super(text);

        mTokenizer = tokenizer;
        mIsEdited = isEdited;
    }

    @Override
    public SpannableStringBuilder replace(int start, int end, CharSequence tb, int tbstart,
            int tbend) {
        if (start != length() || end != length()) {
            mIsEdited = true;
            return super.replace(start, end, tb, tbstart, tbend);
        }

        String appendExpr =
                mTokenizer.getNormalizedExpression(tb.subSequence(tbstart, tbend).toString());
        if (appendExpr.length() == 1) {
            final String expr = mTokenizer.getNormalizedExpression(toString());
            switch (appendExpr.charAt(0)) {
                case '.':
                    // don't allow two decimals in the same number
                    final int index = expr.lastIndexOf('.');
                    if (index != -1 && TextUtils.isDigitsOnly(expr.substring(index + 1, start))) {
                        appendExpr = "";
                    }
                    break;
                case '+':
                case '*':
                case '/':
                    // don't allow leading operator
                    if (start == 0) {
                        appendExpr = "";
                        break;
                    }

                    // don't allow multiple successive operators
                    while (start > 0 && "+-*/".indexOf(expr.charAt(start - 1)) != -1) {
                        --start;
                    }
                    // fall through
                case '-':
                    // don't allow -- or +-
                    if (start > 0 && "+-".indexOf(expr.charAt(start - 1)) != -1) {
                        --start;
                    }

                    // mark as edited since operators can always be appended
                    mIsEdited = true;
                    break;
                case '%':
                    // Percent is postfix; require a preceding operand.
                    if (start == 0) {
                        appendExpr = "";
                        break;
                    }
                    // Don't allow % directly after another operator or another %.
                    if (start > 0 && ("+-*/%".indexOf(expr.charAt(start - 1)) != -1)) {
                        appendExpr = "";
                        break;
                    }
                    mIsEdited = true;
                    break;
                default:
                    break;
            }
        }

        // since this is the first edit replace the entire string
        if (!mIsEdited && appendExpr.length() > 0) {
            start = 0;
            mIsEdited = true;
        }

        appendExpr = mTokenizer.getLocalizedExpression(appendExpr);
        super.replace(start, end, appendExpr, 0, appendExpr.length());
        if (mIsEdited) {
            applyFractionSpans();
        }
        return this;
    }

    private void applyFractionSpans() {
        // Remove existing spans
        final RelativeSizeSpan[] sizeSpans = getSpans(0, length(), RelativeSizeSpan.class);
        for (RelativeSizeSpan span : sizeSpans) {
            removeSpan(span);
        }
        final SuperscriptSpan[] superSpans = getSpans(0, length(), SuperscriptSpan.class);
        for (SuperscriptSpan span : superSpans) {
            removeSpan(span);
        }
        final SubscriptSpan[] subSpans = getSpans(0, length(), SubscriptSpan.class);
        for (SubscriptSpan span : subSpans) {
            removeSpan(span);
        }

        // Apply new spans
        final String text = toString();
        final String opFrac = mTokenizer.getLocalizedExpression("/");
        int index = text.indexOf(opFrac);
        while (index != -1) {
            // Find numerator (digits before)
            int numStart = index;
            while (numStart > 0 && Character.isDigit(text.charAt(numStart - 1))) {
                numStart--;
            }
            if (numStart < index) {
                setSpan(new RelativeSizeSpan(0.7f), numStart, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                setSpan(new SuperscriptSpan(), numStart, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            // Find denominator (digits after)
            int denEnd = index + 1;
            while (denEnd < text.length() && Character.isDigit(text.charAt(denEnd))) {
                denEnd++;
            }
            if (denEnd > index + 1) {
                setSpan(new RelativeSizeSpan(0.7f), index + 1, denEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                setSpan(new SubscriptSpan(), index + 1, denEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            index = text.indexOf(opFrac, index + 1);
        }
    }
}
