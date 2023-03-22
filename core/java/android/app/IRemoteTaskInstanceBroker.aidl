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

import android.graphics.Rect;

/** {@hide} */
interface IRemoteTaskInstanceBroker {
    /**
    * Get current orientation status of remote task
    * @Param taskId task id
    * @return orientation state value
    */
    int getOrientation(int taskId);

    /**
    * Get remote task bounds
    * @Param taskId task id
    * @return remote task bounds
    */
    Rect getRemoteTaskBounds(int taskId);

    /**
    * Check if remote task instance is showing secure content
    * @Param taskId task id
    * @return true if current remote task instance is showing secure content
    */
    boolean isRemoteTaskInstanceShowingSecuredContent(int taskId);

    /**
    * Remove remote task
    * @Param taskId task id
    */
    oneway void removeRemoteTask(int taskId);

    /**
    * Notify remote display orientation changed
    * @Param taskId task id
    * @param newOrientation new orientation state
    */
    oneway void notifyRemoteDisplayOrientationChanged(int taskId, int newOrientation);
}