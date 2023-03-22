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

import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.ServiceManager;
import android.app.RemoteTaskInfo;
import android.app.SystemTaskContext;
import android.app.RemoteTaskParams;
import android.app.RemoteTaskConstants;
import android.app.ICrossDeviceService;
import android.text.TextUtils;

import java.util.List;
import java.util.ArrayList;

/**
 * Specially handle the communication between Device Integration Service and AOSP about
 * remote task instance event. If AOSP wants to communicate with sdk about remote
 * task instance event, it should use this class.
 */

class CrossDeviceServiceDelegate implements CrossDeviceService.OnClientDiedListener {
    private static final String TAG = CrossDeviceServiceDelegate.class.getSimpleName();

    private final Handler mHandler;
    private final RemoteTaskManager mRemoteTaskManager;
    private CrossDeviceService mCrossDeviceService;

    CrossDeviceServiceDelegate(RemoteTaskManager manager, Handler handler) {
        mHandler = handler;
        mRemoteTaskManager = manager;
        mCrossDeviceService = getCrossDeviceService();
        if (mCrossDeviceService != null) {
            mCrossDeviceService.setClientDiedListener(this);
        }
    }

    /**
     * Return cross device service
     *
     * @return {@link ICrossDeviceService}
     */
    private CrossDeviceService getCrossDeviceService() {
        IBinder b = ServiceManager.getService(Context.CROSS_DEVICE_SERVICE);
        return (CrossDeviceService) ICrossDeviceService.Stub.asInterface(b);
    }

    /**
     * Inform remote task service for device availability state change,
     * OEM should call this API when device availability state changed
     * for example device temperature too high, memory/cpu resource exhuasted.
     * Please refer {@link RemoteTaskConstants} for the meaning of device
     * availabiltiy state code
     *
     * @param deviceAvailabilityState current device state
     */
    void handleDeviceAvailabilityStateChanged(int deviceAvailabilityState) {
        if (isAnyClientAliveInService()) {
            mHandler.post(() -> mCrossDeviceService
                    .notifyDeviceAvailabilityStateChanged(deviceAvailabilityState));
        }
    }

    @Override
    public void onClientDied() {
        if (mRemoteTaskManager != null) {
            mRemoteTaskManager.recycleAll();
        }
    }

    /**
     * Check for service is alive or not.
     *
     * @return remote task display infos
     */
    boolean isAnyClientAliveInService() {
        if (mCrossDeviceService == null) {
            mCrossDeviceService = getCrossDeviceService();
            if (mCrossDeviceService != null) {
                mCrossDeviceService.setClientDiedListener(this);
            }
        }
        if (mCrossDeviceService != null) {
            return mCrossDeviceService.isAnyClientAliveInService();
        }
        return false;
    }

    /**
     * Acquir remote task service that we need to verify remote task context.
     *
     * @param taskContext System Task context
     * @return Remote Task launch options
     */
    RemoteTaskParams handleVerifyRemoteTask(SystemTaskContext taskContext) {
        if (isAnyClientAliveInService()) {
            return mCrossDeviceService.verifyRemoteTask(taskContext);
        }

        RemoteTaskLogger.e(TAG, "Service is died, default as common scenario");
        return RemoteTaskParams.create(taskContext.getUuid(),
                RemoteTaskConstants.FLAG_TASK_LAUNCH_SCENARIO_COMMON, taskContext.getDisplayId());
    }

    /**
     * Inform remote task service that we should create new remote task instance.
     *
     * @param intent   Intent
     * @param taskInfo Remote Task Information
     */
    void handleActivateRemoteTask(Intent intent, RemoteTaskInfo taskInfo) {
        if (isAnyClientAliveInService()) {
            mHandler.post(() -> {
                String uuid = taskInfo.getUuid();
                if (TextUtils.isEmpty(uuid)) {
                    mCrossDeviceService.notifyRemoteTaskEmptyUUIDetected(intent);
                } else {
                    mCrossDeviceService.activateRemoteTask(taskInfo);
                }
            });
            return;
        }
        RemoteTaskLogger.e(TAG, "Service is died, need to restart system");
    }

    /**
     * Get current remote task info list
     *
     * @return Remote Task info list
     */
    List getRemoteTaskInfoList() {
        if (isAnyClientAliveInService()) {
            return mCrossDeviceService.getRemoteTaskInfoList();
        }

        RemoteTaskLogger.e(TAG, "Service is died, default as empty task info list");
        List defaultTaskList = new ArrayList<String>();
        return defaultTaskList;
    }

    /**
     * Inform remote task service to close remote task
     *
     * @param reason   the reason why we close this remote task
     * @param uuid     current uuid
     */
    void handleCloseRemoteTask(int taskId) {
        if (isAnyClientAliveInService()) {
            RemoteTaskLogger.d(TAG, "closeTask appExecutionContainerService!= null");
            mHandler.post(() -> {
                try {
                    mCrossDeviceService.notifyRemoteTaskRemoved(taskId);
                } catch (SecurityException e) {
                    //TODO need to eliminate this exception or close will not work correctly
                    RemoteTaskExceptionHandler.onExceptionThrow(e);
                } catch (Exception e) {
                    RemoteTaskExceptionHandler.onExceptionThrow(e);
                }
            });
            return;
        }
        RemoteTaskLogger.e(TAG, "Service is died, need to restart system");
    }

    /**
     * Notify remote task service to handle showing secure content changed
     *
     * @param taskId                  remote task id
     * @param isShowingSecuredContent if remote task is showing secure content
     */
    void handleRemoteShowingSecuredContentChanged(int taskId,
                                                  boolean isShowingSecuredContent) {
        if (isAnyClientAliveInService()) {
            mHandler.post(() -> mCrossDeviceService
                    .notifyRemoteShowingSecuredContentChanged(taskId, isShowingSecuredContent));
            return;
        }
        RemoteTaskLogger.e(TAG, "Service is died, need to restart system");
    }

    /**
     * Inform remote task service to handle launch same app flow
     *
     * @param displayId current display id
     */
    void notifyDisplaySwitched(int displayId) {
        if (isAnyClientAliveInService()) {
            mHandler.post(() -> mCrossDeviceService.notifyDisplaySwitched(displayId));
            return;
        }
        RemoteTaskLogger.e(TAG, "Service is died, need to restart system");
    }

    /**
     * Check white list for user id
     * @param uid caller user id that need to be check from white list
     * @return true if call user id is in white list, false otherwise
     */
    boolean isFromBackgroundWhiteList(int uid) {
        if (isAnyClientAliveInService()) {
            return mCrossDeviceService.isFromBackgroundWhiteListByUid(uid);
        }
        return false;
    }
}