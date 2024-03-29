/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import com.android.internal.telephony.ITelephony;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.internal.R;

/**
 * Displays a dialer like interface to unlock the SIM PIN.
 */
public class SimUnlockScreen extends LinearLayout implements KeyguardScreen, View.OnClickListener,
        KeyguardUpdateMonitor.ConfigurationChangeCallback {

    private static final int DIGIT_PRESS_WAKE_MILLIS = 5000;

    private final KeyguardUpdateMonitor mUpdateMonitor;
    private final KeyguardScreenCallback mCallback;

    private final boolean mCreatedWithKeyboardOpen;

    private TextView mHeaderText;
    private EditText mPinText;

    private TextView mOkButton;
    private TextView mEmergencyCallButton;

    private View mBackSpaceButton;

    private final int[] mEnteredPin = {0, 0, 0, 0, 0, 0, 0, 0};
    private int mEnteredDigits = 0;

    private ProgressDialog mSimUnlockProgressDialog = null;

    private static final char[] DIGITS = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};

    public SimUnlockScreen(Context context, KeyguardUpdateMonitor updateMonitor,
            KeyguardScreenCallback callback) {
        super(context);
        mUpdateMonitor = updateMonitor;
        mCallback = callback;
        mCreatedWithKeyboardOpen = mUpdateMonitor.isKeyboardOpen();

        if (mCreatedWithKeyboardOpen) {
            LayoutInflater.from(context).inflate(R.layout.keyguard_screen_sim_pin_landscape, this, true);
        } else {
            LayoutInflater.from(context).inflate(R.layout.keyguard_screen_sim_pin_portrait, this, true);
            new TouchInput();
        }

        mHeaderText = (TextView) findViewById(R.id.headerText);
        mPinText = (EditText) findViewById(R.id.pinDisplay);
        mBackSpaceButton = findViewById(R.id.backspace);
        mBackSpaceButton.setOnClickListener(this);

        mEmergencyCallButton = (TextView) findViewById(R.id.emergencyCall);
        mOkButton = (TextView) findViewById(R.id.ok);

        mHeaderText.setText(R.string.keyguard_password_enter_pin_code);
        mPinText.setFocusable(false);

        mEmergencyCallButton.setOnClickListener(this);
        mOkButton.setOnClickListener(this);

        mUpdateMonitor.registerConfigurationChangeCallback(this);
        setFocusableInTouchMode(true);
    }

    /** {@inheritDoc} */
    public void onPause() {

    }

    /** {@inheritDoc} */
    public void onResume() {
        // start fresh
        mHeaderText.setText(R.string.keyguard_password_enter_pin_code);
        mPinText.setText("");
    }

    /** {@inheritDoc} */
    public void cleanUp() {
        mUpdateMonitor.removeCallback(this);
    }


    /**
     * Since the IPC can block, we want to run the request in a separate thread
     * with a callback.
     */
    private abstract class CheckSimPin extends Thread {

        private final String mPin;

        protected CheckSimPin(String pin) {
            mPin = pin;
        }

        abstract void onSimLockChangedResponse(boolean success);

        @Override
        public void run() {
            try {
                final boolean result = ITelephony.Stub.asInterface(ServiceManager
                        .checkService("phone")).supplyPin(mPin);
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(result);
                    }
                });
            } catch (RemoteException e) {
                post(new Runnable() {
                    public void run() {
                        onSimLockChangedResponse(false);
                    }
                });
            }
        }
    }
    
    public void onClick(View v) {
        if (v == mBackSpaceButton) {
            final Editable digits = mPinText.getText();
            final int len = digits.length();
            if (len > 0) {
                digits.delete(len-1, len);
                mEnteredDigits--;
            }
            mCallback.pokeWakelock();
        } else if (v == mEmergencyCallButton) {
            mCallback.takeEmergencyCallAction();
        } else if (v == mOkButton) {
            checkPin();
        }
    }

    private Dialog getSimUnlockProgressDialog() {
        if (mSimUnlockProgressDialog == null) {
            mSimUnlockProgressDialog = new ProgressDialog(mContext);
            mSimUnlockProgressDialog.setMessage(
                    mContext.getString(R.string.lockscreen_sim_unlock_progress_dialog_message));
            mSimUnlockProgressDialog.setIndeterminate(true);
            mSimUnlockProgressDialog.setCancelable(false);
            mSimUnlockProgressDialog.getWindow().setType(
                    WindowManager.LayoutParams.TYPE_KEYGUARD_DIALOG);
            mSimUnlockProgressDialog.getWindow().setFlags(
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                    WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }
        return mSimUnlockProgressDialog;
    }

    private void checkPin() {

        // make sure that the pin is at least 4 digits long.
        if (mEnteredDigits < 4) {
            // otherwise, display a message to the user, and don't submit.
            mHeaderText.setText(R.string.invalidPin);
            mPinText.setText("");
            mEnteredDigits = 0;
            mCallback.pokeWakelock();
            return;
        }
        getSimUnlockProgressDialog().show();

        new CheckSimPin(mPinText.getText().toString()) {
            void onSimLockChangedResponse(boolean success) {
                getSimUnlockProgressDialog().hide();
                if (success) {
                    // before closing the keyguard, report back that
                    // the sim is unlocked so it knows right away
                    mUpdateMonitor.reportSimPinUnlocked();
                    mCallback.goToUnlockScreen();
                } else {
                    mHeaderText.setText(R.string.keyguard_password_wrong_pin_code);
                    mPinText.setText("");
                    mEnteredDigits = 0;
                    mCallback.pokeWakelock();
                }
            }
        }.start();
    }


    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            mCallback.goToLockScreen();
            return true;
        }

        final char match = event.getMatch(DIGITS);
        if (match != 0) {
            reportDigit(match - '0');
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            if (mEnteredDigits > 0) {
                mPinText.onKeyDown(keyCode, event);
                mEnteredDigits--;
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_ENTER) {
            checkPin();
            return true;
        }

        return false;
    }

    private void reportDigit(int digit) {
        if (mEnteredDigits == 0) {
            mPinText.setText("");
        }
        if (mEnteredDigits == 8) {
            return;
        }
        mPinText.append(Integer.toString(digit));
        mEnteredPin[mEnteredDigits++] = digit;
    }

    public void onOrientationChange(boolean inPortrait) {}

    public void onKeyboardChange(boolean isKeyboardOpen) {
        if (isKeyboardOpen != mCreatedWithKeyboardOpen) {
            mCallback.recreateMe();
        }
    }

    /**
     * Helper class to handle input from touch dialer.  Only relevant when
     * the keyboard is shut.
     */
    private class TouchInput implements View.OnClickListener {
        private TextView mZero;
        private TextView mOne;
        private TextView mTwo;
        private TextView mThree;
        private TextView mFour;
        private TextView mFive;
        private TextView mSix;
        private TextView mSeven;
        private TextView mEight;
        private TextView mNine;
        private TextView mCancelButton;

        private TouchInput() {
            mZero = (TextView) findViewById(R.id.zero);
            mOne = (TextView) findViewById(R.id.one);
            mTwo = (TextView) findViewById(R.id.two);
            mThree = (TextView) findViewById(R.id.three);
            mFour = (TextView) findViewById(R.id.four);
            mFive = (TextView) findViewById(R.id.five);
            mSix = (TextView) findViewById(R.id.six);
            mSeven = (TextView) findViewById(R.id.seven);
            mEight = (TextView) findViewById(R.id.eight);
            mNine = (TextView) findViewById(R.id.nine);
            mCancelButton = (TextView) findViewById(R.id.cancel);

            mZero.setText("0");
            mOne.setText("1");
            mTwo.setText("2");
            mThree.setText("3");
            mFour.setText("4");
            mFive.setText("5");
            mSix.setText("6");
            mSeven.setText("7");
            mEight.setText("8");
            mNine.setText("9");

            mZero.setOnClickListener(this);
            mOne.setOnClickListener(this);
            mTwo.setOnClickListener(this);
            mThree.setOnClickListener(this);
            mFour.setOnClickListener(this);
            mFive.setOnClickListener(this);
            mSix.setOnClickListener(this);
            mSeven.setOnClickListener(this);
            mEight.setOnClickListener(this);
            mNine.setOnClickListener(this);
            mCancelButton.setOnClickListener(this);
        }


        public void onClick(View v) {
            if (v == mCancelButton) {
                mCallback.goToLockScreen();
                return;
            }

            final int digit = checkDigit(v);
            if (digit >= 0) {
                mCallback.pokeWakelock(DIGIT_PRESS_WAKE_MILLIS);
                reportDigit(digit);
            }
        }

        private int checkDigit(View v) {
            int digit = -1;
            if (v == mZero) {
                digit = 0;
            } else if (v == mOne) {
                digit = 1;
            } else if (v == mTwo) {
                digit = 2;
            } else if (v == mThree) {
                digit = 3;
            } else if (v == mFour) {
                digit = 4;
            } else if (v == mFive) {
                digit = 5;
            } else if (v == mSix) {
                digit = 6;
            } else if (v == mSeven) {
                digit = 7;
            } else if (v == mEight) {
                digit = 8;
            } else if (v == mNine) {
                digit = 9;
            }
            return digit;
        }
    }
}
