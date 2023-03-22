/*
 * Copyright (C) 2023 Microsoft Corporation
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

package com.android.server.wm;

import android.util.Log;

/**
 * Simple Logger especially for remote task
 */
class RemoteTaskLogger {
    static final boolean REMOTE_TASK_DEBUG = true;

    static void e(String tag, String msg) {
        if (REMOTE_TASK_DEBUG) {
            Log.e(tag, msg);
            RemoteTaskExceptionHandler.onLogSave("Error", tag, msg);
        }
    }

    static void i(String tag, String msg) {
        if (REMOTE_TASK_DEBUG) {
            Log.i(tag, msg);
            RemoteTaskExceptionHandler.onLogSave("Info", tag, msg);
        }
    }

    static void d(String tag, String msg) {
        if (REMOTE_TASK_DEBUG) {
            Log.d(tag, msg);
            RemoteTaskExceptionHandler.onLogSave("Debug", tag, msg);
        }
    }

    static void w(String tag, String msg) {
        if (REMOTE_TASK_DEBUG) {
            Log.w(tag, msg);
            RemoteTaskExceptionHandler.onLogSave("Warning", tag, msg);
        }
    }
}
