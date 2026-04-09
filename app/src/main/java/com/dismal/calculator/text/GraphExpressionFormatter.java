package com.dismal.calculator.text;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.text.style.SubscriptSpan;
import android.text.style.SuperscriptSpan;
import com.dismal.calculator.CalculatorExpressionTokenizer;
import com.dismal.calculator.R;

public final class GraphExpressionFormatter {
    private GraphExpressionFormatter() {}

    public static CharSequence format(Context context, String rawExpression) {
        if (rawExpression == null || rawExpression.isEmpty()) return "";

        CalculatorExpressionTokenizer tokenizer = new CalculatorExpressionTokenizer(context);
        String normalized = tokenizer.getNormalizedExpression(rawExpression);
        String localized = tokenizer.getLocalizedExpression(normalized);

        // Common aliases that the graph parser accepts, but we want to render as math glyphs.
        localized = replaceSqrt(localized);

        SpannableStringBuilder sb = new SpannableStringBuilder(localized);
        applyPowerSpans(sb);
        applySubscriptSpans(sb);
        applyFractionSpans(sb, context);
        return sb;
    }

    private static String replaceSqrt(String s) {
        // Replace "sqrt(" with "√(" (and "sqrt" before a token) for a nicer display.
        // Keep this intentionally conservative to avoid unexpected replacements.
        return s.replace("sqrt(", "√(");
    }

    private static void applyPowerSpans(SpannableStringBuilder sb) {
        for (int i = sb.length() - 1; i >= 0; i--) {
            if (sb.charAt(i) != '^') continue;

            int expStart = i + 1;
            if (expStart >= sb.length()) continue;

            int expEnd = findScriptTokenEnd(sb, expStart);
            if (expEnd <= expStart) continue;

            sb.setSpan(new RelativeSizeSpan(0.7f), expStart, expEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new SuperscriptSpan(), expStart, expEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.delete(i, i + 1);
        }
    }

    private static void applySubscriptSpans(SpannableStringBuilder sb) {
        for (int i = sb.length() - 1; i >= 0; i--) {
            if (sb.charAt(i) != '_') continue;

            int subStart = i + 1;
            if (subStart >= sb.length()) continue;

            int subEnd = findScriptTokenEnd(sb, subStart);
            if (subEnd <= subStart) continue;

            sb.setSpan(new RelativeSizeSpan(0.7f), subStart, subEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new SubscriptSpan(), subStart, subEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.delete(i, i + 1);
        }
    }

    private static int findScriptTokenEnd(CharSequence s, int start) {
        int i = start;
        if (i >= s.length()) return start;

        // Optional leading sign.
        if (s.charAt(i) == '-') i++;
        if (i >= s.length()) return i;

        char c = s.charAt(i);
        if (c == '(') {
            int close = findMatchingParen(s, i);
            return close == -1 ? start : close + 1;
        }

        while (i < s.length() && isScriptChar(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isScriptChar(char c) {
        return Character.isLetterOrDigit(c) || c == '.' || c == 'π';
    }

    private static int findMatchingParen(CharSequence s, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static void applyFractionSpans(SpannableStringBuilder sb, Context context) {
        // Use a dedicated fraction slash for digit/digit patterns to render as a "stacked" fraction.
        String opDiv = context.getString(R.string.op_div);   // ÷
        String opFrac = context.getString(R.string.op_frac); // ⁄

        // Replace digit ÷ digit with digit ⁄ digit.
        for (int i = 0; i < sb.length(); i++) {
            if (!regionMatches(sb, i, opDiv)) continue;
            int opLen = opDiv.length();
            int left = i - 1;
            int right = i + opLen;
            if (left >= 0 && right < sb.length() && Character.isDigit(sb.charAt(left)) && Character.isDigit(sb.charAt(right))) {
                sb.replace(i, i + opLen, opFrac);
            }
        }

        // Apply spans around the fraction slash (⁄). Only digits are styled.
        int index = sb.toString().indexOf(opFrac);
        while (index != -1) {
            int numStart = index;
            while (numStart > 0 && Character.isDigit(sb.charAt(numStart - 1))) {
                numStart--;
            }
            if (numStart < index) {
                sb.setSpan(new RelativeSizeSpan(0.7f), numStart, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new SuperscriptSpan(), numStart, index, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            int denEnd = index + opFrac.length();
            while (denEnd < sb.length() && Character.isDigit(sb.charAt(denEnd))) {
                denEnd++;
            }
            if (denEnd > index + opFrac.length()) {
                sb.setSpan(new RelativeSizeSpan(0.7f), index + opFrac.length(), denEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                sb.setSpan(new SubscriptSpan(), index + opFrac.length(), denEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }

            index = sb.toString().indexOf(opFrac, index + 1);
        }
    }

    private static boolean regionMatches(CharSequence text, int index, String needle) {
        if (index < 0) return false;
        if (index + needle.length() > text.length()) return false;
        for (int i = 0; i < needle.length(); i++) {
            if (text.charAt(index + i) != needle.charAt(i)) return false;
        }
        return true;
    }
}

