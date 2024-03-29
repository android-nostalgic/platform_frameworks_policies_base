/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.internal.policy.impl;

import com.android.internal.R;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Canvas;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewManager;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Manages creating, showing, hiding and resetting the keyguard.  Calls back
 * via {@link com.android.internal.policy.impl.KeyguardViewCallback} to poke
 * the wake lock and report that the keyguard is done, which is in turn,
 * reported to this class by the current {@link KeyguardViewBase}.
 */
public class KeyguardViewManager {
    private final static boolean DEBUG = false;
    private static String TAG = "KeyguardViewManager";

    private final Context mContext;
    private final ViewManager mViewManager;
    private final KeyguardViewCallback mCallback;
    private final KeyguardViewProperties mKeyguardViewProperties;

    private final KeyguardUpdateMonitor mUpdateMonitor;

    private FrameLayout mKeyguardHost;
    private KeyguardViewBase mKeyguardView;

    private boolean mScreenOn = false;

    /**
     * @param context Used to create views.
     * @param viewManager Keyguard will be attached to this.
     * @param callback Used to notify of changes.
     */
    public KeyguardViewManager(Context context, ViewManager viewManager,
            KeyguardViewCallback callback, KeyguardViewProperties keyguardViewProperties, KeyguardUpdateMonitor updateMonitor) {
        mContext = context;
        mViewManager = viewManager;
        mCallback = callback;
        mKeyguardViewProperties = keyguardViewProperties;

        mUpdateMonitor = updateMonitor;
    }

    /**
     * Helper class to host the keyguard view.
     */
    private static class KeyguardViewHost extends FrameLayout {
        private final KeyguardViewCallback mCallback;

        private KeyguardViewHost(Context context, KeyguardViewCallback callback) {
            super(context);
            mCallback = callback;
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            super.dispatchDraw(canvas);
            mCallback.keyguardDoneDrawing();
        }
    }

    /**
     * Show the keyguard.  Will handle creating and attaching to the view manager
     * lazily.
     */
    public synchronized void show() {
        if (DEBUG) Log.d(TAG, "show()");

        if (mKeyguardHost == null) {
            if (DEBUG) Log.d(TAG, "keyguard host is null, creating it...");

            mKeyguardHost = new KeyguardViewHost(mContext, mCallback);

            final int stretch = ViewGroup.LayoutParams.FILL_PARENT;
            int flags = WindowManager.LayoutParams.FLAG_DITHER
                    | WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    stretch, stretch, WindowManager.LayoutParams.TYPE_KEYGUARD,
                    flags, PixelFormat.OPAQUE);
            lp.setTitle("Keyguard");

            mViewManager.addView(mKeyguardHost, lp);
        }

        if (mKeyguardView == null) {
            if (DEBUG) Log.d(TAG, "keyguard view is null, creating it...");
            mKeyguardView = mKeyguardViewProperties.createKeyguardView(mContext, mUpdateMonitor);
            mKeyguardView.setId(R.id.lock_screen);
            mKeyguardView.setCallback(mCallback);

            final ViewGroup.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.FILL_PARENT,
                    ViewGroup.LayoutParams.FILL_PARENT);

            mKeyguardHost.addView(mKeyguardView, lp);

            if (mScreenOn) {
                mKeyguardView.onScreenTurnedOn();
            }
        }

        mKeyguardHost.setVisibility(View.VISIBLE);
        mKeyguardView.requestFocus();
    }

    /**
     * Reset the state of the view.
     */
    public synchronized void reset() {
        if (DEBUG) Log.d(TAG, "reset()");
        if (mKeyguardView != null) {
            mKeyguardView.reset();
        }
    }

    public synchronized void onScreenTurnedOff() {
        if (DEBUG) Log.d(TAG, "onScreenTurnedOff()");
        mScreenOn = false;
        if (mKeyguardView != null) {
            mKeyguardView.onScreenTurnedOff();
        }
    }

    public synchronized void onScreenTurnedOn() {
        if (DEBUG) Log.d(TAG, "onScreenTurnedOn()");
        mScreenOn = true;
        if (mKeyguardView != null) {
            mKeyguardView.onScreenTurnedOn();
        }
    }

    public synchronized void verifyUnlock() {
        if (DEBUG) Log.d(TAG, "verifyUnlock()");
        show();
        mKeyguardView.verifyUnlock();
    }

    /**
     * A key has woken the device.  We use this to potentially adjust the state
     * of the lock screen based on the key.
     *
     * The 'Tq' suffix is per the documentation in {@link android.view.WindowManagerPolicy}.
     * Be sure not to take any action that takes a long time; any significant
     * action should be posted to a handler.
     *
     * @param keyCode The wake key.
     */
    public void wakeWhenReadyTq(int keyCode) {
        if (DEBUG) Log.d(TAG, "wakeWhenReady(" + keyCode + ")");
        if (mKeyguardView != null) {
            mKeyguardView.wakeWhenReadyTq(keyCode);
        }
    }

    /**
     * Hides the keyguard view
     */
    public synchronized void hide() {
        if (DEBUG) Log.d(TAG, "hide()");
        if (mKeyguardHost != null) {
            mKeyguardHost.setVisibility(View.INVISIBLE);
            if (mKeyguardView != null) {
                mKeyguardHost.removeView(mKeyguardView);
                mKeyguardView.cleanUp();
                mKeyguardView = null;
            }
        }
    }

    /**
     * @return Whether the keyguard is showing
     */
    public synchronized boolean isShowing() {
        return (mKeyguardHost != null && mKeyguardHost.getVisibility() == View.VISIBLE);
    }
}
