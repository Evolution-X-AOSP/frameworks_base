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

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Notice: This file only use for self-host, release version should remove this class.
 *
 * All associated exceptions with remote task should deliver here.
 * Once exception happen, stack traces and the latest 100 RemoteTaskLogs will be store in folder /data/anr/rth-logs
 */
class RemoteTaskExceptionHandler {
    private static final boolean DEBUG = RemoteTaskLogger.REMOTE_TASK_DEBUG;
    private static final String TAG = RemoteTaskExceptionHandler.class.getSimpleName();
    private static final String LOG_ROOT_DIR = "/data/anr/rth-logs";
    private static final int MAX_LINES = 100;

    private static ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private static LogHistoryHelper mLogHistoryHelper = new LogHistoryHelper();

    public static void onExceptionThrow(Exception exception) {
        if (DEBUG) {
            RemoteTaskLogger.d(TAG, "Remote Task Exception Detected");

            mExecutor.submit(() -> {
                String curTime = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
                        .format(new java.util.Date());
                String logInfo = mergeLogInfo(Log.getStackTraceString(exception), mLogHistoryHelper.toString(), curTime);

                dumpStackToFile(logInfo, curTime);
            });
        }
    }

    public static void onLogSave(String type, String tag, String msg) {
        mExecutor.submit(() -> {
            mLogHistoryHelper.add("Type: " + type + " - " + "Tag: " + tag + " - " + "Msg: " + msg + "\n");
        });
    }

    private static void dumpStackToFile(String logInfo, String curTime) {
        String filePath = LOG_ROOT_DIR + "/rth_log_" + curTime;
        File logDir = new File(LOG_ROOT_DIR);
        File logFile = new File(filePath);
        try {
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            logFile.createNewFile();
            try (FileOutputStream fos = new FileOutputStream(logFile)) {
                fos.write(logInfo.getBytes());
                fos.flush();
            }
        } catch (Exception e) {
            RemoteTaskLogger.d(TAG, "Wrote Log file failed: " + e.toString());
        }
    }

    private static String mergeLogInfo(String stackTraceString, String logHistory, String curTime) {
        StringBuilder sb = new StringBuilder();
        sb.append("========================== RemoteTaskException ==========================\n");
        sb.append("Time: ").append(curTime).append("\n");
        sb.append("Stack Traces: \n").append(stackTraceString);
        sb.append("Log Hostory: \n").append(logHistory);

        return sb.toString();
    }

    // Make sure LogHistoryHelper runs on sigle thread
    private static class LogHistoryHelper {
        private Queue<String> q = new LinkedList<>();

        public void add(String log) {
            if (log == null) {
                return;
            }
            if (q.size() >= MAX_LINES) {
                q.poll();
            }
            q.offer(log);
         }

         public String toString() {
            StringBuilder sb = new StringBuilder();
            for (String log : q) {
                sb.append(log);
            }
            sb.append("Log History End: " + q.size() + " logs.\n");
            return sb.toString();
         }
    }
}
