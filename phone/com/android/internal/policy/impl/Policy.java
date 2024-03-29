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

import android.content.Context;

import com.android.internal.policy.IPolicy;
import com.android.internal.policy.impl.PhoneLayoutInflater;
import com.android.internal.policy.impl.PhoneWindow;
import com.android.internal.policy.impl.PhoneWindowManager;

/**
 * {@hide}
 */

// Simple implementation of the policy interface that spawns the right
// set of objects
public class Policy implements IPolicy {

    public PhoneWindow makeNewWindow(Context context) {
        return new PhoneWindow(context);
    }

    public PhoneLayoutInflater makeNewLayoutInflater(Context context) {
        return new PhoneLayoutInflater(context);
    }

    public PhoneWindowManager makeNewWindowManager() {
        return new PhoneWindowManager();
    }
}
