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
import android.view.Display;

import java.util.List;

/**
 * This class only handles launch intent.
 */
class LaunchIntentPreferredTaskDisplayQualifier extends BasePreferredTaskDisplayQualifier {


    LaunchIntentPreferredTaskDisplayQualifier(RemoteTaskManager manager) {
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
        if (!RemoteTaskManager.isLaunchIntent(intent)) {
            return null;
        }
        //If it's launch intent, think about launch same app scenarios
        int targetDisplayId = Display.INVALID_DISPLAY;
        if (options != null) {
            targetDisplayId = options.getLaunchDisplayId();
            //Call from Device Integration Service, if targetDisplayId != Display.INVALID_DISPLAY,
            //defaultArea will be assigned to a VD TaskDisplayArea.
            if (targetDisplayId != Display.INVALID_DISPLAY) {
                //Try to find same ActivityRecord from MD, if we find one, we should move this task
                // to our VD
                ActivityRecord intentActivity = mRootWindowContainer.findTask(record,
                        mRootWindowContainer.getDefaultTaskDisplayArea());
                if (intentActivity != null
                        && intentActivity.getDisplayId() == Display.DEFAULT_DISPLAY
                        && intentActivity.getTask().getRootActivity() == intentActivity) {
                    //Find one in MD, double check if it's root activity in task, if so, set out
                    //to move this task to specific VD.
                    return intentActivity.getDisplayArea();
                } else {
                    return defaultArea;
                }
            }
        }
        //In most of cases, this intent comes from MD, we need to take VD2MD into consideration.
        List<RemoteTaskInfo> taskList = mManager.getRemoteTaskInfoList();
        for (RemoteTaskInfo taskInfo : taskList) {
            if (taskInfo == null) {
                continue;
            }
            int displayId = taskInfo.getDisplayId();
            if (displayId == Display.INVALID_DISPLAY) {
                continue;
            }
            DisplayContent content = mRootWindowContainer.getDisplayContent(displayId);
            if (content != null) {
                ActivityRecord intentActivity
                        = mRootWindowContainer.findTask(record, content.getDefaultTaskDisplayArea());
                if (intentActivity != null
                        && intentActivity.getTask().getRootActivity() == intentActivity) {
                    //Find a VD, return this
                    return intentActivity.getDisplayArea();
                }
            }
        }
        return null;
    }

}
