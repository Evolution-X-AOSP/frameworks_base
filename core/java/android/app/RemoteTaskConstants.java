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

import android.annotation.SuppressLint;

/**
 * @hide
 */
@SuppressLint("StaticUtils")
public final class RemoteTaskConstants {
    /**
     *  device temperature
     *
     *  @hide
     */
    public static final String DEVICE_TEMPERATURE = "deviceintegration:device.temperature";

    /**
     *  threshold for overheat
     *
     *  @hide
     */
    public static final String OVERHEAT_THRESHOLD = "deviceintegration:overheat.suspend.threshold";

    /**
     *  threshold for resuming from overheat
     *
     *  @hide
     */
    public static final String RESUME_OVERHEAT_THRESHOLD = "deviceintegration:overheat.resume.threshold";

    /**
     *  the maximum concurrent running app count
     *
     *  @hide
     */
    public static final String MAXIMUM_RUNNING_APP_COUNT = "deviceintegration:maximum.app.count";

    /**
     * UUID for each remote task
     *
     * @hide
     */
    public static final String KEY_REMOTE_TASK_UUID = "deviceintegration:remotetask.uuid";

    /**
     * security token for each remote task
     *
     * @hide
     */
    public static final String KEY_REMOTE_TASK_SECURITY_TOKEN = "deviceintegration:remotetask.security.token";

    /**
     * Key for identifying remote task
     *
     * @hide
     */
    public static final String KEY_REMOTE_TASK_LAUNCH_OPTION = "deviceintegration:remotetask.launchOption";

    /**
     * value for remote task, REMOTE_TASK_FLAG_LAUNCH means we need start this task as
     * remote task.
     */
    public static final int REMOTE_TASK_FLAG_LAUNCH = 32;
    public static final int REMOTE_TASK_FLAG_DEFAULT = 0;

    /**
     * value for device availability state
     */
    /* Device availability constrained, New invocations will be denied */
    @SuppressLint("OverlappingConstants")
    public static final int DEVICE_AVAILABILITY_STATE_CONSTRAINED = 4;
    /* Device availability free, New invocations are acceptable */
    @SuppressLint("OverlappingConstants")
    public static final int DEVICE_AVAILABILITY_STATE_FREE = 8;
    /* Device availability critical, Current handlers will be killed */
    @SuppressLint("OverlappingConstants")
    public static final int DEVICE_AVAILABILITY_STATE_CRITICAL = 16;

    /**
     * Flag for task launch scenarios
     */
    private static final int FIRST_LOCAL_TASK_LAUNCH_SCENARIO_FLAG = 0;
    private static final int FIRST_REMOTE_TASK_LAUNCH_SCENARIO_FLAG = 100;

    @SuppressLint("OverlappingConstants")
    public static final int FLAG_TASK_LAUNCH_SCENARIO_COMMON = FIRST_LOCAL_TASK_LAUNCH_SCENARIO_FLAG;
    @SuppressLint("OverlappingConstants")
    public static final int FLAG_TASK_LAUNCH_SCENARIO_VDTOMD = FIRST_LOCAL_TASK_LAUNCH_SCENARIO_FLAG + 1;
    @SuppressLint("OverlappingConstants")
    public static final int FLAG_TASK_LAUNCH_SCENARIO_DUP = FIRST_LOCAL_TASK_LAUNCH_SCENARIO_FLAG + 2;

    @SuppressLint("OverlappingConstants")
    public static final int FLAG_TASK_LAUNCH_SCENARIO_CLIENT = FIRST_REMOTE_TASK_LAUNCH_SCENARIO_FLAG;
    @SuppressLint("OverlappingConstants")
    public static final int FLAG_TASK_LAUNCH_SCENARIO_MDTOVD = FIRST_REMOTE_TASK_LAUNCH_SCENARIO_FLAG + 1;
    @SuppressLint("OverlappingConstants")
    public static final int FLAG_TASK_LAUNCH_SCENARIO_ATOA = FIRST_REMOTE_TASK_LAUNCH_SCENARIO_FLAG + 2;
    @SuppressLint("OverlappingConstants")
    public static final int FLAG_TASK_LAUNCH_SCENARIO_EDGE = FIRST_REMOTE_TASK_LAUNCH_SCENARIO_FLAG + 3;
}
