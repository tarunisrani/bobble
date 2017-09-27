/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.inputmethod.keyboard;

import android.animation.AnimatorInflater;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Typeface;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android.inputmethod.keyboard.internal.DrawingHandler;
import com.android.inputmethod.keyboard.internal.DrawingPreviewPlacerView;
import com.android.inputmethod.keyboard.internal.GestureFloatingTextDrawingPreview;
import com.android.inputmethod.keyboard.internal.GestureTrailsDrawingPreview;
import com.android.inputmethod.keyboard.internal.KeyDrawParams;
import com.android.inputmethod.keyboard.internal.KeyPreviewChoreographer;
import com.android.inputmethod.keyboard.internal.KeyPreviewDrawParams;
import com.android.inputmethod.keyboard.internal.KeyPreviewView;
import com.android.inputmethod.keyboard.internal.KeyboardIconsSet;
import com.android.inputmethod.keyboard.internal.LanguageOnSpacebarHelper;
import com.android.inputmethod.keyboard.internal.MoreKeySpec;
import com.android.inputmethod.keyboard.internal.NonDistinctMultitouchHelper;
import com.android.inputmethod.keyboard.internal.SlidingKeyInputDrawingPreview;
import com.android.inputmethod.keyboard.internal.TimerHandler;
import com.android.inputmethod.latin.utils.CoordinateUtils;
import com.android.inputmethod.latin.utils.SpacebarLanguageUtils;
import com.android.inputmethod.latin.utils.TypefaceUtils;
import com.android.inputmethod.latin.utils.ViewLayoutUtils;

import org.smc.inputmethod.accessibility.AccessibilityUtils;
import org.smc.inputmethod.accessibility.MainKeyboardAccessibilityDelegate;
import org.smc.inputmethod.annotations.ExternallyReferenced;
import org.smc.inputmethod.indic.Constants;
import org.smc.inputmethod.indic.R;
import org.smc.inputmethod.indic.SuggestedWords;
import org.smc.inputmethod.indic.settings.DebugSettings;

import java.util.HashMap;
import java.util.WeakHashMap;

/**
 * A view that is responsible for detecting key presses and touch movements.
 *
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextRatio
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextColor
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextShadowRadius
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarTextShadowColor
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarFinalAlpha
 * @attr ref R.styleable#MainKeyboardView_languageOnSpacebarFadeoutAnimator
 * @attr ref R.styleable#MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator
 * @attr ref R.styleable#MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator
 * @attr ref R.styleable#MainKeyboardView_keyHysteresisDistance
 * @attr ref R.styleable#MainKeyboardView_touchNoiseThresholdTime
 * @attr ref R.styleable#MainKeyboardView_touchNoiseThresholdDistance
 * @attr ref R.styleable#MainKeyboardView_keySelectionByDraggingFinger
 * @attr ref R.styleable#MainKeyboardView_keyRepeatStartTimeout
 * @attr ref R.styleable#MainKeyboardView_keyRepeatInterval
 * @attr ref R.styleable#MainKeyboardView_longPressKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_longPressShiftKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_ignoreAltCodeKeyTimeout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewLayout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewOffset
 * @attr ref R.styleable#MainKeyboardView_keyPreviewHeight
 * @attr ref R.styleable#MainKeyboardView_keyPreviewLingerTimeout
 * @attr ref R.styleable#MainKeyboardView_keyPreviewShowUpAnimator
 * @attr ref R.styleable#MainKeyboardView_keyPreviewDismissAnimator
 * @attr ref R.styleable#MainKeyboardView_moreKeysKeyboardLayout
 * @attr ref R.styleable#MainKeyboardView_moreKeysKeyboardForActionLayout
 * @attr ref R.styleable#MainKeyboardView_backgroundDimAlpha
 * @attr ref R.styleable#MainKeyboardView_showMoreKeysKeyboardAtTouchPoint
 * @attr ref R.styleable#MainKeyboardView_gestureFloatingPreviewTextLingerTimeout
 * @attr ref R.styleable#MainKeyboardView_gestureStaticTimeThresholdAfterFastTyping
 * @attr ref R.styleable#MainKeyboardView_gestureDetectFastMoveSpeedThreshold
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicThresholdDecayDuration
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicTimeThresholdFrom
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicTimeThresholdTo
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicDistanceThresholdFrom
 * @attr ref R.styleable#MainKeyboardView_gestureDynamicDistanceThresholdTo
 * @attr ref R.styleable#MainKeyboardView_gestureSamplingMinimumDistance
 * @attr ref R.styleable#MainKeyboardView_gestureRecognitionMinimumTime
 * @attr ref R.styleable#MainKeyboardView_gestureRecognitionSpeedThreshold
 * @attr ref R.styleable#MainKeyboardView_suppressKeyPreviewAfterBatchInputDuration
 */
public final class MainKeyboardView extends KeyboardView implements PointerTracker.DrawingProxy,
        MoreKeysPanel.Controller, DrawingHandler.Callbacks, TimerHandler.Callbacks {
    private static final String TAG = MainKeyboardView.class.getSimpleName();

    /** Listener for {@link KeyboardActionListener}. */
    private KeyboardActionListener mKeyboardActionListener;

    /* Space key and its icon and background. */
    private Key mSpaceKey;
    // Stuff to draw language name on spacebar.
    private final int mLanguageOnSpacebarFinalAlpha;
    private ObjectAnimator mLanguageOnSpacebarFadeoutAnimator;
    private int mLanguageOnSpacebarFormatType;
    private boolean mHasMultipleEnabledIMEsOrSubtypes;
    private int mLanguageOnSpacebarAnimAlpha = Constants.Color.ALPHA_OPAQUE;
    private final float mLanguageOnSpacebarTextRatio;
    private float mLanguageOnSpacebarTextSize;
    private final int mLanguageOnSpacebarTextColor;
    private final float mLanguageOnSpacebarTextShadowRadius;
    private final int mLanguageOnSpacebarTextShadowColor;
    private static final float LANGUAGE_ON_SPACEBAR_TEXT_SHADOW_RADIUS_DISABLED = -1.0f;
    // The minimum x-scale to fit the language name on spacebar.
    private static final float MINIMUM_XSCALE_OF_LANGUAGE_NAME = 0.8f;

    // Stuff to draw altCodeWhileTyping keys.
    private final ObjectAnimator mAltCodeKeyWhileTypingFadeoutAnimator;
    private final ObjectAnimator mAltCodeKeyWhileTypingFadeinAnimator;
    private int mAltCodeKeyWhileTypingAnimAlpha = Constants.Color.ALPHA_OPAQUE;

    // Drawing preview placer view
    private final DrawingPreviewPlacerView mDrawingPreviewPlacerView;
    private final int[] mOriginCoords = CoordinateUtils.newInstance();
    private final GestureFloatingTextDrawingPreview mGestureFloatingTextDrawingPreview;
    private final GestureTrailsDrawingPreview mGestureTrailsDrawingPreview;
    private final SlidingKeyInputDrawingPreview mSlidingKeyInputDrawingPreview;

    // Key preview
    private final KeyPreviewDrawParams mKeyPreviewDrawParams;
    private final KeyPreviewChoreographer mKeyPreviewChoreographer;

    // More keys keyboard
    private final Paint mBackgroundDimAlphaPaint = new Paint();
    private boolean mNeedsToDimEntireKeyboard;
    private final View mMoreKeysKeyboardContainer;
    private final View mMoreKeysKeyboardForActionContainer;
    private final WeakHashMap<Key, Keyboard> mMoreKeysKeyboardCache = new WeakHashMap<>();
    private final boolean mConfigShowMoreKeysKeyboardAtTouchedPoint;
    // More keys panel (used by both more keys keyboard and more suggestions view)
    // TODO: Consider extending to support multiple more keys panels
    private MoreKeysPanel mMoreKeysPanel;

    // Gesture floating preview text
    // TODO: Make this parameter customizable by user via settings.
    private int mGestureFloatingPreviewTextLingerTimeout;

    private final KeyDetector mKeyDetector;
    private final NonDistinctMultitouchHelper mNonDistinctMultitouchHelper;

    private final TimerHandler mKeyTimerHandler;
    private final int mLanguageOnSpacebarHorizontalMargin;

    private final DrawingHandler mDrawingHandler = new DrawingHandler(this);

    private MainKeyboardAccessibilityDelegate mAccessibilityDelegate;

    public MainKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.mainKeyboardViewStyle);
    }

    public MainKeyboardView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);

        mDrawingPreviewPlacerView = new DrawingPreviewPlacerView(context, attrs);

        final TypedArray mainKeyboardViewAttr = context.obtainStyledAttributes(
                attrs, R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        final int ignoreAltCodeKeyTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_ignoreAltCodeKeyTimeout, 0);
        final int gestureRecognitionUpdateTime = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureRecognitionUpdateTime, 0);
        mKeyTimerHandler = new TimerHandler(
                this, ignoreAltCodeKeyTimeout, gestureRecognitionUpdateTime);

        final float keyHysteresisDistance = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistance, 0.0f);
        final float keyHysteresisDistanceForSlidingModifier = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_keyHysteresisDistanceForSlidingModifier, 0.0f);
        mKeyDetector = new KeyDetector(
                keyHysteresisDistance, keyHysteresisDistanceForSlidingModifier);

        PointerTracker.init(mainKeyboardViewAttr, mKeyTimerHandler, this /* DrawingProxy */);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        final boolean forceNonDistinctMultitouch = prefs.getBoolean(
                DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH, false);
        final boolean hasDistinctMultitouch = context.getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN_MULTITOUCH_DISTINCT)
                && !forceNonDistinctMultitouch;
        mNonDistinctMultitouchHelper = hasDistinctMultitouch ? null
                : new NonDistinctMultitouchHelper();

        final int backgroundDimAlpha = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_backgroundDimAlpha, 0);
        mBackgroundDimAlphaPaint.setColor(Color.BLACK);
        mBackgroundDimAlphaPaint.setAlpha(backgroundDimAlpha);
        mLanguageOnSpacebarTextRatio = mainKeyboardViewAttr.getFraction(
                R.styleable.MainKeyboardView_languageOnSpacebarTextRatio, 1, 1, 1.0f);
        mLanguageOnSpacebarTextColor = mainKeyboardViewAttr.getColor(
                R.styleable.MainKeyboardView_languageOnSpacebarTextColor, 0);
        mLanguageOnSpacebarTextShadowRadius = mainKeyboardViewAttr.getFloat(
                R.styleable.MainKeyboardView_languageOnSpacebarTextShadowRadius,
                LANGUAGE_ON_SPACEBAR_TEXT_SHADOW_RADIUS_DISABLED);
        mLanguageOnSpacebarTextShadowColor = mainKeyboardViewAttr.getColor(
                R.styleable.MainKeyboardView_languageOnSpacebarTextShadowColor, 0);
        mLanguageOnSpacebarFinalAlpha = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_languageOnSpacebarFinalAlpha,
                Constants.Color.ALPHA_OPAQUE);
        final int languageOnSpacebarFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_languageOnSpacebarFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeoutAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeoutAnimator, 0);
        final int altCodeKeyWhileTypingFadeinAnimatorResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_altCodeKeyWhileTypingFadeinAnimator, 0);

        mKeyPreviewDrawParams = new KeyPreviewDrawParams(mainKeyboardViewAttr);
        mKeyPreviewChoreographer = new KeyPreviewChoreographer(mKeyPreviewDrawParams);

        final int moreKeysKeyboardLayoutId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_moreKeysKeyboardLayout, 0);
        final int moreKeysKeyboardForActionLayoutId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_moreKeysKeyboardForActionLayout,
                moreKeysKeyboardLayoutId);
        mConfigShowMoreKeysKeyboardAtTouchedPoint = mainKeyboardViewAttr.getBoolean(
                R.styleable.MainKeyboardView_showMoreKeysKeyboardAtTouchedPoint, false);

        mGestureFloatingPreviewTextLingerTimeout = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureFloatingPreviewTextLingerTimeout, 0);

        mGestureFloatingTextDrawingPreview = new GestureFloatingTextDrawingPreview(
                mainKeyboardViewAttr);
        mGestureFloatingTextDrawingPreview.setDrawingView(mDrawingPreviewPlacerView);

        mGestureTrailsDrawingPreview = new GestureTrailsDrawingPreview(mainKeyboardViewAttr);
        mGestureTrailsDrawingPreview.setDrawingView(mDrawingPreviewPlacerView);

        mSlidingKeyInputDrawingPreview = new SlidingKeyInputDrawingPreview(mainKeyboardViewAttr);
        mSlidingKeyInputDrawingPreview.setDrawingView(mDrawingPreviewPlacerView);
        mainKeyboardViewAttr.recycle();

        final LayoutInflater inflater = LayoutInflater.from(getContext());
        mMoreKeysKeyboardContainer = inflater.inflate(moreKeysKeyboardLayoutId, null);
        mMoreKeysKeyboardForActionContainer = inflater.inflate(
                moreKeysKeyboardForActionLayoutId, null);
        mLanguageOnSpacebarFadeoutAnimator = loadObjectAnimator(
                languageOnSpacebarFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeoutAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeoutAnimatorResId, this);
        mAltCodeKeyWhileTypingFadeinAnimator = loadObjectAnimator(
                altCodeKeyWhileTypingFadeinAnimatorResId, this);

        mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;

        mLanguageOnSpacebarHorizontalMargin = (int)getResources().getDimension(
                R.dimen.config_language_on_spacebar_horizontal_margin);

//        placeKeys();

    }

    @Override
    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        super.setHardwareAcceleratedDrawingEnabled(enabled);
        mDrawingPreviewPlacerView.setHardwareAcceleratedDrawingEnabled(enabled);
    }

    private ObjectAnimator loadObjectAnimator(final int resId, final Object target) {
        if (resId == 0) {
            // TODO: Stop returning null.
            return null;
        }
        final ObjectAnimator animator = (ObjectAnimator)AnimatorInflater.loadAnimator(
                getContext(), resId);
        if (animator != null) {
            animator.setTarget(target);
        }
        return animator;
    }

    private static void cancelAndStartAnimators(final ObjectAnimator animatorToCancel,
            final ObjectAnimator animatorToStart) {
        if (animatorToCancel == null || animatorToStart == null) {
            // TODO: Stop using null as a no-operation animator.
            return;
        }
        float startFraction = 0.0f;
        if (animatorToCancel.isStarted()) {
            animatorToCancel.cancel();
            startFraction = 1.0f - animatorToCancel.getAnimatedFraction();
        }
        final long startTime = (long)(animatorToStart.getDuration() * startFraction);
        animatorToStart.start();
        animatorToStart.setCurrentPlayTime(startTime);
    }

    // Implements {@link TimerHander.Callbacks} method.
    @Override
    public void startWhileTypingFadeinAnimation() {
        cancelAndStartAnimators(
                mAltCodeKeyWhileTypingFadeoutAnimator, mAltCodeKeyWhileTypingFadeinAnimator);
    }

    @Override
    public void startWhileTypingFadeoutAnimation() {
        cancelAndStartAnimators(
                mAltCodeKeyWhileTypingFadeinAnimator, mAltCodeKeyWhileTypingFadeoutAnimator);
    }

    @ExternallyReferenced
    public int getLanguageOnSpacebarAnimAlpha() {
        return mLanguageOnSpacebarAnimAlpha;
    }

    @ExternallyReferenced
    public void setLanguageOnSpacebarAnimAlpha(final int alpha) {
        mLanguageOnSpacebarAnimAlpha = alpha;
        invalidateKey(mSpaceKey);
    }

    @ExternallyReferenced
    public int getAltCodeKeyWhileTypingAnimAlpha() {
        return mAltCodeKeyWhileTypingAnimAlpha;
    }

    @ExternallyReferenced
    public void setAltCodeKeyWhileTypingAnimAlpha(final int alpha) {
        if (mAltCodeKeyWhileTypingAnimAlpha == alpha) {
            return;
        }
        // Update the visual of alt-code-key-while-typing.
        mAltCodeKeyWhileTypingAnimAlpha = alpha;
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        for (final Key key : keyboard.mAltCodeKeysWhileTyping) {
            invalidateKey(key);
        }
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        PointerTracker.setKeyboardActionListener(listener);
    }

    // TODO: We should reconsider which coordinate system should be used to represent keyboard
    // event.
    public int getKeyX(final int x) {
        return Constants.isValidCoordinate(x) ? mKeyDetector.getTouchX(x) : x;
    }

    // TODO: We should reconsider which coordinate system should be used to represent keyboard
    // event.
    public int getKeyY(final int y) {
        return Constants.isValidCoordinate(y) ? mKeyDetector.getTouchY(y) : y;
    }

    /**
     * Attaches a keyboard to this view. The keyboard can be switched at any time and the
     * view will re-layout itself to accommodate the keyboard.
     * @see Keyboard
     * @see #getKeyboard()
     * @param keyboard the keyboard to display in this view
     */
    @Override
    public void setKeyboard(final Keyboard keyboard) {
        // Remove any pending messages, except dismissing preview and key repeat.
        mKeyTimerHandler.cancelLongPressTimers();

//        placeKeys(keyboard);

//        setKeyParams(mKeyPreviewDrawParams);

        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(
                keyboard, -getPaddingLeft(), -getPaddingTop() + getVerticalCorrection());
        PointerTracker.setKeyDetector(mKeyDetector);
        mMoreKeysKeyboardCache.clear();

        mSpaceKey = keyboard.getKey(Constants.CODE_SPACE);
        final int keyHeight = keyboard.mMostCommonKeyHeight - keyboard.mVerticalGap;
        mLanguageOnSpacebarTextSize = keyHeight * mLanguageOnSpacebarTextRatio;

        if (AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            if (mAccessibilityDelegate == null) {
                mAccessibilityDelegate = new MainKeyboardAccessibilityDelegate(this, mKeyDetector);
            }
            mAccessibilityDelegate.setKeyboard(keyboard);
        } else {
            mAccessibilityDelegate = null;
        }
    }

    /**
     * Enables or disables the key preview popup. This is a popup that shows a magnified
     * version of the depressed key. By default the preview is enabled.
     * @param previewEnabled whether or not to enable the key feedback preview
     * @param delay the delay after which the preview is dismissed
     */
    public void setKeyPreviewPopupEnabled(final boolean previewEnabled, final int delay) {
        mKeyPreviewDrawParams.setPopupEnabled(previewEnabled, delay);
    }

    /**
     * Enables or disables the key preview popup animations and set animations' parameters.
     *
     * @param hasCustomAnimationParams false to use the default key preview popup animations
     *   specified by keyPreviewShowUpAnimator and keyPreviewDismissAnimator attributes.
     *   true to override the default animations with the specified parameters.
     * @param showUpStartXScale from this x-scale the show up animation will start.
     * @param showUpStartYScale from this y-scale the show up animation will start.
     * @param showUpDuration the duration of the show up animation in milliseconds.
     * @param dismissEndXScale to this x-scale the dismiss animation will end.
     * @param dismissEndYScale to this y-scale the dismiss animation will end.
     * @param dismissDuration the duration of the dismiss animation in milliseconds.
     */
    public void setKeyPreviewAnimationParams(final boolean hasCustomAnimationParams,
            final float showUpStartXScale, final float showUpStartYScale, final int showUpDuration,
            final float dismissEndXScale, final float dismissEndYScale, final int dismissDuration) {
        mKeyPreviewDrawParams.setAnimationParams(hasCustomAnimationParams,
                showUpStartXScale, showUpStartYScale, showUpDuration,
                dismissEndXScale, dismissEndYScale, dismissDuration);
    }

    private void locatePreviewPlacerView() {
        getLocationInWindow(mOriginCoords);
        Log.e("KEYBOARD", mOriginCoords[0]+" "+mOriginCoords[1]);
//        mOriginCoords[0] = 10;
//        mOriginCoords[1] = 10;
        mDrawingPreviewPlacerView.setKeyboardViewGeometry(mOriginCoords, getWidth(), getHeight());
    }

    private void installPreviewPlacerView() {
        final View rootView = getRootView();
        if (rootView == null) {
            Log.w(TAG, "Cannot find root view");
            return;
        }
        final ViewGroup windowContentView = (ViewGroup)rootView.findViewById(android.R.id.content);
        // Note: It'd be very weird if we get null by android.R.id.content.
        if (windowContentView == null) {
            Log.w(TAG, "Cannot find android.R.id.content view to add DrawingPreviewPlacerView");
            return;
        }
        windowContentView.addView(mDrawingPreviewPlacerView);
    }

    // Implements {@link DrawingHandler.Callbacks} method.
    @Override
    public void dismissAllKeyPreviews() {
        mKeyPreviewChoreographer.dismissAllKeyPreviews();
        PointerTracker.setReleasedKeyGraphicsToAllKeys();
    }

    @Override
    public void showKeyPreview(final Key key) {
        // If the key is invalid or has no key preview, we must not show key preview.
        if (key == null || key.noKeyPreview()) {
            return;
        }
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        final KeyPreviewDrawParams previewParams = mKeyPreviewDrawParams;
        if (!previewParams.isPopupEnabled()) {
            previewParams.setVisibleOffset(-keyboard.mVerticalGap);
            return;
        }

        locatePreviewPlacerView();
        getLocationInWindow(mOriginCoords);
        mKeyPreviewChoreographer.placeAndShowKeyPreview(key, keyboard.mIconsSet, mKeyDrawParams,
                getWidth(), mOriginCoords, mDrawingPreviewPlacerView, isHardwareAccelerated());


//        showKeyPreview(key, mShowingKeyPreviewViews.get(key), false);

    }

    // Implements {@link TimerHandler.Callbacks} method.
    @Override
    public void dismissKeyPreviewWithoutDelay(final Key key) {
        mKeyPreviewChoreographer.dismissKeyPreview(key, false /* withAnimation */);
        // To redraw key top letter.
        invalidateKey(key);
    }

    @Override
    public void dismissKeyPreview(final Key key) {
        if (!isHardwareAccelerated()) {
            // TODO: Implement preference option to control key preview method and duration.
            mDrawingHandler.dismissKeyPreview(mKeyPreviewDrawParams.getLingerTimeout(), key);
            return;
        }
        mKeyPreviewChoreographer.dismissKeyPreview(key, true /* withAnimation */);
//        dismissKeyPreview(key, false /* withAnimation */);
    }

    public void setSlidingKeyInputPreviewEnabled(final boolean enabled) {
        mSlidingKeyInputDrawingPreview.setPreviewEnabled(enabled);
    }

    @Override
    public void showSlidingKeyInputPreview(final PointerTracker tracker) {
        locatePreviewPlacerView();
        mSlidingKeyInputDrawingPreview.setPreviewPosition(tracker);
    }

    @Override
    public void dismissSlidingKeyInputPreview() {
        mSlidingKeyInputDrawingPreview.dismissSlidingKeyInputPreview();
    }

    private void setGesturePreviewMode(final boolean isGestureTrailEnabled,
            final boolean isGestureFloatingPreviewTextEnabled) {
        mGestureFloatingTextDrawingPreview.setPreviewEnabled(isGestureFloatingPreviewTextEnabled);
        mGestureTrailsDrawingPreview.setPreviewEnabled(isGestureTrailEnabled);
    }

    // Implements {@link DrawingHandler.Callbacks} method.
    @Override
    public void showGestureFloatingPreviewText(final SuggestedWords suggestedWords) {
        locatePreviewPlacerView();
        mGestureFloatingTextDrawingPreview.setSuggetedWords(suggestedWords);
    }

    public void dismissGestureFloatingPreviewText() {
        locatePreviewPlacerView();
        mDrawingHandler.dismissGestureFloatingPreviewText(mGestureFloatingPreviewTextLingerTimeout);
    }

    @Override
    public void showGestureTrail(final PointerTracker tracker,
            final boolean showsFloatingPreviewText) {
        locatePreviewPlacerView();
        if (showsFloatingPreviewText) {
            mGestureFloatingTextDrawingPreview.setPreviewPosition(tracker);
        }
        mGestureTrailsDrawingPreview.setPreviewPosition(tracker);
    }

    // Note that this method is called from a non-UI thread.
    @SuppressWarnings("static-method")
    public void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        PointerTracker.setMainDictionaryAvailability(mainDictionaryAvailable);
    }

    public void setGestureHandlingEnabledByUser(final boolean isGestureHandlingEnabledByUser,
            final boolean isGestureTrailEnabled,
            final boolean isGestureFloatingPreviewTextEnabled) {
        PointerTracker.setGestureHandlingEnabledByUser(isGestureHandlingEnabledByUser);
        setGesturePreviewMode(isGestureHandlingEnabledByUser && isGestureTrailEnabled,
                isGestureHandlingEnabledByUser && isGestureFloatingPreviewTextEnabled);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        installPreviewPlacerView();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mDrawingPreviewPlacerView.removeAllViews();
    }

    private MoreKeysPanel onCreateMoreKeysPanel(final Key key, final Context context) {
        final MoreKeySpec[] moreKeys = key.getMoreKeys();
        if (moreKeys == null) {
            return null;
        }
        Keyboard moreKeysKeyboard = mMoreKeysKeyboardCache.get(key);
        if (moreKeysKeyboard == null) {
            // {@link KeyPreviewDrawParams#mPreviewVisibleWidth} should have been set at
            // {@link KeyPreviewChoreographer#placeKeyPreview(Key,TextView,KeyboardIconsSet,KeyDrawParams,int,int[]},
            // though there may be some chances that the value is zero. <code>width == 0</code>
            // will cause zero-division error at
            // {@link MoreKeysKeyboardParams#setParameters(int,int,int,int,int,int,boolean,int)}.
            final boolean isSingleMoreKeyWithPreview = mKeyPreviewDrawParams.isPopupEnabled()
                    && !key.noKeyPreview() && moreKeys.length == 1
                    && mKeyPreviewDrawParams.getVisibleWidth() > 0;
            final MoreKeysKeyboard.Builder builder = new MoreKeysKeyboard.Builder(
                    context, key, getKeyboard(), isSingleMoreKeyWithPreview,
                    mKeyPreviewDrawParams.getVisibleWidth(),
                    mKeyPreviewDrawParams.getVisibleHeight(), newLabelPaint(key));
            moreKeysKeyboard = builder.build();
            mMoreKeysKeyboardCache.put(key, moreKeysKeyboard);
        }

        final View container = key.isActionKey() ? mMoreKeysKeyboardForActionContainer
                : mMoreKeysKeyboardContainer;
        final MoreKeysKeyboardView moreKeysKeyboardView =
                (MoreKeysKeyboardView)container.findViewById(R.id.more_keys_keyboard_view);
        moreKeysKeyboardView.setKeyboard(moreKeysKeyboard);
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        return moreKeysKeyboardView;
    }

    // Implements {@link TimerHandler.Callbacks} method.
    /**
     * Called when a key is long pressed.
     * @param tracker the pointer tracker which pressed the parent key
     */
    @Override
    public void onLongPress(final PointerTracker tracker) {
        if (isShowingMoreKeysPanel()) {
            return;
        }
        final Key key = tracker.getKey();
        if (key == null) {
            return;
        }
        final KeyboardActionListener listener = mKeyboardActionListener;
        if (key.hasNoPanelAutoMoreKey()) {
            final int moreKeyCode = key.getMoreKeys()[0].mCode;
            tracker.onLongPressed();
            listener.onPressKey(moreKeyCode, 0 /* repeatCount */, true /* isSinglePointer */);
            listener.onCodeInput(moreKeyCode, Constants.NOT_A_COORDINATE,
                    Constants.NOT_A_COORDINATE, false /* isKeyRepeat */);
            listener.onReleaseKey(moreKeyCode, false /* withSliding */);
            return;
        }
        final int code = key.getCode();
        if (code == Constants.CODE_SPACE || code == Constants.CODE_LANGUAGE_SWITCH) {
            // Long pressing the space key invokes IME switcher dialog.
            if (listener.onCustomRequest(Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER)) {
                tracker.onLongPressed();
                listener.onReleaseKey(code, false /* withSliding */);
                return;
            }
        }
        openMoreKeysPanel(key, tracker);
    }

    private void openMoreKeysPanel(final Key key, final PointerTracker tracker) {
        final MoreKeysPanel moreKeysPanel = onCreateMoreKeysPanel(key, getContext());
        if (moreKeysPanel == null) {
            return;
        }

        final int[] lastCoords = CoordinateUtils.newInstance();
        tracker.getLastCoordinates(lastCoords);
        final boolean keyPreviewEnabled = mKeyPreviewDrawParams.isPopupEnabled()
                && !key.noKeyPreview();
        // The more keys keyboard is usually horizontally aligned with the center of the parent key.
        // If showMoreKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
        // keys keyboard is placed at the touch point of the parent key.
        final int pointX = (mConfigShowMoreKeysKeyboardAtTouchedPoint && !keyPreviewEnabled)
                ? CoordinateUtils.x(lastCoords)
                : key.getX() + key.getWidth() / 2;
        // The more keys keyboard is usually vertically aligned with the top edge of the parent key
        // (plus vertical gap). If the key preview is enabled, the more keys keyboard is vertically
        // aligned with the bottom edge of the visible part of the key preview.
        // {@code mPreviewVisibleOffset} has been set appropriately in
        // {@link KeyboardView#showKeyPreview(PointerTracker)}.
        final int pointY = key.getY() + mKeyPreviewDrawParams.getVisibleOffset();
        moreKeysPanel.showMoreKeysPanel(this, this, pointX, pointY, mKeyboardActionListener);
        tracker.onShowMoreKeysPanel(moreKeysPanel);
        // TODO: Implement zoom in animation of more keys panel.
        dismissKeyPreviewWithoutDelay(key);
    }

    public boolean isInDraggingFinger() {
        if (isShowingMoreKeysPanel()) {
            return true;
        }
        return PointerTracker.isAnyInDraggingFinger();
    }

    @Override
    public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
        locatePreviewPlacerView();
        panel.showInParent(mDrawingPreviewPlacerView);
        mMoreKeysPanel = panel;
        dimEntireKeyboard(true /* dimmed */);
    }

    public boolean isShowingMoreKeysPanel() {
        return mMoreKeysPanel != null && mMoreKeysPanel.isShowingInParent();
    }

    @Override
    public void onCancelMoreKeysPanel() {
        PointerTracker.dismissAllMoreKeysPanels();
    }

    @Override
    public void onDismissMoreKeysPanel() {
        dimEntireKeyboard(false /* dimmed */);
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.removeFromParent();
            mMoreKeysPanel = null;
        }
    }

    public void startDoubleTapShiftKeyTimer() {
        mKeyTimerHandler.startDoubleTapShiftKeyTimer();
    }

    public void cancelDoubleTapShiftKeyTimer() {
        mKeyTimerHandler.cancelDoubleTapShiftKeyTimer();
    }

    public boolean isInDoubleTapShiftKeyTimeout() {
        return mKeyTimerHandler.isInDoubleTapShiftKeyTimeout();
    }

    @Override
    public boolean onTouchEvent(final MotionEvent me) {
        if (getKeyboard() == null) {
            return false;
        }
        if (mNonDistinctMultitouchHelper != null) {
            if (me.getPointerCount() > 1 && mKeyTimerHandler.isInKeyRepeat()) {
                // Key repeating timer will be canceled if 2 or more keys are in action.
                mKeyTimerHandler.cancelKeyRepeatTimers();
            }
            // Non distinct multitouch screen support
            mNonDistinctMultitouchHelper.processMotionEvent(me, mKeyDetector);
            return true;
        }
        return processMotionEvent(me);
    }

    public boolean processMotionEvent(final MotionEvent me) {
        final int index = me.getActionIndex();
        final int id = me.getPointerId(index);
        final PointerTracker tracker = PointerTracker.getPointerTracker(id);
        // When a more keys panel is showing, we should ignore other fingers' single touch events
        // other than the finger that is showing the more keys panel.
        if (isShowingMoreKeysPanel() && !tracker.isShowingMoreKeysPanel()
                && PointerTracker.getActivePointerTrackerCount() == 1) {
            return true;
        }
        tracker.processMotionEvent(me, mKeyDetector);
        return true;
    }

    public void cancelAllOngoingEvents() {
        mKeyTimerHandler.cancelAllMessages();
        mDrawingHandler.cancelAllMessages();
        dismissAllKeyPreviews();
        dismissGestureFloatingPreviewText();
        dismissSlidingKeyInputPreview();
        PointerTracker.dismissAllMoreKeysPanels();
        PointerTracker.cancelAllPointerTrackers();
    }

    public void closing() {
        cancelAllOngoingEvents();
        mMoreKeysKeyboardCache.clear();
    }

    public void onHideWindow() {
        onDismissMoreKeysPanel();
        final MainKeyboardAccessibilityDelegate accessibilityDelegate = mAccessibilityDelegate;
        if (accessibilityDelegate != null
                && AccessibilityUtils.getInstance().isAccessibilityEnabled()) {
            accessibilityDelegate.onHideWindow();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onHoverEvent(final MotionEvent event) {
        final MainKeyboardAccessibilityDelegate accessibilityDelegate = mAccessibilityDelegate;
        if (accessibilityDelegate != null
                && AccessibilityUtils.getInstance().isTouchExplorationEnabled()) {
            return accessibilityDelegate.onHoverEvent(event);
        }
        return super.onHoverEvent(event);
    }

    public void updateShortcutKey(final boolean available) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return;
        }
        final Key shortcutKey = keyboard.getKey(Constants.CODE_SHORTCUT);
        if (shortcutKey == null) {
            return;
        }
        shortcutKey.setEnabled(available);
        invalidateKey(shortcutKey);
    }

    public void startDisplayLanguageOnSpacebar(final boolean subtypeChanged,
            final int languageOnSpacebarFormatType,
            final boolean hasMultipleEnabledIMEsOrSubtypes) {
        if (subtypeChanged) {
            KeyPreviewView.clearTextCache();
        }
        mLanguageOnSpacebarFormatType = languageOnSpacebarFormatType;
        mHasMultipleEnabledIMEsOrSubtypes = hasMultipleEnabledIMEsOrSubtypes;
        final ObjectAnimator animator = mLanguageOnSpacebarFadeoutAnimator;
        if (animator == null) {
            mLanguageOnSpacebarFormatType = LanguageOnSpacebarHelper.FORMAT_TYPE_NONE;
        } else {
            if (subtypeChanged
                    && languageOnSpacebarFormatType != LanguageOnSpacebarHelper.FORMAT_TYPE_NONE) {
                setLanguageOnSpacebarAnimAlpha(Constants.Color.ALPHA_OPAQUE);
                if (animator.isStarted()) {
                    animator.cancel();
                }
                animator.start();
            } else {
                if (!animator.isStarted()) {
                    mLanguageOnSpacebarAnimAlpha = mLanguageOnSpacebarFinalAlpha;
                }
            }
        }
        invalidateKey(mSpaceKey);
    }

    private void dimEntireKeyboard(final boolean dimmed) {
        final boolean needsRedrawing = mNeedsToDimEntireKeyboard != dimmed;
        mNeedsToDimEntireKeyboard = dimmed;
        if (needsRedrawing) {
            invalidateAllKeys();
        }
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);

        // Overlay a dark rectangle to dim.
        if (mNeedsToDimEntireKeyboard) {
            canvas.drawRect(0.0f, 0.0f, getWidth(), getHeight(), mBackgroundDimAlphaPaint);
        }
    }

    @Override
    protected void onDrawKeyTopVisuals(final Key key, final Canvas canvas, final Paint paint,
            final KeyDrawParams params) {
        if (key.altCodeWhileTyping() && key.isEnabled()) {
            params.mAnimAlpha = mAltCodeKeyWhileTypingAnimAlpha;
        }
        super.onDrawKeyTopVisuals(key, canvas, paint, params);
        final int code = key.getCode();
        if (code == Constants.CODE_SPACE) {
            // If input language are explicitly selected.
            if (mLanguageOnSpacebarFormatType != LanguageOnSpacebarHelper.FORMAT_TYPE_NONE) {
                drawLanguageOnSpacebar(key, canvas, paint);
            }
            // Whether space key needs to show the "..." popup hint for special purposes
            if (key.isLongPressEnabled() && mHasMultipleEnabledIMEsOrSubtypes) {
                drawKeyPopupHint(key, canvas, paint, params);
            }
        } else if (code == Constants.CODE_LANGUAGE_SWITCH) {
            drawKeyPopupHint(key, canvas, paint, params);
        }
    }

    private boolean fitsTextIntoWidth(final int width, final String text, final Paint paint) {
        final int maxTextWidth = width - mLanguageOnSpacebarHorizontalMargin * 2;
        paint.setTextScaleX(1.0f);
        final float textWidth = TypefaceUtils.getStringWidth(text, paint);
        if (textWidth < width) {
            return true;
        }

        final float scaleX = maxTextWidth / textWidth;
        if (scaleX < MINIMUM_XSCALE_OF_LANGUAGE_NAME) {
            return false;
        }

        paint.setTextScaleX(scaleX);
        return TypefaceUtils.getStringWidth(text, paint) < maxTextWidth;
    }

    // Layout language name on spacebar.
    private String layoutLanguageOnSpacebar(final Paint paint,
            final InputMethodSubtype subtype, final int width) {
        // Choose appropriate language name to fit into the width.
        if (mLanguageOnSpacebarFormatType == LanguageOnSpacebarHelper.FORMAT_TYPE_FULL_LOCALE) {
            final String fullText = SpacebarLanguageUtils.getFullDisplayName(subtype);
            if (fitsTextIntoWidth(width, fullText, paint)) {
                return fullText;
            }
        }

        final String middleText = SpacebarLanguageUtils.getMiddleDisplayName(subtype);
        if (fitsTextIntoWidth(width, middleText, paint)) {
            return middleText;
        }

        return "";
    }

    private void drawLanguageOnSpacebar(final Key key, final Canvas canvas, final Paint paint) {
        final int width = key.getWidth();
        final int height = key.getHeight();
        paint.setTextAlign(Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT);
        paint.setTextSize(mLanguageOnSpacebarTextSize);
        final InputMethodSubtype subtype = getKeyboard().mId.mSubtype;
        final String language = layoutLanguageOnSpacebar(paint, subtype, width);
        // Draw language text with shadow
        final float descent = paint.descent();
        final float textHeight = -paint.ascent() + descent;
        final float baseline = height / 2 + textHeight / 2;
        if (mLanguageOnSpacebarTextShadowRadius > 0.0f) {
            paint.setShadowLayer(mLanguageOnSpacebarTextShadowRadius, 0, 0,
                    mLanguageOnSpacebarTextShadowColor);
        } else {
            paint.clearShadowLayer();
        }
        paint.setColor(mLanguageOnSpacebarTextColor);
        paint.setAlpha(mLanguageOnSpacebarAnimAlpha);
        canvas.drawText(language, width / 2, baseline - descent, paint);
        paint.clearShadowLayer();
        paint.setTextScaleX(1.0f);
    }

    @Override
    public void deallocateMemory() {
        super.deallocateMemory();
        mDrawingPreviewPlacerView.deallocateMemory();
    }


    /*
    * Added By Tarun
    * */


    private final HashMap<Key,FrameLayout> mShowingKeyPreviewViews = new HashMap<>();
//    private final ArrayDeque<FrameLayout> mFreeKeyPreviewViews = new ArrayDeque<>();


    public void placeKeyss(Keyboard keyboard){

//        getLocationInWindow(mOriginCoords);

        for(Key key : keyboard.getSortedKeys()){
//            Log.i("KEYS", key.getLabel());

            if (keyboard.hasKey(key)) {
                final int x = key.getX() + getPaddingLeft();
                final int y = key.getY() + getPaddingTop();
//                mWorkingRect.set(x, y, x + key.getWidth(), y + key.getHeight());

                Log.i("COORDINATES", "X: "+x+"  Y: "+y+" Width: "+key.getWidth()+" Height: "+key.getHeight());
            }



//            KeyPreviewView keyPreviewView = getKeyPreviewView(getContext(), this);
//            TextView keyTextView = getKeyPreviewView(getContext(), this, key);
            TextView keyTextView = new TextView(getContext());




            final int iconId = key.getIconId();
            if (iconId != KeyboardIconsSet.ICON_UNDEFINED) {
                keyTextView.setCompoundDrawables(null, null, null, key.getPreviewIcon(keyboard.mIconsSet));
                keyTextView.setText(null);
            }else{
                keyTextView.setText(key.getLabel());
            }



//            keyTextView.setBackgroundColor(Color.GREEN);
            keyTextView.setGravity(Gravity.CENTER);


            keyTextView.setTypeface(key.selectTypeface(mKeyDrawParams));
            keyTextView.setTextSize(key.selectTextSize(mKeyDrawParams)/2);
//            keyTextView.setTextColor(key.selectTextColor(mKeyDrawParams));
            keyTextView.setTextColor(Color.WHITE);

            FrameLayout keyTextLayout = getKeyLayout(getContext(), this, key, keyTextView);
//            keyTextLayout.setBackgroundColor(Color.CYAN);

            placeKeyPreview(key, keyTextLayout, keyboard.mIconsSet, mKeyDrawParams,
                    getWidth(), mOriginCoords);

        }
    }

    private TextView getKeyPreviewView(Context context, ViewGroup placerView, Key key){
        KeyPreviewView keyPreviewView = new KeyPreviewView(context, null /* attrs */);

        TextView keyTextView = new TextView(context, null);

//        ketTextView.setBackgroundResource(mKeyPreviewDrawParams.mPreviewBackgroundResId);
        MarginLayoutParams marginLayoutParams = ViewLayoutUtils.newLayoutParam(placerView, key.getDrawWidth(), key.getHeight());

        placerView.addView(keyTextView, marginLayoutParams);

        return keyTextView;

    }

    private FrameLayout getKeyLayout(Context context, ViewGroup placerView, Key key, TextView keyTextView){

        FrameLayout parent = mShowingKeyPreviewViews.get(key);
        if (parent != null) {
            return parent;
        }

        parent = new FrameLayout(context, null);

//        TextView keyTextView = new TextView(context, null);

        FrameLayout container = new FrameLayout(context, null);


        placerView.addView(parent, ViewLayoutUtils.newLayoutParam(placerView, 0, 0));

        parent.addView(container, ViewLayoutUtils.newLayoutParam(parent, 0,0));
//        MarginLayoutParams parentLayoutparams = (MarginLayoutParams)parent.getLayoutParams();
//        parentLayoutparams.width = 100;
//        parentLayoutparams.height = 100;
//        parentLayoutparams.setMargins(10, 10, 10, 10);




//        ketTextView.setBackgroundResource(mKeyPreviewDrawParams.mPreviewBackgroundResId);
//        MarginLayoutParams marginLayoutParams = ViewLayoutUtils.newLayoutParam(container, key.getDrawWidth(), key.getHeight());



        container.addView(keyTextView, ViewLayoutUtils.newLayoutParam(container, key.getDrawWidth(), key.getHeight()));
//        MarginLayoutParams mlp = (MarginLayoutParams)keyTextView.getLayoutParams();
//        mlp.width = 50;
//        mlp.height = 50;
//        mlp.setMargins(10, 10, 10, 10);

//        FrameLayout.LayoutParams fllp = (FrameLayout.LayoutParams)keyTextView.getLayoutParams();
//        fllp.gravity = Gravity.CENTER;
//        fllp.bottomMargin = 10;

//        container.setBackgroundResource(mKeyPreviewDrawParams.mPreviewBackgroundResId);

        mShowingKeyPreviewViews.put(key, parent);

        return parent;

    }

    private void placeKeyPreview(final Key key, final View keyPreviewView,
                                 final KeyboardIconsSet iconsSet, final KeyDrawParams drawParams,
                                 final int keyboardViewWidth, final int[] originCoords) {
//        keyPreviewView.setPreviewVisual(key, iconsSet, drawParams);
        keyPreviewView.measure(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//        mKeyPreviewDrawParams.setGeometry(keyPreviewView);
        final int previewWidth = keyPreviewView.getMeasuredWidth();
        final int previewHeight = mKeyPreviewDrawParams.mPreviewHeight;
        final int keyDrawWidth = key.getDrawWidth();
        // The key preview is horizontally aligned with the center of the visible part of the
        // parent key. If it doesn't fit in this {@link KeyboardView}, it is moved inward to fit and
        // the left/right background is used if such background is specified.
        final int keyPreviewPosition;
        int previewX = key.getDrawX() - (previewWidth - keyDrawWidth) / 2
                + CoordinateUtils.x(originCoords);
        if (previewX < 0) {
            previewX = 0;
            keyPreviewPosition = KeyPreviewView.POSITION_LEFT;
        } else if (previewX > keyboardViewWidth - previewWidth) {
            previewX = keyboardViewWidth - previewWidth;
            keyPreviewPosition = KeyPreviewView.POSITION_RIGHT;
        } else {
            keyPreviewPosition = KeyPreviewView.POSITION_MIDDLE;
        }
        final boolean hasMoreKeys = (key.getMoreKeys() != null);
//        keyPreviewView.setPreviewBackground(hasMoreKeys, keyPreviewPosition);
        // The key preview is placed vertically above the top edge of the parent key with an
        // arbitrary offset.
        final int previewY = key.getY() - previewHeight + mKeyPreviewDrawParams.mPreviewOffset
                + CoordinateUtils.y(originCoords);


//        ViewLayoutUtils.placeViewAt(keyPreviewView, previewX, previewY, previewWidth, previewHeight);
//        keyPreviewView.setPivotX(previewWidth / 2.0f);
//        keyPreviewView.setPivotY(previewHeight);

        ViewLayoutUtils.placeViewAt(keyPreviewView, key.getX(), key.getY(), previewWidth, previewHeight);
        keyPreviewView.setPivotX(key.getDrawWidth() / 4.0f);
        keyPreviewView.setPivotY(key.getHeight());
    }

    /*private void slideUp(FrameLayout frameLayout){
        if(frameLayout == null){
            return;
        }

        FrameLayout container = (FrameLayout)frameLayout.getChildAt(0);

        if(container == null){
            return;
        }

        TextView view = (TextView) container.getChildAt(0);
        view.setBackgroundColor(Color.BLUE);
        FrameLayout.LayoutParams fllp = (FrameLayout.LayoutParams)container.getLayoutParams();
        fllp.bottomMargin = 50;
        fllp.height += 50;
//        view.setTranslationY(-100.0f);
        container.invalidate();
        container.requestLayout();



//        container.addView(keyTextView, ViewLayoutUtils.newLayoutParam(container, key.getDrawWidth(), key.getHeight()));
//        MarginLayoutParams mlp = (MarginLayoutParams)keyTextView.getLayoutParams();
//        mlp.width = 50;
//        mlp.height = 50;
//        mlp.setMargins(10, 10, 10, 10);

    }

    private void slideDown(FrameLayout frameLayout){
        if(frameLayout == null){
            return;
        }

        FrameLayout container = (FrameLayout)frameLayout.getChildAt(0);

        if(container == null){
            return;
        }

        TextView view = (TextView) container.getChildAt(0);
        view.setBackgroundColor(Color.CYAN);
        FrameLayout.LayoutParams fllp = (FrameLayout.LayoutParams)container.getLayoutParams();
//        fllp.gravity = Gravity.CENTER;
        fllp.bottomMargin = 0;
        fllp.height -= 50;
//        view.setTranslationY(0.0f);
        container.invalidate();
        container.requestLayout();
    }

    private void showKeyPreview(final Key key, final FrameLayout keyPreviewView,
                                final boolean withAnimation) {
        if (!withAnimation) {
//            keyPreviewView.setVisibility(View.VISIBLE);
            slideUp(keyPreviewView);
//            mShowingKeyPreviewViews.put(key, keyPreviewView);
            return;
        }

        // Show preview with animation.
        final Animator showUpAnimator = createShowUpAnimator(key, keyPreviewView);
        final Animator dismissAnimator = createDismissAnimator(key, keyPreviewView);
        final KeyPreviewAnimators animators = new KeyPreviewAnimators(
                showUpAnimator, dismissAnimator);
        keyPreviewView.setTag(animators);
        animators.startShowUp();
    }


    private void dismissKeyPreview(final Key key, final boolean withAnimation) {
        if (key == null) {
            return;
        }
        final FrameLayout keyPreviewView = mShowingKeyPreviewViews.get(key);
        if (keyPreviewView == null) {
            return;
        }
        final Object tag = keyPreviewView.getTag();
        if (withAnimation) {
            if (tag instanceof KeyPreviewAnimators) {
                final KeyPreviewAnimators animators = (KeyPreviewAnimators)tag;
                animators.startDismiss();
                return;
            }
        }
        // Dismiss preview without animation.
//        mShowingKeyPreviewViews.remove(key);
        if (tag instanceof Animator) {
            ((Animator)tag).cancel();
        }
        keyPreviewView.setTag(null);
//        keyPreviewView.setVisibility(View.INVISIBLE);
//        mFreeKeyPreviewViews.add(keyPreviewView);
        slideDown(keyPreviewView);
    }

    public Animator createShowUpAnimator(final Key key, final FrameLayout keyPreviewView) {
        final Animator animator = mKeyPreviewDrawParams.createShowUpAnimator(keyPreviewView);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(final Animator animator) {
                showKeyPreview(key, keyPreviewView, false *//* withAnimation *//*);
            }
        });
        return animator;
    }

    private Animator createDismissAnimator(final Key key, final FrameLayout keyPreviewView) {
        final Animator animator = mKeyPreviewDrawParams.createDismissAnimator(keyPreviewView);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(final Animator animator) {
                dismissKeyPreview(key, false *//* withAnimation *//*);
            }
        });
        return animator;
    }

    private static class KeyPreviewAnimators extends AnimatorListenerAdapter {
        private final Animator mShowUpAnimator;
        private final Animator mDismissAnimator;

        public KeyPreviewAnimators(final Animator showUpAnimator, final Animator dismissAnimator) {
            mShowUpAnimator = showUpAnimator;
            mDismissAnimator = dismissAnimator;
        }

        public void startShowUp() {
            mShowUpAnimator.start();
        }

        public void startDismiss() {
            if (mShowUpAnimator.isRunning()) {
                mShowUpAnimator.addListener(this);
                return;
            }
            mDismissAnimator.start();
        }

        @Override
        public void onAnimationEnd(final Animator animator) {
            mDismissAnimator.start();
        }
    }*/

}
