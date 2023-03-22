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
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.app.RemoteTaskInfo;
import android.app.SystemTaskContext;
import android.app.RemoteTaskParams;
import android.app.ActivityOptions;
import android.app.IApplicationThread;
import android.app.RemoteTaskConstants;
import android.app.WindowConfiguration;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.DeviceIntegrationUtils;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.view.Display;
import android.view.WindowManager;

import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static android.app.RemoteTaskConstants.REMOTE_TASK_FLAG_DEFAULT;
import static android.app.RemoteTaskConstants.FLAG_TASK_LAUNCH_SCENARIO_COMMON;
import static android.app.RemoteTaskConstants.FLAG_TASK_LAUNCH_SCENARIO_CLIENT;
import static android.app.RemoteTaskConstants.FLAG_TASK_LAUNCH_SCENARIO_DUP;
import static android.app.RemoteTaskConstants.FLAG_TASK_LAUNCH_SCENARIO_VDTOMD;
import static android.app.RemoteTaskConstants.FLAG_TASK_LAUNCH_SCENARIO_MDTOVD;
import static android.app.RemoteTaskConstants.REMOTE_TASK_FLAG_LAUNCH;
import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.CATEGORY_HOME;
import static android.content.Intent.CATEGORY_SECONDARY_HOME;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP;
import static android.content.pm.ActivityInfo.LAUNCH_SINGLE_TOP;

/**
 * Responsible for managing remote task event.
 */
public class RemoteTaskManager {
    private static final String THREAD_NAME = "RemoteTaskThread";
    private static final String TAG = RemoteTaskManager.class.getSimpleName();
    private static final String REASON_PROCESS_RESTART = "restartActivityProcess";

    final ActivityTaskManagerService mActivityTaskManagerService;
    private final boolean mDeviceIntegrationDisabled;
    private final CrossDeviceServiceDelegate mCrossDeviceServiceDelegate;
    private final List<BasePreferredTaskDisplayQualifier> mDisplayQualifierList;
    private final Handler mRemoteTaskHandler;
    private RootWindowContainer mRootWindowContainer;

    RemoteTaskManager(ActivityTaskManagerService service) {
        mActivityTaskManagerService = service;
        mDeviceIntegrationDisabled = DeviceIntegrationUtils.DISABLE_DEVICE_INTEGRATION;
        HandlerThread handlerThread = new HandlerThread(THREAD_NAME);
        handlerThread.start();
        mDisplayQualifierList = new ArrayList<>();
        mDisplayQualifierList.add(new LaunchIntentPreferredTaskDisplayQualifier(this));
        mDisplayQualifierList.add(new OtherIntentPreferredTaskDisplayQualifier(this));
        mRemoteTaskHandler = new Handler(handlerThread.getLooper());
        mCrossDeviceServiceDelegate = new CrossDeviceServiceDelegate(this, mRemoteTaskHandler);
    }

    void setRootWindowContainer(RootWindowContainer container) {
        mRootWindowContainer = container;
        for (BasePreferredTaskDisplayQualifier qualifier : mDisplayQualifierList) {
            qualifier.setRootWindowContainer(container);
        }
    }

    /**
     * Get caller activity reccord
     *
     * @param thread         IApplicationThread
     * @param sourceRecord   ActivityRecord
     * @param callingPid     Caller pid
     * @param callingUid     Caller uid
     * @param realCallingPid Real caller pid
     * @param realCallingUid Real caller uid
     * @return Caller activity reccord
     */
    ActivityRecord getCallerRecord(IApplicationThread thread, ActivityRecord sourceRecord,
                                   int callingPid, int callingUid,
                                   int realCallingPid, int realCallingUid) {
        ActivityRecord callerRecord = sourceRecord;
        if (callerRecord == null) {
            WindowProcessController callerApp
                    = mActivityTaskManagerService.getProcessController(thread);
            if (callerApp == null) {
                callerApp =
                        mActivityTaskManagerService.getProcessController(realCallingPid,
                                realCallingUid);
            }
            if (callerApp == null) {
                callerApp =
                        mActivityTaskManagerService.getProcessController(callingPid, callingUid);
            }
            if (callerApp != null) {
                callerRecord = callerApp.getTopActivity();
            }
        }
        return callerRecord;
    }

    /**
     * Get current remote task info array
     * @return array of remote task info array
     */
    @NonNull
    List<RemoteTaskInfo> getRemoteTaskInfoList() {
       return  (List<RemoteTaskInfo>)mCrossDeviceServiceDelegate.getRemoteTaskInfoList();
    }

    /**
     * Entry for all tasks who want to launch in remote task/VD that may need verify.
     *
     * @param container    RootWindowContainer
     * @param thread       IApplicationThread
     * @param reuseTask    Task
     * @param sourceRecord ActivityRecord
     * @param record       ActivityRecord
     * @param options      ActivityOptions
     * @return modified ActivityOptions if needed
     */
    ActivityOptions verifyRemoteTaskIfNeeded(RootWindowContainer container,
                                             IApplicationThread thread, int callingPid,
                                             int callingUid, int realCallingPid,
                                             int realCallingUid, Task reuseTask,
                                             ActivityRecord sourceRecord, ActivityRecord record,
                                             ActivityOptions options) {
        if(!isAnyClientAlive()) {
            return options;
        }
        //Step 1 : get caller activity record.
        ActivityRecord callerRecord = getCallerRecord(thread, sourceRecord, callingPid,
                callingUid, realCallingPid, realCallingUid);
        //Step 2 : Is current launch an edge case or not.
        final boolean isEdgeCase = (sourceRecord != null && sourceRecord.finishing && sourceRecord.isRootOfTask()
                                && sourceRecord.getTask().getDisplayId() > Display.DEFAULT_DISPLAY
                                && sourceRecord.getTask().getTopNonFinishingActivity() == null);
         /*
          * Step 3 : Build up system task launch context. Including launch uuid and flag, perfered
          * disply id, caller package name, caller display id, perfered reuse display id, launch from
          * home intent or not, edge case or not.
          */
        String uuid = "";
        String securityToken = "";
        int launchFlag = RemoteTaskConstants.REMOTE_TASK_FLAG_DEFAULT;
        int displayId = Display.INVALID_DISPLAY;
        if (options != null) {
           uuid = options.getRemoteUuid();
           securityToken = options.getRemoteSecurityToken();
           launchFlag = options.getRemoteTaskFlag();
           displayId = options.getLaunchDisplayId();
        }
        final int sourceDisplayId = callerRecord != null ?
                callerRecord.getTask().getDisplayId() : Display.INVALID_DISPLAY;
        final int reuseDisplayId = reuseTask != null ? reuseTask.getDisplayId() : Display.INVALID_DISPLAY;

        SystemTaskContext taskContext = SystemTaskContext.create(uuid, securityToken, launchFlag,
                record.launchedFromPackage, displayId, sourceDisplayId, reuseDisplayId,
                isHomeIntent(record.intent), isEdgeCase);

        /*
         * Step 4 : transact system task launch context to system privacy app to apply remote task
         * policy and return new updated task launch parameters.
         */
        RemoteTaskParams taskParams = mCrossDeviceServiceDelegate.handleVerifyRemoteTask(taskContext);
        /*
         * Step 5 : unpackage task launch parameters returned from system privacy app and build up
         * new updated task launch options including task uuid, prefered display id, launch scenario.
         */
        int launchScenario = taskParams.getLaunchScenario();
        if (launchScenario == FLAG_TASK_LAUNCH_SCENARIO_COMMON) {
            return options;
        }
        if (options == null) {
            options = ActivityOptions.makeBasic();
        }

        displayId = taskParams.getDisplayId();
        if (displayId != Display.INVALID_DISPLAY) {
            container.getDisplayContentOrCreate(displayId);
            WindowManager windowManager =
                    (WindowManager)mActivityTaskManagerService.mContext.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager != null) {
                windowManager.setDisplayImePolicy(displayId, WindowManager.DISPLAY_IME_POLICY_LOCAL);
            }
        }

        if (options != null) {
            options.setRemoteUuid(taskParams.getUuid());
            options.setRemoteTaskLaunchScenario(launchScenario);
            options.setLaunchDisplayId(displayId);
            setRemoteTaskLaunchScenario(reuseTask, options);
        }

        //Step 6 : return updated task launch options to ActivityStarter to execute activity start and task launch.
        return options;
    }

    /**
     * Activate launch remote task/VD.
     *
     * @param newTask new Task
     * @param task    Task
     * @param record       ActivityRecord
     * @param options      ActivityOptions
     */
    void activateRemoteTaskIfNeeded(boolean newTask, Task reuseTask, ActivityRecord record, ActivityOptions options) {
        if(!isAnyClientAlive()) {
            return;
        }
        Task remoteTask = null;
        if (reuseTask != null) {
            remoteTask = reuseTask;
        } else if (newTask){
            remoteTask = record.getRootTask();
        }

        if (remoteTask != null && options != null &&
                options.getRemoteTaskLaunchScenario() >= RemoteTaskConstants.FLAG_TASK_LAUNCH_SCENARIO_CLIENT) {

            String pkg = record.intent.getPackage();
            if (pkg == null && record.intent.getComponent() != null) {
                pkg = record.intent.getComponent().getPackageName();
            }
            RemoteTaskInfo taskInfo = RemoteTaskInfo.create(options.getRemoteUuid(), pkg,
                    remoteTask.getDisplayId(), remoteTask.mTaskId);
            mCrossDeviceServiceDelegate.handleActivateRemoteTask(record.intent, taskInfo);
            setRemoteTaskLaunchScenario(remoteTask, options);
        }
    }

    void setRemoteTaskLaunchScenario(Task task, ActivityOptions options) {
        if (task == null || options == null) {
            return;
        }

        int launchScenario = options.getRemoteTaskLaunchScenario();
        if (launchScenario != FLAG_TASK_LAUNCH_SCENARIO_DUP) {
            task.mLaunchScenario = launchScenario;
        }
    }

    boolean isAnyClientAlive() {
        if(mDeviceIntegrationDisabled) {
            return false;
        }
        return mCrossDeviceServiceDelegate.isAnyClientAliveInService();
    }

    private boolean isHomeIntent(Intent intent) {
        return ACTION_MAIN.equals(intent.getAction())
                && (intent.hasCategory(CATEGORY_HOME)
                || intent.hasCategory(CATEGORY_SECONDARY_HOME))
                && intent.getCategories().size() == 1
                && intent.getData() == null
                && intent.getType() == null;
    }

    /**
     * try to remove task with id = taskId
     *
     * @param taskId id for task
     * @Caller RemoteTaskManager - When some user action trigger task close(such as back/exit
     * button on the last activity or home button, handler manager
     * will call this function to check if the task is handled by
     * remote task manager if it is , close it.
     */
    void closeRemoteTask(int taskId) {
        if(!isAnyClientAlive()) {
            return;
        }

        Task task = anyTaskForId(taskId);
        if(anyTaskExist(task)) {
            /*
             * Notify aosp task manager service to close and remove this task really, task remove
             * action must be processed in aosp main tread to avoid dead lock.
             */
            mRemoteTaskHandler.post(() -> mActivityTaskManagerService.removeTask(taskId));
        }
    }

    /**
     * Notify task was closed by task id
     * This API is only called when PL want to close a task that
     * didn't exist, and just notify PL that the task already closed before
     * and PL can release the task container safty.
     *
     * @param taskId id for task
     */
    void notifyRemoteTaskClosed(int taskId) {
        mRemoteTaskHandler.post(() -> {
            mCrossDeviceServiceDelegate.handleCloseRemoteTask(taskId);
        });
    }

    /**
     * Notify correspond task was closed
     *
     * @param taskId id for task
     * @Caller 1. RemoteTaskManager - If app call Activity.finish on the last
     * activity of the task, the task will close itself, if the task is handled by remote
     * task handler manager, need to notify the handler that the task
     * already closed.
     * 2. ActivityStackSupervisor - If task was close and removed by task manager service and the
     * task is handled by remote task handler manger, need to notify
     * the handler that the task already closed.
     */
    void notifyRemoteTaskClosed(Task task) {
        task.mAllowReparent = false;
        mCrossDeviceServiceDelegate.handleCloseRemoteTask(task.mTaskId);
    }

    boolean anyTaskExist(Task task) {
        if (task == null) {
            return false;
        }
        return task.mLaunchScenario >= FLAG_TASK_LAUNCH_SCENARIO_CLIENT;
    }

    Task anyTaskForId(int taskId) {
        return mActivityTaskManagerService.mRootWindowContainer.anyTaskForId(taskId);
    }

    boolean findTaskOnlyForLaunch(Intent intent, String affinity, int taskId) {
        if(!isAnyClientAlive()) {
            return false;
        }
        Task task = mActivityTaskManagerService.mRootWindowContainer.anyTaskForId(taskId);
        if (task == null || task.mLaunchScenario < FLAG_TASK_LAUNCH_SCENARIO_CLIENT || intent == null) {
            return false;
        }

        return isLaunchIntent(intent)
                && TextUtils.equals(affinity, task.affinity);
    }

    /**
     * This should only call in recent use task scenarios, more specifically, when
     * click an app in recent use task, but this app already launch in VD, then we should
     * call this method to switch app from VD to MD.
     *
     * @param task   Task which activate from Recent use tasks panel
     * @param intent Related intent
     * @return true if device system sdk already handle this, otherwise false.
     */
    boolean interceptFromRecents(Task task, Intent intent) {
        if (!isAnyClientAlive() || task == null ||
                task.getDisplayId() != Display.DEFAULT_DISPLAY ||
                task.mPrevDisplayId <= Display.DEFAULT_DISPLAY) {
            return false;
        }

        RemoteTaskLogger.d(TAG, "interceptFromRecents");
        task.mLaunchScenario = FLAG_TASK_LAUNCH_SCENARIO_VDTOMD;
        mCrossDeviceServiceDelegate.notifyDisplaySwitched(task.mPrevDisplayId);
        return true;
    }

    /**
     * While task was removed, we need to guarantee correspond remote handler should be
     * notified and removed with correct reason.
     *
     * @param task   the task which was removed.
     */
    void handleRemoveTask(Task task) {
        if (task.mLaunchScenario < FLAG_TASK_LAUNCH_SCENARIO_CLIENT || !isAnyClientAlive()) {
            return;
        }

        notifyRemoteTaskClosed(task);
    }

    /**
     * While trigger launching app, some apps will start an no-window activity and immediately
     * finish this activity,for this situation, we need to guarantee this Task should be removed.
     *
     * @param task the task where activity finish.
     */
    void handleFinishActivity(Task task, ActivityRecord finishingRecord) {
        if (!isAnyClientAlive() || task.mLaunchScenario < FLAG_TASK_LAUNCH_SCENARIO_CLIENT ||
                !task.isRootTask() || task.getDisplayId() <= Display.DEFAULT_DISPLAY) {
            return;
        }
        if (!finishingRecord.isRootOfTask()) {
            return;
        }
        if (task.getChildCount() > 1 && task.getTopNonFinishingActivity() != null) {
            return;
        }
        closeRemoteTask(task.mTaskId);
        RemoteTaskLogger.d(TAG, "handleFinishActivity start");
    }

    /**
     * Query prefer display area
     *
     * @param defaultArea  TaskDisplayArea
     * @param intent       Intent
     * @param sourceRecord ActivityRecord
     * @param record       ActivityRecord
     * @param options      ActivityOptions
     * @return prefer area
     */
    TaskDisplayArea queryPreferredDisplayArea(@Nullable Task task, TaskDisplayArea defaultArea, Intent intent,
                                              ActivityRecord sourceRecord, ActivityRecord record,
                                              ActivityOptions options) {
        if (!isAnyClientAlive() || options == null || options.getRemoteTaskLaunchScenario() == FLAG_TASK_LAUNCH_SCENARIO_COMMON) {
            return defaultArea;
        }

        //For this case, we don't handle, let it goes with default flow
        if (options != null) {
            if (options.getRemoteTaskFlag() == RemoteTaskConstants.REMOTE_TASK_FLAG_LAUNCH) {
                return defaultArea;
            } else if (options.getRemoteTaskLaunchScenario() == FLAG_TASK_LAUNCH_SCENARIO_DUP
                    && task != null) {
                return task.getDisplayArea();
            }
        }
        for (BasePreferredTaskDisplayQualifier handler : mDisplayQualifierList) {
            TaskDisplayArea area = handler.queryPreferredDisplay(defaultArea, intent,
                    sourceRecord, record, options);
            if (area != null) {
                return area;
            }
        }
        return defaultArea;
    }

    /**
     * Every document mode with {@link Intent#FLAG_ACTIVITY_MULTIPLE_TASK}  will open an new task
     * for launching Activity, but on launch same app scenarios, we should make sure if users want
     * to move task between VD and MD, if so, we should intercept original logic and find an
     * appropriate task from system.
     *
     * @param startActivity ActivityRecord
     * @param options       ActivityOptions
     * @param area          TaskDisplayArea
     * @param launchFlags   int
     * @return existing ActivityRecord on system.
     */
    @Nullable
    ActivityRecord findTaskForReuseIfNeeded(ActivityRecord startActivity, ActivityOptions options,
                                            TaskDisplayArea area, int launchFlags) {
        if (!isAnyClientAlive()) {
            return null;
        }
        if ((launchFlags & Intent.FLAG_ACTIVITY_NEW_DOCUMENT) != 0
                && (launchFlags & FLAG_ACTIVITY_MULTIPLE_TASK) != 0) {
            ActivityRecord result = mRootWindowContainer.findTask(startActivity, area);
            if (result == null) {
                return null;
            }
            Task task = result.getTask();
            if (task == null) {
                return null;
            }
            if (task.getDisplayId() <= Display.DEFAULT_DISPLAY) {
                //Check MD2VD scenario
                if (options != null) {
                    if (!TextUtils.isEmpty(options.getRemoteUuid())
                            && options.getRemoteTaskFlag() == REMOTE_TASK_FLAG_LAUNCH) {
                        return result;
                    }
                }
            } else {
                //Check VD2MD scenario
                if (anyTaskExist(task)) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * Find the top ActivityRecord from WindowProcessController
     *
     * @param thread IApplicationThread
     * @return ActivityRecord if we find one.
     */
    ActivityRecord getTopActivityFromController(IApplicationThread thread) {
        if (thread == null) {
            return null;
        }
        if (mActivityTaskManagerService != null) {
            WindowProcessController controller =
                    mActivityTaskManagerService.getProcessController(thread);
            if (controller != null) {
                return controller.getTopActivity();
            }
        }
        return null;
    }

    /**
     * If is in display switch session
     *
     * @return true if yes
     */
    boolean isDisplaySwitchDetected(ActivityOptions options) {
        if (options == null || !isAnyClientAlive()) {
            return false;
        }
        int scenario = options.getRemoteTaskLaunchScenario();
        return scenario == FLAG_TASK_LAUNCH_SCENARIO_MDTOVD ||
                scenario == FLAG_TASK_LAUNCH_SCENARIO_VDTOMD;
    }

    /**
     * Get TaskDisplayArea depends on target displayId
     *
     * @return TaskDisplayArea
     */
    TaskDisplayArea getFinalPreferredTaskDisplayArea( IApplicationThread thread, int callingPid,
                                                      int callingUid, int realCallingPid,
                                                      int realCallingUid, ActivityRecord sourceRecord,
                                                      ActivityOptions options) {
        if (!isAnyClientAlive() || options == null || options.getRemoteTaskLaunchScenario() == FLAG_TASK_LAUNCH_SCENARIO_COMMON) {
            return null;
        }
        ActivityTaskManagerService service = mActivityTaskManagerService;
        int targetId = Display.INVALID_DISPLAY;
        if (options != null) {
            targetId = options.getLaunchDisplayId();
        }
        if (targetId == Display.INVALID_DISPLAY) {
            ActivityRecord callerRecord = getCallerRecord(thread, sourceRecord, callingPid,
                    callingUid, realCallingPid, realCallingUid);
            if (callerRecord != null) {
                targetId = callerRecord.getDisplayId();
            }
        }

        if (service == null) {
            return null;
        }
        if (service.mRootWindowContainer.getDisplayContent(targetId) != null) {
            return service.mRootWindowContainer
                    .getDisplayContent(targetId).getDefaultTaskDisplayArea();
        }
        return null;
    }

    /**
     * @param options Activity Launch Options
     * @return true if we have session needs to handle
     */
    boolean inAnyInterceptSession(ActivityOptions options) {
        if (!isAnyClientAlive()) {
            return false;
        }

        if (options != null) {
            int scenario = options.getRemoteTaskLaunchScenario();
            return scenario == FLAG_TASK_LAUNCH_SCENARIO_DUP ||
                    scenario == FLAG_TASK_LAUNCH_SCENARIO_MDTOVD ||
                    scenario == FLAG_TASK_LAUNCH_SCENARIO_VDTOMD;
        }
        return false;
    }

    /**
     * Clean all of caches from manager.
     */
    void recycleAll() {
    }

    /**
     * Claiming to listen process die event, if it's corresponding to task show in VD,
     * try to remove handler
     *
     * @param wpc WindowProcessController
     * @param reason reason of process die
     */
    public void handleProcessDied(WindowProcessController wpc, String reason) {
        if (!isAnyClientAlive() || TextUtils.equals(reason, REASON_PROCESS_RESTART)) {
            return;
        }

        List<RemoteTaskInfo> taskInfoList = getRemoteTaskInfoList();
        for (RemoteTaskInfo taskInfo : taskInfoList) {
            if (taskInfo != null) {
                Task task = mActivityTaskManagerService.mRootWindowContainer.anyTaskForId(taskInfo.getTaskId());
                if (task != null && task.getRemoteTaskPid() == wpc.getRemoteTaskPid()) {
                    notifyRemoteTaskClosed(task);
                }
            }
        }
    }

    /**
     * If it's launch intent
     *
     * @param intent Intent
     * @return true if it is.
     */
    static boolean isLaunchIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        if (TextUtils.isEmpty(intent.getAction())) {
            return false;
        }
        if (TextUtils.equals(intent.getAction(), Intent.ACTION_MAIN)) {
            return intent.getCategories() != null
                    && intent.getCategories().contains(Intent.CATEGORY_LAUNCHER);
        }
        return false;
    }

    /**
     * Attempt to ignore relaunch workflow when we are in display switch session
     *
     * @param displayChanged    flag
     * @param previousDisplayId int
     * @param currentDisplayId  int
     * @param changes           int
     * @return true if so.
     */
    @SuppressLint("NewApi")
    boolean shouldIgnoreRelaunch(Task task, boolean displayChanged,
                                 int previousDisplayId, int currentDisplayId, int changes) {
        if (!isAnyClientAlive()) {
            return false;
        }
        if (displayChanged) {
            if (task.mLaunchScenario == FLAG_TASK_LAUNCH_SCENARIO_VDTOMD ||
                    task.mLaunchScenario == FLAG_TASK_LAUNCH_SCENARIO_MDTOVD) {
                return true;
            }
        } else {
            //Fix bug that game app PUGB cannot launch on VD.
            if ((changes & ActivityInfo.CONFIG_SCREEN_LAYOUT) != 0) {
                return anyTaskExist(task) && previousDisplayId == currentDisplayId;
            }
        }
        return false;
    }

    /**
     * When system find that it's potential new task launch, we should filter
     * Single Top mode while checking whether should start a new remote task session.
     *
     * @param area          TaskDisplayArea
     * @param startActivity ActivityRecord
     * @param notTop        ActivityRecord
     * @param launchFlags   int
     * @param launchMode    int
     * @return true if need to deliver Intent to current top activity
     */
    boolean isDeliverToCurrentTop(TaskDisplayArea area, ActivityRecord startActivity,
                                  ActivityRecord notTop, int launchFlags, int launchMode) {
        if (area == null || startActivity == null) {
            return false;
        }
        Task topRootTask =
                mActivityTaskManagerService.mRootWindowContainer.getTopDisplayFocusedRootTask();
        if (topRootTask == null) {
            return false;
        }
        final ActivityRecord top = topRootTask.topRunningNonDelayedActivityLocked(notTop);
        final boolean dontStart = top != null
                && top.mActivityComponent.equals(startActivity.mActivityComponent)
                && top.mUserId == startActivity.mUserId
                && top.attachedToProcess()
                && ((launchFlags & FLAG_ACTIVITY_SINGLE_TOP) != 0
                || LAUNCH_SINGLE_TOP == launchMode)
                // This allows home activity to automatically launch on secondary task display area
                // when it was added, if home was the top activity on default task display area,
                // instead of sending new intent to the home activity on default display area.
                && (!top.isActivityTypeHome() || top.getDisplayArea() == area);
        return dontStart;
    }

    /**
     * Check white list for user id
     * @param uid caller user id that need to be check from white list
     * @return true if call user id is in white list, false otherwise
     */
    boolean isFromBackgroundWhiteList(int uid) {
        if (!isAnyClientAlive()) {
            return false;
        }
        return mCrossDeviceServiceDelegate.isFromBackgroundWhiteList(uid);
    }
}
