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

import android.app.IRemoteTaskHandler;
import android.app.IRemoteTaskInstanceBroker;
import android.view.InputEvent;
import android.os.Bundle;

import java.util.List;

/** {@hide} */
interface ICrossDeviceService {
    /**
     * Inject input event.
     * @param event Input event
     * @param injectInputEventModeAsync Inject input event mode
     */
    void injectInputEvent(in InputEvent event, int injectInputEventModeAsync);

    /**
     * Get package name running in main display
     * for input injection optimization
     * @return top running package name
     */
    String getTopRunningPackageName();

    /**
     * Wake up phone
     * @param time wake up time
     */
    void wakeUp(long time);

    /**
     * Update white list for this service
     * @param activityList activity list
     */
    void updateBackgroundActivityList(in List<String> activityList);

    /**
     * Check white list for this service with current binder caller
     * @return true if call user id is in white list, false otherwise
     */
    boolean isFromBackgroundWhiteList();

    /**
     * Check white list for this service
     * @param uid caller user id that need to be check from white list
     * @return true if call user id is in white list, false otherwise
     */
    boolean isFromBackgroundWhiteListByUid(int uid);

    /**
     * get device temperature
     * @return device temperature
     */
    Bundle getDeviceTemperature();

    /**
    * Check if touch is required
    * @Param packageName app package name
    * @return true if touch is required
    */
    boolean isTouchRequired(String packageName);

    /**
     * Get {@link #android.app.IRemoteTaskInstanceBroker}
     * @return IRemoteTaskInstanceBroker stub
     */
    IRemoteTaskInstanceBroker getRemoteTaskInstanceBroker();

    /**
     * Register remote task handler
     * @param handler remote task handler
     */
    void registerRemoteTaskHandler(in IRemoteTaskHandler handler);

    /**
     * Unregister remote task handler
     * @param handler remote task handler
     */
    void unRegisterRemoteTaskHandler(in IRemoteTaskHandler handler);

    /**
     * get the threshold of overheat
     * @return OverheatThreshold
     */
    Bundle getOverheatThreshold();

    /**
     * get the threshold of resume overheat
     * @return ResumeOverheatThreshold
     */
    Bundle getResumeOverheatThreshold();

    /**
     * get the maximum app count of Suspend
     * @return SuspendDefaultCacheSize
     */
    Bundle getMaximumAppCount();

    /**
     * Set media projection permission granted
     * @param uid user id to be grant permission
     */
     void setPermissionGranted(int uid);

    /**
     * Get media projection permission granted
     * @param uid user id to get permission grant status
     * @return true if permission granted, false otherwise
     */
     boolean getPermissionGranted(int uid);
}