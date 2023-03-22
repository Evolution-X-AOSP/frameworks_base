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

package android.app;

import android.app.SystemTaskContext;
import android.app.RemoteTaskParams;
import android.app.RemoteTaskInfo;
import android.content.Intent;

import java.util.List;

/** {@hide} */
interface IRemoteTaskHandler {

    /**
     * Call service to verify remote task
     * @param context remote task context
     * @return remote task options
     */
    RemoteTaskParams verifyRemoteTask(in SystemTaskContext taskContext);

    /**
     * Get current remote task info list
     * @return remote task info list
     */
    List getRemoteTaskInfoList();

    /**
     * Call service to start a new remote task
     * @param taskInfo remote task informations
     */
    oneway void activateRemoteTask(in RemoteTaskInfo taskInfo);

    /**
     * Notify service the remote task has been removed
     * @param taskId remote task id to be removed
     */
    oneway void notifyRemoteTaskRemoved(int taskId);

    /**
     * Notify service the display of remote task is switched
     * @param displayId current display id
     */
    oneway void notifyDisplaySwitched(int displayId);

    /**
     * Notify service the device availability state changed
     * @param deviceAvailabilityState the device availability state value
     */
    oneway void notifyDeviceAvailabilityStateChanged(int deviceAvailabilityState);

    /**
     * Notify service an empty UUID remote task detected
     * @param intent the intent of remote task
     */
    oneway void notifyRemoteTaskEmptyUUIDetected(in Intent intent);

    /**
     * Notify service new remote task failed to create
     * @param uuid uuid of remote task
     * @param reason reason of the fail
     */
    oneway void notifyRemoteTaskCreationFailed(in String uuid, int reason);

    /**
     * Notify remote task service to handle showing secure content changed
     *
     * @param taskId                  remote task id
     * @param isShowingSecuredContent if remote task is showing secure content
     */
    oneway void notifyRemoteShowingSecuredContentChanged(int taskId, boolean isShowingSecuredContent);

    /**
     * Notify that client app is died
     */
    oneway void notifyClientDied();
}