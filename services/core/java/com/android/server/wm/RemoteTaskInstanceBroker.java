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

import android.os.Parcel;
import android.os.RemoteException;
import android.content.Context;
import android.content.res.Configuration;
import android.app.RemoteTaskConstants;
import android.app.ActivityClient;
import android.app.CrossDeviceManager;
import android.app.IRemoteTaskInstanceBroker;
import android.graphics.Rect;

import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_180;
import static android.view.Surface.ROTATION_270;
import static android.view.Surface.ROTATION_90;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.view.WindowManager.LayoutParams.FLAG_SECURE;

/**
 * System service for managing Remote Task Instance features
 */
public class RemoteTaskInstanceBroker extends IRemoteTaskInstanceBroker.Stub {
    private static final String TAG = RemoteTaskInstanceBroker.class.getSimpleName();

    private final Context mContext;
    private final ActivityTaskManagerService mActivityTaskManagerService;

    public RemoteTaskInstanceBroker(Context context, ActivityTaskManagerService service) {
        mContext = context;
        mActivityTaskManagerService = service;
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (!CrossDeviceManager.isCallerAllowed(mContext)) {
            throw new RemoteException("Caller is not allowed");
        }
        return super.onTransact(code, data, reply, flags);
    }

    /**
     * Get current orientation status of remote task
     * @Param taskId task id
     * @return orientation state value
     */
    @Override
    public int getOrientation(int taskId) {
        Task task = mActivityTaskManagerService.mRootWindowContainer.anyTaskForId(taskId);
        if (task != null) {
            return task.getConfiguration().orientation;
        }
        RemoteTaskLogger.e(TAG, "Task == null, orientation = undefined");
        return Configuration.ORIENTATION_UNDEFINED;
    }

    /**
     * Get remote task bounds
     * @Param taskId task id
     * @return remote task bounds
     */
    @Override
    public Rect getRemoteTaskBounds(int taskId) {
        Rect rect = null;
        Task task = mActivityTaskManagerService.mRootWindowContainer.anyTaskForId(taskId);
        if (task == null) {
            return null;
        }
        rect = task.getBounds();
        if (rect == null && task.getTaskInfo() != null) {
            rect = task.getTaskInfo().configuration.windowConfiguration.getAppBounds();
        }
        return rect;
    }

    /**
     * Check if remote task instance is showing secure content
     * @Param taskId task id
     * @return true if current remote task instance is showing secure content
     */
    @Override
    public boolean isRemoteTaskInstanceShowingSecuredContent(int taskId) {
        Task task = mActivityTaskManagerService.mRootWindowContainer.anyTaskForId(taskId);
        if (task == null) {
            return false;
        }
        WindowManagerService windowManagerService = mActivityTaskManagerService.mWindowManager;
        DisplayContent displayContent
                = windowManagerService.mRoot.getDisplayContent(task.getDisplayId());
        if ((displayContent != null) && (displayContent.mCurrentFocus != null)) {
            return (displayContent.mCurrentFocus.mAttrs.flags & FLAG_SECURE) != 0;
        }
        return false;
    }

    /**
     * Remove remote task
     * @Param taskId task id
     */
    @Override
    public void removeRemoteTask(int taskId) {
        Task task = mActivityTaskManagerService.mRootWindowContainer.anyTaskForId(taskId);
        if (task == null) {
            mActivityTaskManagerService.getRemoteTaskManager().notifyRemoteTaskClosed(taskId);
            return;
        }

        /*
         * If aosp task info correspond to this handler has been set, try to close and
         * remove this aosp task.
         */
        RemoteTaskLogger.d(TAG, "Task != null task Id =" + taskId);
        mActivityTaskManagerService.getRemoteTaskManager().closeRemoteTask(taskId);
    }

    /**
     * Notify remote display orientation changed
     * @Param taskId task id
     * @param newOrientation new orientation state
     */
    @Override
    public void notifyRemoteDisplayOrientationChanged(int taskId, int newOrientation) {
        Task task = mActivityTaskManagerService.mRootWindowContainer.anyTaskForId(taskId);
        if (task == null) {
            return;
        }
        ActivityRecord topRecord = task.topRunningActivity();
        if (topRecord != null && topRecord.info.isFixedOrientation()) {
            return;
        }
        if (task.getTaskInfo() == null) {
            return;
        }
        switch (newOrientation) {
            case ROTATION_0:
                newOrientation = SCREEN_ORIENTATION_PORTRAIT;
                break;
            case ROTATION_90:
                newOrientation = SCREEN_ORIENTATION_LANDSCAPE;
                break;
            case ROTATION_180:
                newOrientation = SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                break;
            case ROTATION_270:
                newOrientation = SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                break;
            default:
                newOrientation = SCREEN_ORIENTATION_UNSPECIFIED;
        }
        ActivityClient.getInstance().setRequestedOrientation(task.getTaskInfo()
                .getToken().asBinder(), newOrientation);
    }
}