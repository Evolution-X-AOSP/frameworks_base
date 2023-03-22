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

import android.app.RemoteTaskInfo;
import android.app.ActivityOptions;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.text.TextUtils;
import android.view.Display;

import java.util.List;

/**
 * Handle all intent except launch intent.
 */
class OtherIntentPreferredTaskDisplayQualifier extends BasePreferredTaskDisplayQualifier {
    OtherIntentPreferredTaskDisplayQualifier(RemoteTaskManager manager) {
        super(manager);
    }

    /**
     * @param defaultArea  TaskDisplayArea
     * @param intent       Intent
     * @param sourceRecord ActivityRecord
     * @param record       ActivityRecord
     * @param options      ActivityOptions
     * @return TaskDisplayArea if we find one
     */
    @Override
    TaskDisplayArea queryPreferredDisplay(TaskDisplayArea defaultArea, Intent intent,
                                          ActivityRecord sourceRecord, ActivityRecord record,
                                          ActivityOptions options) {
        //This intent launch inside the app
        List<RemoteTaskInfo> taskList = mManager.getRemoteTaskInfoList();
        if (sourceRecord != null) {
            int sourceDisplayId = sourceRecord.getDisplayId();
            if (!mManager.anyTaskExist(sourceRecord.getRootTask())) {
                return null;
            }
            if (record.launchMode != ActivityInfo.LAUNCH_SINGLE_TASK
                    && (intent.getFlags() & Intent.FLAG_ACTIVITY_NEW_TASK) == 0) {
                return null;
            }

            for (RemoteTaskInfo taskInfo : taskList) {
                if (taskInfo == null) {
                    continue;
                }
                Task task = mManager.anyTaskForId(taskInfo.getTaskId());
                int displayId = taskInfo.getDisplayId();
                if (task == null || displayId == sourceDisplayId) {
                    continue;
                }
                ActivityRecord intentActivity = mRootWindowContainer.findTask(
                        record, task.getDisplayArea());
                if (intentActivity != null) {
                    return intentActivity.getDisplayArea();
                }
            }
            //No way! have to find from Default display
            ActivityRecord intentActivity
                    = mRootWindowContainer.findTask(record,
                    mRootWindowContainer.getDefaultTaskDisplayArea());
            if (intentActivity != null) {
                return intentActivity.getDisplayArea();
            }
        } else {
            //sourceRecord = null, better to find related display by affinity, we only need to check
            //our remote task, eg, Tencent video. For other case, return default TaskDisplayArea.
            if (options != null && options.getLaunchDisplayId() > Display.DEFAULT_DISPLAY) {
                return defaultArea;
            }

            for (RemoteTaskInfo taskInfo : taskList) {
                Task task = mManager.anyTaskForId(taskInfo.getTaskId());
                int displayId = taskInfo.getDisplayId();
                if (task == null) {
                    continue;
                }
                if (displayId != Display.INVALID_DISPLAY) {
                    if (TextUtils.equals(task.affinity, record.taskAffinity)
                            && TextUtils.equals(task.realActivity.getPackageName(), record.packageName)) {
                        ActivityRecord activityRecord = mRootWindowContainer
                                .findTask(record, task.getDisplayArea());
                        if (activityRecord != null) {
                            return activityRecord.getDisplayArea();
                        }
                    }
                }
            }

            //No way! have to find from Default display
            ActivityRecord intentActivity
                    = mRootWindowContainer.findTask(record,
                    mRootWindowContainer.getDefaultTaskDisplayArea());
            if (intentActivity != null) {
                return intentActivity.getDisplayArea();
            }
        }
        return null;
    }
}
