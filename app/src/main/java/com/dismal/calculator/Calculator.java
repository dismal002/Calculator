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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Activity;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.viewpager.widget.ViewPager;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnLongClickListener;
import android.view.ViewAnimationUtils;
import android.view.ViewGroupOverlay;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.content.Intent;
import android.widget.PopupWindow;

import com.dismal.calculator.CalculatorEditText.OnTextSizeChangeListener;
import com.dismal.calculator.CalculatorExpressionEvaluator.EvaluateCallback;

public class Calculator extends Activity
        implements OnTextSizeChangeListener, EvaluateCallback, OnLongClickListener {

    private static final String NAME = Calculator.class.getName();

    // instance state keys
    private static final String KEY_CURRENT_STATE = NAME + "_currentState";
    private static final String KEY_CURRENT_EXPRESSION = NAME + "_currentExpression";
    private static final String KEY_DEGREE_MODE = NAME + "_degreeMode";
    private static final String KEY_FRACTION_MODE = NAME + "_fractionMode";
    private static final String KEY_PROGRAMMER_MODE = NAME + "_programmerMode";
    private static final String KEY_PROGRAMMER_BASE = NAME + "_programmerBase";

    /**
     * Constant for an invalid resource id.
     */
    public static final int INVALID_RES_ID = -1;

    private enum CalculatorState {
        INPUT, EVALUATE, RESULT, ERROR
    }

    private enum ProgrammerBase {
        DEC(10, R.string.base_dec),
        HEX(16, R.string.base_hex),
        BIN(2, R.string.base_bin);

        final int radix;
        final int labelResId;

        ProgrammerBase(int radix, int labelResId) {
            this.radix = radix;
            this.labelResId = labelResId;
        }
    }

    private final TextWatcher mFormulaTextWatcher = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence charSequence, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            setState(CalculatorState.INPUT);
            evaluateCurrentExpression();
        }
    };

    private final OnKeyListener mFormulaOnKeyListener = new OnKeyListener() {
        @Override
        public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                        onEquals();
                    }
                    // ignore all other actions
                    return true;
            }
            return false;
        }
    };

    private final Editable.Factory mFormulaEditableFactory = new Editable.Factory() {
        @Override
        public Editable newEditable(CharSequence source) {
            final boolean isEdited = mCurrentState == CalculatorState.INPUT
                    || mCurrentState == CalculatorState.ERROR;
            return new CalculatorExpressionBuilder(source, mTokenizer, isEdited);
        }
    };

    private CalculatorState mCurrentState;
    private CalculatorExpressionTokenizer mTokenizer;
    private CalculatorExpressionEvaluator mEvaluator;
    private ProgrammerExpressionEvaluator mProgrammerEvaluator;

    private View mDisplayView;
    private CalculatorEditText mFormulaEditText;
    private CalculatorEditText mResultEditText;
    private ViewPager mPadViewPager;
    private View mDeleteButton;
    private View mClearButton;
    private View mEqualButton;
    private TextView mModeView;
    private Button mModeButton;
    private Button mFractionModeButton;
    private Button mProgrammerModeButtonAdvanced;
    private Button mProgrammerModeButtonProgrammer;
    private Button mBaseBinButton;
    private Button mBaseHexButton;
    private Button mBaseDecButton;
    private View mAdvancedPad;
    private View mProgrammerPad;

    private boolean mIsDegreeMode;
    private boolean mShowFraction;
    private boolean mIsProgrammerMode;
    private ProgrammerBase mProgrammerBase;
    private Animator mCurrentAnimator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_calculator);

        mDisplayView = findViewById(R.id.display);
        mFormulaEditText = (CalculatorEditText) findViewById(R.id.formula);
        mResultEditText = (CalculatorEditText) findViewById(R.id.result);
        mPadViewPager = (ViewPager) findViewById(R.id.pad_pager);
        mDeleteButton = findViewById(R.id.del);
        mClearButton = findViewById(R.id.clr);
        mModeView = (TextView) findViewById(R.id.mode);
        updateModeIndicator();

        final View menuButton = findViewById(R.id.menu);
        if (menuButton != null) {
            menuButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showMenu(view);
                }
            });
        }

        final View xButton = findViewById(R.id.key_x);
        if (xButton != null) {
            xButton.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    if (mIsProgrammerMode) {
                        return true;
                    }
                    showVariablePicker(view);
                    return true;
                }
            });
        }

        mEqualButton = findViewById(R.id.pad_numeric).findViewById(R.id.eq);
        if (mEqualButton == null || mEqualButton.getVisibility() != View.VISIBLE) {
            mEqualButton = findViewById(R.id.pad_operator).findViewById(R.id.eq);
        }

        mTokenizer = new CalculatorExpressionTokenizer(this);
        mEvaluator = new CalculatorExpressionEvaluator(mTokenizer);
        mProgrammerEvaluator = new ProgrammerExpressionEvaluator(mTokenizer);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mIsDegreeMode = savedInstanceState != null
                ? savedInstanceState.getBoolean(KEY_DEGREE_MODE, false)
                : prefs.getBoolean(KEY_DEGREE_MODE, false);
        mShowFraction = savedInstanceState != null
                ? savedInstanceState.getBoolean(KEY_FRACTION_MODE, false)
                : prefs.getBoolean(KEY_FRACTION_MODE, false);
        mIsProgrammerMode = savedInstanceState != null
                ? savedInstanceState.getBoolean(KEY_PROGRAMMER_MODE, false)
                : prefs.getBoolean(KEY_PROGRAMMER_MODE, false);
        final int baseOrdinal = savedInstanceState != null
                ? savedInstanceState.getInt(KEY_PROGRAMMER_BASE, ProgrammerBase.DEC.ordinal())
                : prefs.getInt(KEY_PROGRAMMER_BASE, ProgrammerBase.DEC.ordinal());
        mProgrammerBase = (baseOrdinal >= 0 && baseOrdinal < ProgrammerBase.values().length)
                ? ProgrammerBase.values()[baseOrdinal]
                : ProgrammerBase.DEC;

        mAdvancedPad = findViewById(R.id.pad_advanced);
        mProgrammerPad = findViewById(R.id.pad_programmer);

        mModeButton = (Button) mAdvancedPad.findViewById(R.id.toggle_mode);
        updateDegreeMode();

        mFractionModeButton = (Button) mAdvancedPad.findViewById(R.id.toggle_fraction);
        updateFractionMode();

        mProgrammerModeButtonAdvanced =
                (Button) mAdvancedPad.findViewById(R.id.toggle_programmer);
        mProgrammerModeButtonProgrammer =
                (Button) mProgrammerPad.findViewById(R.id.toggle_programmer);
        mBaseBinButton = (Button) mProgrammerPad.findViewById(R.id.base_bin);
        mBaseHexButton = (Button) mProgrammerPad.findViewById(R.id.base_hex);
        mBaseDecButton = (Button) mProgrammerPad.findViewById(R.id.base_dec);
        updateProgrammerMode();
        updateProgrammerBase();

        savedInstanceState = savedInstanceState == null ? Bundle.EMPTY : savedInstanceState;
        setState(CalculatorState.values()[
                savedInstanceState.getInt(KEY_CURRENT_STATE, CalculatorState.INPUT.ordinal())]);
        mFormulaEditText.setText(mTokenizer.getLocalizedExpression(
                savedInstanceState.getString(KEY_CURRENT_EXPRESSION, "")));
        evaluateCurrentExpression();

        mFormulaEditText.setEditableFactory(mFormulaEditableFactory);
        mFormulaEditText.addTextChangedListener(mFormulaTextWatcher);
        mFormulaEditText.setOnKeyListener(mFormulaOnKeyListener);
        mFormulaEditText.setOnTextSizeChangeListener(this);
        mDeleteButton.setOnLongClickListener(this);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // If there's an animation in progress, end it immediately to ensure the state is
        // up-to-date before it is serialized.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.end();
        }

        super.onSaveInstanceState(outState);

        outState.putInt(KEY_CURRENT_STATE, mCurrentState.ordinal());
        outState.putString(KEY_CURRENT_EXPRESSION,
                mTokenizer.getNormalizedExpression(mFormulaEditText.getText().toString()));
        outState.putBoolean(KEY_DEGREE_MODE, mIsDegreeMode);
        outState.putBoolean(KEY_FRACTION_MODE, mShowFraction);
        outState.putBoolean(KEY_PROGRAMMER_MODE, mIsProgrammerMode);
        outState.putInt(KEY_PROGRAMMER_BASE, mProgrammerBase.ordinal());
    }

    private void setState(CalculatorState state) {
        if (mCurrentState != state) {
            mCurrentState = state;

            if (state == CalculatorState.RESULT || state == CalculatorState.ERROR) {
                mDeleteButton.setVisibility(View.GONE);
                mClearButton.setVisibility(View.VISIBLE);
            } else {
                mDeleteButton.setVisibility(View.VISIBLE);
                mClearButton.setVisibility(View.GONE);
            }

            if (state == CalculatorState.ERROR) {
                final int errorColor = getResources().getColor(R.color.calculator_error_color);
                mFormulaEditText.setTextColor(errorColor);
                mResultEditText.setTextColor(errorColor);
                getWindow().setStatusBarColor(errorColor);
            } else {
                mFormulaEditText.setTextColor(
                        getResources().getColor(R.color.display_formula_text_color));
                mResultEditText.setTextColor(
                        getResources().getColor(R.color.display_result_text_color));
                getWindow().setStatusBarColor(
                        getResources().getColor(R.color.calculator_accent_color));
            }
        }
    }

    @Override
    public void onBackPressed() {
        if (mPadViewPager == null || mPadViewPager.getCurrentItem() == 0) {
            // If the user is currently looking at the first pad (or the pad is not paged),
            // allow the system to handle the Back button.
            super.onBackPressed();
        } else {
            // Otherwise, select the previous pad.
            mPadViewPager.setCurrentItem(mPadViewPager.getCurrentItem() - 1);
        }
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();

        // If there's an animation in progress, end it immediately to ensure the state is
        // up-to-date before the pending user interaction is handled.
        if (mCurrentAnimator != null) {
            mCurrentAnimator.end();
        }
    }

    public void onButtonClick(View view) {
        final int id = view.getId();
        if (id == R.id.eq) {
            onEquals();
        } else if (id == R.id.del) {
            onDelete();
        } else if (id == R.id.clr) {
            onClear();
        } else if (id == R.id.fun_cos || id == R.id.fun_ln || id == R.id.fun_log
                || id == R.id.fun_sin || id == R.id.fun_tan) {
            // Add left parenthesis after functions.
            mFormulaEditText.append(((Button) view).getText() + "(");
        } else if (id == R.id.fun_abs) {
            mFormulaEditText.append("|");
        } else if (id == R.id.toggle_mode) {
            toggleDegreeMode();
        } else if (id == R.id.toggle_fraction) {
            toggleFractionMode();
        } else if (id == R.id.toggle_programmer) {
            toggleProgrammerMode();
        } else if (id == R.id.base_bin) {
            setProgrammerBase(ProgrammerBase.BIN);
        } else if (id == R.id.base_hex) {
            setProgrammerBase(ProgrammerBase.HEX);
        } else if (id == R.id.base_dec) {
            setProgrammerBase(ProgrammerBase.DEC);
        } else if (id == R.id.open_graph) {
            openGraph();
        } else {
            mFormulaEditText.append(((Button) view).getText());
        }
    }

    private void toggleDegreeMode() {
        mIsDegreeMode = !mIsDegreeMode;
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(KEY_DEGREE_MODE, mIsDegreeMode)
                .apply();
        updateDegreeMode();
        evaluateCurrentExpression();
    }

    private void updateDegreeMode() {
        if (mIsDegreeMode) {
            mModeButton.setText(R.string.mode_deg);
            mModeButton.setContentDescription(getString(R.string.desc_switch_rad));
        } else {
            mModeButton.setText(R.string.mode_rad);
            mModeButton.setContentDescription(getString(R.string.desc_switch_deg));
        }
        updateModeIndicator();
    }

    private void toggleFractionMode() {
        mShowFraction = !mShowFraction;
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(KEY_FRACTION_MODE, mShowFraction)
                .apply();
        updateFractionMode();
        evaluateCurrentExpression();
    }

    private void updateFractionMode() {
        if (mShowFraction) {
            mFractionModeButton.setText(R.string.mode_frac);
            mFractionModeButton.setContentDescription(getString(R.string.desc_switch_dec));
        } else {
            mFractionModeButton.setText(R.string.mode_dec);
            mFractionModeButton.setContentDescription(getString(R.string.desc_switch_frac));
        }
    }

    private void toggleProgrammerMode() {
        mIsProgrammerMode = !mIsProgrammerMode;
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putBoolean(KEY_PROGRAMMER_MODE, mIsProgrammerMode)
                .apply();
        updateProgrammerMode();
        evaluateCurrentExpression();
    }

    private void setProgrammerBase(ProgrammerBase base) {
        if (mProgrammerBase == base) {
            return;
        }
        mProgrammerBase = base;
        PreferenceManager.getDefaultSharedPreferences(this)
                .edit()
                .putInt(KEY_PROGRAMMER_BASE, mProgrammerBase.ordinal())
                .apply();
        updateProgrammerBase();
        evaluateCurrentExpression();
    }

    private void updateProgrammerMode() {
        if (mProgrammerModeButtonAdvanced != null) {
            if (mIsProgrammerMode) {
                mProgrammerModeButtonAdvanced.setText(R.string.mode_prog);
                mProgrammerModeButtonAdvanced.setContentDescription(
                        getString(R.string.desc_switch_std));
            } else {
                mProgrammerModeButtonAdvanced.setText(R.string.mode_std);
                mProgrammerModeButtonAdvanced.setContentDescription(
                        getString(R.string.desc_switch_prog));
            }
        }
        if (mProgrammerModeButtonProgrammer != null) {
            if (mIsProgrammerMode) {
                mProgrammerModeButtonProgrammer.setText(R.string.mode_prog);
                mProgrammerModeButtonProgrammer.setContentDescription(
                        getString(R.string.desc_switch_std));
            } else {
                mProgrammerModeButtonProgrammer.setText(R.string.mode_std);
                mProgrammerModeButtonProgrammer.setContentDescription(
                        getString(R.string.desc_switch_prog));
            }
        }

        if (mAdvancedPad != null && mProgrammerPad != null) {
            mAdvancedPad.setVisibility(mIsProgrammerMode ? View.GONE : View.VISIBLE);
            mProgrammerPad.setVisibility(mIsProgrammerMode ? View.VISIBLE : View.GONE);
        }

        updateModeIndicator();
        updateProgrammerInputButtons();
    }

    private void updateProgrammerBase() {
        if (mBaseBinButton != null) mBaseBinButton.setEnabled(mProgrammerBase != ProgrammerBase.BIN);
        if (mBaseHexButton != null) mBaseHexButton.setEnabled(mProgrammerBase != ProgrammerBase.HEX);
        if (mBaseDecButton != null) mBaseDecButton.setEnabled(mProgrammerBase != ProgrammerBase.DEC);
        updateModeIndicator();
        updateProgrammerInputButtons();
    }

    private void updateProgrammerInputButtons() {
        final boolean isProg = mIsProgrammerMode;
        final boolean isBin = isProg && mProgrammerBase == ProgrammerBase.BIN;
        final boolean isHex = isProg && mProgrammerBase == ProgrammerBase.HEX;

        setButtonEnabled(R.id.op_pct, !isProg);
        setButtonEnabled(R.id.dec_point, !isProg);
        setButtonEnabled(R.id.open_graph, !isProg);

        setButtonEnabled(R.id.digit_2, !isBin);
        setButtonEnabled(R.id.digit_3, !isBin);
        setButtonEnabled(R.id.digit_4, !isBin);
        setButtonEnabled(R.id.digit_5, !isBin);
        setButtonEnabled(R.id.digit_6, !isBin);
        setButtonEnabled(R.id.digit_7, !isBin);
        setButtonEnabled(R.id.digit_8, !isBin);
        setButtonEnabled(R.id.digit_9, !isBin);

        setButtonEnabled(R.id.digit_a, isHex);
        setButtonEnabled(R.id.digit_b, isHex);
        setButtonEnabled(R.id.digit_c, isHex);
        setButtonEnabled(R.id.digit_d, isHex);
        setButtonEnabled(R.id.digit_e, isHex);
        setButtonEnabled(R.id.digit_f, isHex);
    }

    private void setButtonEnabled(int id, boolean enabled) {
        final View v = findViewById(id);
        if (v != null) {
            v.setEnabled(enabled);
        }
    }

    private void updateModeIndicator() {
        if (mModeView != null) {
            if (mIsProgrammerMode) {
                mModeView.setText(mProgrammerBase.labelResId);
            } else {
                mModeView.setText(mIsDegreeMode ? R.string.mode_deg : R.string.mode_rad);
            }
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (view.getId() == R.id.del) {
            onClear();
            return true;
        }
        return false;
    }

    private String formatSymbolicResult(double rawResult) {
        final Radical radical = Radical.fromDouble(rawResult);
        if (radical != null && (radical.radicand > 1L || Math.abs(radical.coefficient) > 1L)) {
            return radical.toNiceString();
        }

        final Fraction fraction = Fraction.fromDouble(rawResult, 1000000);
        if (fraction != null) {
            return fraction.toNiceString();
        }

        return null;
    }

    @Override
    public void onEvaluate(String expr, double rawResult, String result, int errorResourceId) {
        if (mCurrentState == CalculatorState.INPUT) {
            String displayResult = result;
            if (!mIsProgrammerMode && mShowFraction
                    && errorResourceId == INVALID_RES_ID
                    && !TextUtils.isEmpty(result)) {
                final String symbolic = formatSymbolicResult(rawResult);
                if (symbolic != null) {
                    displayResult = symbolic;
                }
            }
            mResultEditText.setText(displayResult);
        } else if (errorResourceId != INVALID_RES_ID) {
            onError(errorResourceId);
        } else if (!TextUtils.isEmpty(result)) {
            String displayResult = result;
            if (!mIsProgrammerMode && mShowFraction) {
                final String symbolic = formatSymbolicResult(rawResult);
                if (symbolic != null) {
                    displayResult = symbolic;
                }
            }
            onResult(displayResult);
        } else if (mCurrentState == CalculatorState.EVALUATE) {
            // The current expression cannot be evaluated -> return to the input state.
            setState(CalculatorState.INPUT);
        }

        mFormulaEditText.requestFocus();
    }

    private void showMenu(View anchor) {
        final PopupMenu popupMenu = new PopupMenu(this, anchor);
        popupMenu.getMenuInflater().inflate(R.menu.activity_calculator, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (item.getItemId() == R.id.menu_licenses) {
                    startActivity(new Intent(Calculator.this, Licenses.class));
                    return true;
                }
                return false;
            }
        });
        popupMenu.show();
    }

    private void showVariablePicker(View anchor) {
        final View content = getLayoutInflater().inflate(R.layout.variable_picker_popup, null);
        final LinearLayout container = (LinearLayout) content.findViewById(R.id.variable_container);

        final String letters = "abcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < letters.length(); i++) {
            final String letter = String.valueOf(letters.charAt(i));
            final Button b = new Button(this);
            b.setText(letter);
            b.setAllCaps(false);
            b.setTextColor(getResources().getColor(R.color.pad_button_text_color));
            b.setBackgroundResource(R.drawable.pad_button_background);
            b.setMinWidth(0);
            b.setMinHeight(0);
            b.setPadding(16, 12, 16, 12);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mFormulaEditText.append(letter);
                    if (v.getTag() instanceof PopupWindow) {
                        ((PopupWindow) v.getTag()).dismiss();
                    }
                }
            });
            container.addView(b);
        }

        final PopupWindow popup = new PopupWindow(
                content, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(0x00000000));
        popup.setOutsideTouchable(true);

        for (int i = 0; i < container.getChildCount(); i++) {
            container.getChildAt(i).setTag(popup);
        }

        content.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        final int popupWidth = content.getMeasuredWidth();
        final int popupHeight = content.getMeasuredHeight();

        final int[] loc = new int[2];
        anchor.getLocationInWindow(loc);

        final DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        final int screenW = dm.widthPixels;
        final int screenH = dm.heightPixels;

        int x = loc[0] + anchor.getWidth();
        int y = loc[1] + (anchor.getHeight() / 2) - (popupHeight / 2);
        if (x + popupWidth > screenW) {
            x = Math.max(0, screenW - popupWidth);
        }
        if (y + popupHeight > screenH) {
            y = Math.max(0, screenH - popupHeight);
        }
        if (y < 0) {
            y = 0;
        }

        popup.showAtLocation(anchor, 0, x, y);
    }

    @Override
    public void onTextSizeChanged(final TextView textView, float oldSize) {
        if (mCurrentState != CalculatorState.INPUT) {
            // Only animate text changes that occur from user input.
            return;
        }

        // Calculate the values needed to perform the scale and translation animations,
        // maintaining the same apparent baseline for the displayed text.
        final float textScale = oldSize / textView.getTextSize();
        final float translationX = (1.0f - textScale) *
                (textView.getWidth() / 2.0f - textView.getPaddingEnd());
        final float translationY = (1.0f - textScale) *
                (textView.getHeight() / 2.0f - textView.getPaddingBottom());

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(textView, View.SCALE_X, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.SCALE_Y, textScale, 1.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_X, translationX, 0.0f),
                ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, translationY, 0.0f));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_mediumAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.start();
    }

    private void onEquals() {
        if (mCurrentState == CalculatorState.INPUT) {
            setState(CalculatorState.EVALUATE);
            evaluateCurrentExpression();
        }
    }

    private void openGraph() {
        final String exprNormalized = mTokenizer.getNormalizedExpression(mFormulaEditText.getText().toString());
        if (TextUtils.isEmpty(exprNormalized)) {
            Toast.makeText(this, R.string.graph_error, Toast.LENGTH_SHORT).show();
            return;
        }
        final Intent intent = new Intent(this, GraphActivity.class);
        intent.putExtra(GraphActivity.EXTRA_EXPR_NORMALIZED, exprNormalized);
        intent.putExtra(GraphActivity.EXTRA_DEGREE_MODE, mIsDegreeMode);
        startActivity(intent);
    }

    private void evaluateCurrentExpression() {
        if (mIsProgrammerMode) {
            mProgrammerEvaluator.evaluate(mFormulaEditText.getText(), mProgrammerBase.radix, this);
        } else {
            mEvaluator.evaluate(mFormulaEditText.getText(), mIsDegreeMode, this);
        }
    }

    private void onDelete() {
        // Delete works like backspace; remove the last character from the expression.
        final Editable formulaText = mFormulaEditText.getEditableText();
        final int formulaLength = formulaText.length();
        if (formulaLength > 0) {
            formulaText.delete(formulaLength - 1, formulaLength);
        }
    }

    private void reveal(View sourceView, int colorRes, AnimatorListener listener) {
        final ViewGroupOverlay groupOverlay =
                (ViewGroupOverlay) getWindow().getDecorView().getOverlay();

        final Rect displayRect = new Rect();
        mDisplayView.getGlobalVisibleRect(displayRect);

        // Make reveal cover the display and status bar.
        final View revealView = new View(this);
        revealView.setBottom(displayRect.bottom);
        revealView.setLeft(displayRect.left);
        revealView.setRight(displayRect.right);
        revealView.setBackgroundColor(getResources().getColor(colorRes));
        groupOverlay.add(revealView);

        final int[] clearLocation = new int[2];
        sourceView.getLocationInWindow(clearLocation);
        clearLocation[0] += sourceView.getWidth() / 2;
        clearLocation[1] += sourceView.getHeight() / 2;

        final int revealCenterX = clearLocation[0] - revealView.getLeft();
        final int revealCenterY = clearLocation[1] - revealView.getTop();

        final double x1_2 = Math.pow(revealView.getLeft() - revealCenterX, 2);
        final double x2_2 = Math.pow(revealView.getRight() - revealCenterX, 2);
        final double y_2 = Math.pow(revealView.getTop() - revealCenterY, 2);
        final float revealRadius = (float) Math.max(Math.sqrt(x1_2 + y_2), Math.sqrt(x2_2 + y_2));

        final Animator revealAnimator =
                ViewAnimationUtils.createCircularReveal(revealView,
                        revealCenterX, revealCenterY, 0.0f, revealRadius);
        revealAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_longAnimTime));

        final Animator alphaAnimator = ObjectAnimator.ofFloat(revealView, View.ALPHA, 0.0f);
        alphaAnimator.setDuration(
                getResources().getInteger(android.R.integer.config_mediumAnimTime));
        alphaAnimator.addListener(listener);

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(revealAnimator).before(alphaAnimator);
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                groupOverlay.remove(revealView);
                mCurrentAnimator = null;
            }
        });

        mCurrentAnimator = animatorSet;
        animatorSet.start();
    }

    private void onClear() {
        if (TextUtils.isEmpty(mFormulaEditText.getText())) {
            return;
        }

        final View sourceView = mClearButton.getVisibility() == View.VISIBLE
                ? mClearButton : mDeleteButton;
        reveal(sourceView, R.color.calculator_accent_color, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mFormulaEditText.getEditableText().clear();
            }
        });
    }

    private void onError(final int errorResourceId) {
        if (mCurrentState != CalculatorState.EVALUATE) {
            // Only animate error on evaluate.
            mResultEditText.setText(errorResourceId);
            return;
        }

        reveal(mEqualButton, R.color.calculator_error_color, new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                setState(CalculatorState.ERROR);
                mResultEditText.setText(errorResourceId);
            }
        });
    }

    private void onResult(final String result) {
        // Calculate the values needed to perform the scale and translation animations,
        // accounting for how the scale will affect the final position of the text.
        final float resultScale =
                mFormulaEditText.getVariableTextSize(result) / mResultEditText.getTextSize();
        final float resultTranslationX = (1.0f - resultScale) *
                (mResultEditText.getWidth() / 2.0f - mResultEditText.getPaddingEnd());
        final float resultTranslationY = (1.0f - resultScale) *
                (mResultEditText.getHeight() / 2.0f - mResultEditText.getPaddingBottom()) +
                (mFormulaEditText.getBottom() - mResultEditText.getBottom()) +
                (mResultEditText.getPaddingBottom() - mFormulaEditText.getPaddingBottom());
        final float formulaTranslationY = -mFormulaEditText.getBottom();

        // Use a value animator to fade to the final text color over the course of the animation.
        final int resultTextColor = mResultEditText.getCurrentTextColor();
        final int formulaTextColor = mFormulaEditText.getCurrentTextColor();
        final ValueAnimator textColorAnimator =
                ValueAnimator.ofObject(new ArgbEvaluator(), resultTextColor, formulaTextColor);
        textColorAnimator.addUpdateListener(new AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                mResultEditText.setTextColor((int) valueAnimator.getAnimatedValue());
            }
        });

        final AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(
                textColorAnimator,
                ObjectAnimator.ofFloat(mResultEditText, View.SCALE_X, resultScale),
                ObjectAnimator.ofFloat(mResultEditText, View.SCALE_Y, resultScale),
                ObjectAnimator.ofFloat(mResultEditText, View.TRANSLATION_X, resultTranslationX),
                ObjectAnimator.ofFloat(mResultEditText, View.TRANSLATION_Y, resultTranslationY),
                ObjectAnimator.ofFloat(mFormulaEditText, View.TRANSLATION_Y, formulaTranslationY));
        animatorSet.setDuration(getResources().getInteger(android.R.integer.config_longAnimTime));
        animatorSet.setInterpolator(new AccelerateDecelerateInterpolator());
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mResultEditText.setText(result);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                // Reset all of the values modified during the animation.
                mResultEditText.setTextColor(resultTextColor);
                mResultEditText.setScaleX(1.0f);
                mResultEditText.setScaleY(1.0f);
                mResultEditText.setTranslationX(0.0f);
                mResultEditText.setTranslationY(0.0f);
                mFormulaEditText.setTranslationY(0.0f);

                // Finally update the formula to use the current result.
                mFormulaEditText.setText(result);
                setState(CalculatorState.RESULT);

                mCurrentAnimator = null;
            }
        });

        mCurrentAnimator = animatorSet;
        animatorSet.start();
    }
}
