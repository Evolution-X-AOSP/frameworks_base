/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.server.am;

import static android.app.ActivityManager.PROCESS_STATE_BOUND_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_ACTIVITY;
import static android.app.ActivityManager.PROCESS_STATE_CACHED_EMPTY;
import static android.app.ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_BACKGROUND;
import static android.app.ActivityManager.PROCESS_STATE_IMPORTANT_FOREGROUND;
import static android.app.ActivityManager.PROCESS_STATE_LAST_ACTIVITY;
import static android.app.ActivityManager.PROCESS_STATE_NONEXISTENT;
import static android.app.ActivityManager.PROCESS_STATE_RECEIVER;
import static android.app.ActivityManager.PROCESS_STATE_SERVICE;
import static android.app.ActivityManager.PROCESS_STATE_TOP;
import static android.util.DebugUtils.valueToString;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.android.server.am.ActivityManagerInternalTest.CustomThread;
import static com.android.server.am.ActivityManagerService.DISPATCH_UIDS_CHANGED_UI_MSG;
import static com.android.server.am.ActivityManagerService.Injector;
import static com.android.server.am.ActivityManagerService.NETWORK_STATE_BLOCK;
import static com.android.server.am.ActivityManagerService.NETWORK_STATE_NO_CHANGE;
import static com.android.server.am.ActivityManagerService.NETWORK_STATE_UNBLOCK;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.IApplicationThread;
import android.app.IUidObserver;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import com.android.server.appop.AppOpsService;
import com.android.server.am.ProcessList.IsolatedUidRange;
import com.android.server.am.ProcessList.IsolatedUidRangeAllocator;
import com.android.server.wm.ActivityTaskManagerService;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Test class for {@link ActivityManagerService}.
 *
 * Build/Install/Run:
 *  atest FrameworksServicesTests:ActivityManagerServiceTest
 */
@SmallTest
@FlakyTest(bugId = 113616538)
public class ActivityManagerServiceTest {
    private static final String TAG = ActivityManagerServiceTest.class.getSimpleName();

    private static final int TEST_UID = 11111;

    private static final long TEST_PROC_STATE_SEQ1 = 555;
    private static final long TEST_PROC_STATE_SEQ2 = 556;

    private static final int[] UID_RECORD_CHANGES = {
        UidRecord.CHANGE_PROCSTATE,
        UidRecord.CHANGE_GONE,
        UidRecord.CHANGE_GONE | UidRecord.CHANGE_IDLE,
        UidRecord.CHANGE_IDLE,
        UidRecord.CHANGE_ACTIVE
    };

    private Context mContext = getInstrumentation().getTargetContext();
    @Mock private AppOpsService mAppOpsService;
    @Mock private PackageManager mPackageManager;

    private TestInjector mInjector;
    private ActivityManagerService mAms;
    private HandlerThread mHandlerThread;
    private TestHandler mHandler;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new TestHandler(mHandlerThread.getLooper());
        mInjector = new TestInjector();
        mAms = new ActivityManagerService(mInjector);
        mAms.mWaitForNetworkTimeoutMs = 2000;
        mAms.mActivityTaskManager = new ActivityTaskManagerService(mContext);
        mAms.mActivityTaskManager.initialize(null, null, mHandler.getLooper());
    }

    @After
    public void tearDown() {
        mHandlerThread.quit();
    }

    @SuppressWarnings("GuardedBy")
    @MediumTest
    @Test
    public void incrementProcStateSeqAndNotifyAppsLocked() throws Exception {

        final UidRecord uidRec = addUidRecord(TEST_UID);
        addUidRecord(TEST_UID + 1);

        // Uid state is not moving from background to foreground or vice versa.
        verifySeqCounterAndInteractions(uidRec,
                PROCESS_STATE_TOP, // prevState
                PROCESS_STATE_TOP, // curState
                0, // expectedGlobalCounter
                0, // exptectedCurProcStateSeq
                NETWORK_STATE_NO_CHANGE, // expectedBlockState
                false); // expectNotify

        // Uid state is moving from foreground to background.
        verifySeqCounterAndInteractions(uidRec,
                PROCESS_STATE_FOREGROUND_SERVICE, // prevState
                PROCESS_STATE_SERVICE, // curState
                1, // expectedGlobalCounter
                1, // exptectedCurProcStateSeq
                NETWORK_STATE_UNBLOCK, // expectedBlockState
                true); // expectNotify

        // Explicitly setting the seq counter for more verification.
        // @SuppressWarnings("GuardedBy")
        mAms.mProcessList.mProcStateSeqCounter = 42;

        // Uid state is not moving from background to foreground or vice versa.
        verifySeqCounterAndInteractions(uidRec,
                PROCESS_STATE_IMPORTANT_BACKGROUND, // prevState
                PROCESS_STATE_IMPORTANT_FOREGROUND, // curState
                42, // expectedGlobalCounter
                1, // exptectedCurProcStateSeq
                NETWORK_STATE_NO_CHANGE, // expectedBlockState
                false); // expectNotify

        // Uid state is moving from background to foreground.
        verifySeqCounterAndInteractions(uidRec,
                PROCESS_STATE_LAST_ACTIVITY, // prevState
                PROCESS_STATE_TOP, // curState
                43, // expectedGlobalCounter
                43, // exptectedCurProcStateSeq
                NETWORK_STATE_BLOCK, // expectedBlockState
                false); // expectNotify

        // verify waiting threads are not notified.
        uidRec.waitingForNetwork = false;
        // Uid state is moving from foreground to background.
        verifySeqCounterAndInteractions(uidRec,
                PROCESS_STATE_FOREGROUND_SERVICE, // prevState
                PROCESS_STATE_SERVICE, // curState
                44, // expectedGlobalCounter
                44, // exptectedCurProcStateSeq
                NETWORK_STATE_UNBLOCK, // expectedBlockState
                false); // expectNotify

        // Verify when uid is not restricted, procStateSeq is not incremented.
        uidRec.waitingForNetwork = true;
        mInjector.setNetworkRestrictedForUid(false);
        verifySeqCounterAndInteractions(uidRec,
                PROCESS_STATE_IMPORTANT_BACKGROUND, // prevState
                PROCESS_STATE_TOP, // curState
                44, // expectedGlobalCounter
                44, // exptectedCurProcStateSeq
                -1, // expectedBlockState, -1 to verify there are no interactions with main thread.
                false); // expectNotify

        // Verify when waitForNetworkTimeout is 0, then procStateSeq is not incremented.
        mAms.mWaitForNetworkTimeoutMs = 0;
        mInjector.setNetworkRestrictedForUid(true);
        verifySeqCounterAndInteractions(uidRec,
                PROCESS_STATE_TOP, // prevState
                PROCESS_STATE_IMPORTANT_BACKGROUND, // curState
                44, // expectedGlobalCounter
                44, // exptectedCurProcStateSeq
                -1, // expectedBlockState, -1 to verify there are no interactions with main thread.
                false); // expectNotify

        // Verify when the uid doesn't have internet permission, then procStateSeq is not
        // incremented.
        uidRec.hasInternetPermission = false;
        mAms.mWaitForNetworkTimeoutMs = 111;
        mInjector.setNetworkRestrictedForUid(true);
        verifySeqCounterAndInteractions(uidRec,
                PROCESS_STATE_CACHED_ACTIVITY, // prevState
                PROCESS_STATE_FOREGROUND_SERVICE, // curState
                44, // expectedGlobalCounter
                44, // exptectedCurProcStateSeq
                -1, // expectedBlockState, -1 to verify there are no interactions with main thread.
                false); // expectNotify

        // Verify procStateSeq is not incremented when the uid is not an application, regardless
        // of the process state.
        final int notAppUid = 111;
        final UidRecord uidRec2 = addUidRecord(notAppUid);
        verifySeqCounterAndInteractions(uidRec2,
                PROCESS_STATE_CACHED_EMPTY, // prevState
                PROCESS_STATE_TOP, // curState
                44, // expectedGlobalCounter
                0, // exptectedCurProcStateSeq
                -1, // expectedBlockState, -1 to verify there are no interactions with main thread.
                false); // expectNotify
    }

    private UidRecord addUidRecord(int uid) {
        final UidRecord uidRec = new UidRecord(uid);
        uidRec.waitingForNetwork = true;
        uidRec.hasInternetPermission = true;
        mAms.mProcessList.mActiveUids.put(uid, uidRec);

        final ProcessRecord appRec = new ProcessRecord(mAms, new ApplicationInfo(), TAG, uid);
        appRec.thread = Mockito.mock(IApplicationThread.class);
        mAms.mProcessList.mLruProcesses.add(appRec);

        return uidRec;
    }

    @SuppressWarnings("GuardedBy")
    private void verifySeqCounterAndInteractions(UidRecord uidRec, int prevState, int curState,
            int expectedGlobalCounter, int expectedCurProcStateSeq, int expectedBlockState,
            boolean expectNotify) throws Exception {
        CustomThread thread = new CustomThread(uidRec.networkStateLock);
        thread.startAndWait("Unexpected state for " + uidRec);

        uidRec.setProcState = prevState;
        uidRec.setCurProcState(curState);
        mAms.incrementProcStateSeqAndNotifyAppsLocked();

        // @SuppressWarnings("GuardedBy")
        assertEquals(expectedGlobalCounter, mAms.mProcessList.mProcStateSeqCounter);
        assertEquals(expectedCurProcStateSeq, uidRec.curProcStateSeq);

        for (int i = mAms.mProcessList.getLruSizeLocked() - 1; i >= 0; --i) {
            final ProcessRecord app = mAms.mProcessList.mLruProcesses.get(i);
            // AMS should notify apps only for block states other than NETWORK_STATE_NO_CHANGE.
            if (app.uid == uidRec.uid && expectedBlockState == NETWORK_STATE_BLOCK) {
                verify(app.thread).setNetworkBlockSeq(uidRec.curProcStateSeq);
            } else {
                verifyZeroInteractions(app.thread);
            }
            Mockito.reset(app.thread);
        }

        if (expectNotify) {
            thread.assertTerminated("Unexpected state for " + uidRec);
        } else {
            thread.assertWaiting("Unexpected state for " + uidRec);
            thread.interrupt();
        }
    }

    private void validateAppZygoteIsolatedUidRange(IsolatedUidRange uidRange) {
        assertNotNull(uidRange);
        assertTrue(uidRange.mFirstUid >= Process.FIRST_APP_ZYGOTE_ISOLATED_UID
                && uidRange.mFirstUid <= Process.LAST_APP_ZYGOTE_ISOLATED_UID);
        assertTrue(uidRange.mLastUid >= Process.FIRST_APP_ZYGOTE_ISOLATED_UID
                && uidRange.mLastUid <= Process.LAST_APP_ZYGOTE_ISOLATED_UID);
        assertTrue(uidRange.mLastUid > uidRange.mFirstUid
                && ((uidRange.mLastUid - uidRange.mFirstUid + 1)
                     == Process.NUM_UIDS_PER_APP_ZYGOTE));
    }

    private void verifyUidRangesNoOverlap(IsolatedUidRange uidRange1, IsolatedUidRange uidRange2) {
        IsolatedUidRange lowRange = uidRange1.mFirstUid <= uidRange2.mFirstUid ? uidRange1 : uidRange2;
        IsolatedUidRange highRange = lowRange == uidRange1  ? uidRange2 : uidRange1;

        assertTrue(highRange.mFirstUid > lowRange.mLastUid);
    }

    @Test
    public void testIsolatedUidRangeAllocator() {
        final IsolatedUidRangeAllocator allocator = mAms.mProcessList.mAppIsolatedUidRangeAllocator;

        // Create initial range
        ApplicationInfo appInfo = new ApplicationInfo();
        appInfo.processName = "com.android.test.app";
        appInfo.uid = 10000;
        final IsolatedUidRange range = allocator.getOrCreateIsolatedUidRangeLocked(
                appInfo.processName, appInfo.uid);
        validateAppZygoteIsolatedUidRange(range);
        verifyIsolatedUidAllocator(range);

        // Create a second range
        ApplicationInfo appInfo2 = new ApplicationInfo();
        appInfo2.processName = "com.android.test.app2";
        appInfo2.uid = 10001;
        IsolatedUidRange range2 = allocator.getOrCreateIsolatedUidRangeLocked(
                appInfo2.processName, appInfo2.uid);
        validateAppZygoteIsolatedUidRange(range2);
        verifyIsolatedUidAllocator(range2);

        // Verify ranges don't overlap
        verifyUidRangesNoOverlap(range, range2);

        // Free range, reallocate and verify
        allocator.freeUidRangeLocked(appInfo2);
        range2 = allocator.getOrCreateIsolatedUidRangeLocked(appInfo2.processName, appInfo2.uid);
        validateAppZygoteIsolatedUidRange(range2);
        verifyUidRangesNoOverlap(range, range2);
        verifyIsolatedUidAllocator(range2);

        // Free both
        allocator.freeUidRangeLocked(appInfo);
        allocator.freeUidRangeLocked(appInfo2);

        // Verify for a secondary user
        ApplicationInfo appInfo3 = new ApplicationInfo();
        appInfo3.processName = "com.android.test.app";
        appInfo3.uid = 1010000;
        final IsolatedUidRange range3 = allocator.getOrCreateIsolatedUidRangeLocked(
                appInfo3.processName, appInfo3.uid);
        validateAppZygoteIsolatedUidRange(range3);
        verifyIsolatedUidAllocator(range3);

        allocator.freeUidRangeLocked(appInfo3);
        // Try to allocate the maximum number of UID ranges
        int maxNumUidRanges = (Process.LAST_APP_ZYGOTE_ISOLATED_UID
                - Process.FIRST_APP_ZYGOTE_ISOLATED_UID + 1) / Process.NUM_UIDS_PER_APP_ZYGOTE;
        for (int i = 0; i < maxNumUidRanges; i++) {
            appInfo = new ApplicationInfo();
            appInfo.uid = 10000 + i;
            appInfo.processName = "com.android.test.app" + Integer.toString(i);
            IsolatedUidRange uidRange = allocator.getOrCreateIsolatedUidRangeLocked(
                    appInfo.processName, appInfo.uid);
            validateAppZygoteIsolatedUidRange(uidRange);
            verifyIsolatedUidAllocator(uidRange);
        }

        // Try to allocate another one and make sure it fails
        appInfo = new ApplicationInfo();
        appInfo.uid = 9000;
        appInfo.processName = "com.android.test.app.failed";
        IsolatedUidRange failedRange = allocator.getOrCreateIsolatedUidRangeLocked(
                appInfo.processName, appInfo.uid);

        assertNull(failedRange);
    }

    public void verifyIsolatedUid(ProcessList.IsolatedUidRange range, int uid) {
        assertTrue(uid >= range.mFirstUid && uid <= range.mLastUid);
    }

    public void verifyIsolatedUidAllocator(ProcessList.IsolatedUidRange range) {
        int uid = range.allocateIsolatedUidLocked(0);
        verifyIsolatedUid(range, uid);

        int uid2 = range.allocateIsolatedUidLocked(0);
        verifyIsolatedUid(range, uid2);
        assertTrue(uid2 != uid);

        // Free both
        range.freeIsolatedUidLocked(uid);
        range.freeIsolatedUidLocked(uid2);

        // Allocate the entire range
        for (int i = 0; i < (range.mLastUid - range.mFirstUid + 1); ++i) {
            uid = range.allocateIsolatedUidLocked(0);
            verifyIsolatedUid(range, uid);
        }

        // Ensure the next one fails
        uid = range.allocateIsolatedUidLocked(0);
        assertEquals(uid, -1);
    }

    @Test
    public void testGlobalIsolatedUidAllocator() {
        final IsolatedUidRange globalUidRange = mAms.mProcessList.mGlobalIsolatedUids;
        assertEquals(globalUidRange.mFirstUid, Process.FIRST_ISOLATED_UID);
        assertEquals(globalUidRange.mLastUid, Process.LAST_ISOLATED_UID);
        verifyIsolatedUidAllocator(globalUidRange);
    }

    @Test
    public void testBlockStateForUid() {
        final UidRecord uidRec = new UidRecord(TEST_UID);
        int expectedBlockState;

        final String errorTemplate = "Block state should be %s, prevState: %s, curState: %s";
        Function<Integer, String> errorMsg = (blockState) -> {
            return String.format(errorTemplate,
                    valueToString(ActivityManagerService.class, "NETWORK_STATE_", blockState),
                    valueToString(ActivityManager.class, "PROCESS_STATE_", uidRec.setProcState),
                    valueToString(ActivityManager.class, "PROCESS_STATE_", uidRec.getCurProcState())
            );
        };

        // No change in uid state
        uidRec.setProcState = PROCESS_STATE_RECEIVER;
        uidRec.setCurProcState(PROCESS_STATE_RECEIVER);
        expectedBlockState = NETWORK_STATE_NO_CHANGE;
        assertEquals(errorMsg.apply(expectedBlockState),
                expectedBlockState, mAms.getBlockStateForUid(uidRec));

        // Foreground to foreground
        uidRec.setProcState = PROCESS_STATE_FOREGROUND_SERVICE;
        uidRec.setCurProcState(PROCESS_STATE_BOUND_FOREGROUND_SERVICE);
        expectedBlockState = NETWORK_STATE_NO_CHANGE;
        assertEquals(errorMsg.apply(expectedBlockState),
                expectedBlockState, mAms.getBlockStateForUid(uidRec));

        // Background to background
        uidRec.setProcState = PROCESS_STATE_CACHED_ACTIVITY;
        uidRec.setCurProcState(PROCESS_STATE_CACHED_EMPTY);
        expectedBlockState = NETWORK_STATE_NO_CHANGE;
        assertEquals(errorMsg.apply(expectedBlockState),
                expectedBlockState, mAms.getBlockStateForUid(uidRec));

        // Background to background
        uidRec.setProcState = PROCESS_STATE_NONEXISTENT;
        uidRec.setCurProcState(PROCESS_STATE_CACHED_ACTIVITY);
        expectedBlockState = NETWORK_STATE_NO_CHANGE;
        assertEquals(errorMsg.apply(expectedBlockState),
                expectedBlockState, mAms.getBlockStateForUid(uidRec));

        // Background to foreground
        uidRec.setProcState = PROCESS_STATE_SERVICE;
        uidRec.setCurProcState(PROCESS_STATE_FOREGROUND_SERVICE);
        expectedBlockState = NETWORK_STATE_BLOCK;
        assertEquals(errorMsg.apply(expectedBlockState),
                expectedBlockState, mAms.getBlockStateForUid(uidRec));

        // Foreground to background
        uidRec.setProcState = PROCESS_STATE_TOP;
        uidRec.setCurProcState(PROCESS_STATE_LAST_ACTIVITY);
        expectedBlockState = NETWORK_STATE_UNBLOCK;
        assertEquals(errorMsg.apply(expectedBlockState),
                expectedBlockState, mAms.getBlockStateForUid(uidRec));
    }

    /**
     * This test verifies that process state changes are dispatched to observers based on the
     * changes they wanted to listen (this is specified when registering the observer).
     */
    @Test
    public void testDispatchUids_dispatchNeededChanges() throws RemoteException {
        when(mAppOpsService.noteOperation(AppOpsManager.OP_GET_USAGE_STATS, Process.myUid(), null))
                .thenReturn(AppOpsManager.MODE_ALLOWED);

        final int[] changesToObserve = {
            ActivityManager.UID_OBSERVER_PROCSTATE,
            ActivityManager.UID_OBSERVER_GONE,
            ActivityManager.UID_OBSERVER_IDLE,
            ActivityManager.UID_OBSERVER_ACTIVE,
            ActivityManager.UID_OBSERVER_PROCSTATE | ActivityManager.UID_OBSERVER_GONE
                    | ActivityManager.UID_OBSERVER_ACTIVE | ActivityManager.UID_OBSERVER_IDLE
        };
        final IUidObserver[] observers = new IUidObserver.Stub[changesToObserve.length];
        for (int i = 0; i < observers.length; ++i) {
            observers[i] = Mockito.mock(IUidObserver.Stub.class);
            when(observers[i].asBinder()).thenReturn((IBinder) observers[i]);
            mAms.registerUidObserver(observers[i], changesToObserve[i] /* which */,
                    ActivityManager.PROCESS_STATE_UNKNOWN /* cutpoint */, null /* caller */);

            // When we invoke AMS.registerUidObserver, there are some interactions with observers[i]
            // mock in RemoteCallbackList class. We don't want to test those interactions and
            // at the same time, we don't want those to interfere with verifyNoMoreInteractions.
            // So, resetting the mock here.
            Mockito.reset(observers[i]);
        }

        // Add pending uid records each corresponding to a different change type UidRecord.CHANGE_*
        final int[] changesForPendingUidRecords = UID_RECORD_CHANGES;

        final int[] procStatesForPendingUidRecords = {
            ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE,
            ActivityManager.PROCESS_STATE_NONEXISTENT,
            ActivityManager.PROCESS_STATE_CACHED_EMPTY,
            ActivityManager.PROCESS_STATE_CACHED_ACTIVITY,
            ActivityManager.PROCESS_STATE_TOP
        };
        final Map<Integer, UidRecord.ChangeItem> changeItems = new HashMap<>();
        for (int i = 0; i < changesForPendingUidRecords.length; ++i) {
            final UidRecord.ChangeItem pendingChange = new UidRecord.ChangeItem();
            pendingChange.change = changesForPendingUidRecords[i];
            pendingChange.uid = i;
            pendingChange.processState = procStatesForPendingUidRecords[i];
            pendingChange.procStateSeq = i;
            changeItems.put(changesForPendingUidRecords[i], pendingChange);
            mAms.mPendingUidChanges.add(pendingChange);
        }

        mAms.dispatchUidsChanged();
        // Verify the required changes have been dispatched to observers.
        for (int i = 0; i < observers.length; ++i) {
            final int changeToObserve = changesToObserve[i];
            final IUidObserver observerToTest = observers[i];
            if ((changeToObserve & ActivityManager.UID_OBSERVER_IDLE) != 0) {
                // Observer listens to uid idle changes, so change items corresponding to
                // UidRecord.CHANGE_IDLE or UidRecord.CHANGE_IDLE_GONE needs to be
                // delivered to this observer.
                final int[] changesToVerify = {
                    UidRecord.CHANGE_IDLE,
                    UidRecord.CHANGE_GONE | UidRecord.CHANGE_IDLE
                };
                verifyObserverReceivedChanges(observerToTest, changesToVerify, changeItems,
                        (observer, changeItem) -> {
                            verify(observer).onUidIdle(changeItem.uid, changeItem.ephemeral);
                        });
            }
            if ((changeToObserve & ActivityManager.UID_OBSERVER_ACTIVE) != 0) {
                // Observer listens to uid active changes, so change items corresponding to
                // UidRecord.CHANGE_ACTIVE needs to be delivered to this observer.
                final int[] changesToVerify = { UidRecord.CHANGE_ACTIVE };
                verifyObserverReceivedChanges(observerToTest, changesToVerify, changeItems,
                        (observer, changeItem) -> {
                            verify(observer).onUidActive(changeItem.uid);
                        });
            }
            if ((changeToObserve & ActivityManager.UID_OBSERVER_GONE) != 0) {
                // Observer listens to uid gone changes, so change items corresponding to
                // UidRecord.CHANGE_GONE or UidRecord.CHANGE_IDLE_GONE needs to be
                // delivered to this observer.
                final int[] changesToVerify = {
                        UidRecord.CHANGE_GONE,
                        UidRecord.CHANGE_GONE | UidRecord.CHANGE_IDLE
                };
                verifyObserverReceivedChanges(observerToTest, changesToVerify, changeItems,
                        (observer, changeItem) -> {
                            verify(observer).onUidGone(changeItem.uid, changeItem.ephemeral);
                        });
            }
            if ((changeToObserve & ActivityManager.UID_OBSERVER_PROCSTATE) != 0) {
                // Observer listens to uid procState changes, so change items corresponding to
                // UidRecord.CHANGE_PROCSTATE or UidRecord.CHANGE_IDLE or UidRecord.CHANGE_ACTIVE
                // needs to be delivered to this observer.
                final int[] changesToVerify = {
                        UidRecord.CHANGE_PROCSTATE,
                        UidRecord.CHANGE_ACTIVE,
                        UidRecord.CHANGE_IDLE
                };
                verifyObserverReceivedChanges(observerToTest, changesToVerify, changeItems,
                        (observer, changeItem) -> {
                            verify(observer).onUidStateChanged(changeItem.uid,
                                    changeItem.processState, changeItem.procStateSeq);
                        });
            }
            // Verify there are no other callbacks for this observer.
            verifyNoMoreInteractions(observerToTest);
        }
    }

    private interface ObserverChangesVerifier {
        void verify(IUidObserver observer, UidRecord.ChangeItem changeItem) throws RemoteException;
    }

    private void verifyObserverReceivedChanges(IUidObserver observer, int[] changesToVerify,
            Map<Integer, UidRecord.ChangeItem> changeItems, ObserverChangesVerifier verifier)
            throws RemoteException {
        for (int change : changesToVerify) {
            final UidRecord.ChangeItem changeItem = changeItems.get(change);
            verifier.verify(observer, changeItem);
        }
    }

    /**
     * This test verifies that process state changes are dispatched to observers only when they
     * change across the cutpoint (this is specified when registering the observer).
     */
    @Test
    public void testDispatchUidChanges_procStateCutpoint() throws RemoteException {
        final IUidObserver observer = Mockito.mock(IUidObserver.Stub.class);

        when(observer.asBinder()).thenReturn((IBinder) observer);
        mAms.registerUidObserver(observer, ActivityManager.UID_OBSERVER_PROCSTATE /* which */,
                ActivityManager.PROCESS_STATE_SERVICE /* cutpoint */, null /* callingPackage */);
        // When we invoke AMS.registerUidObserver, there are some interactions with observer
        // mock in RemoteCallbackList class. We don't want to test those interactions and
        // at the same time, we don't want those to interfere with verifyNoMoreInteractions.
        // So, resetting the mock here.
        Mockito.reset(observer);

        final UidRecord.ChangeItem changeItem = new UidRecord.ChangeItem();
        changeItem.uid = TEST_UID;
        changeItem.change = UidRecord.CHANGE_PROCSTATE;
        changeItem.processState = ActivityManager.PROCESS_STATE_LAST_ACTIVITY;
        changeItem.procStateSeq = 111;
        mAms.mPendingUidChanges.add(changeItem);
        mAms.dispatchUidsChanged();
        // First process state message is always delivered regardless of whether the process state
        // change is above or below the cutpoint (PROCESS_STATE_SERVICE).
        verify(observer).onUidStateChanged(TEST_UID,
                changeItem.processState, changeItem.procStateSeq);
        verifyNoMoreInteractions(observer);

        changeItem.processState = ActivityManager.PROCESS_STATE_RECEIVER;
        mAms.mPendingUidChanges.add(changeItem);
        mAms.dispatchUidsChanged();
        // Previous process state change is below cutpoint (PROCESS_STATE_SERVICE) and
        // the current process state change is also below cutpoint, so no callback will be invoked.
        verifyNoMoreInteractions(observer);

        changeItem.processState = ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE;
        mAms.mPendingUidChanges.add(changeItem);
        mAms.dispatchUidsChanged();
        // Previous process state change is below cutpoint (PROCESS_STATE_SERVICE) and
        // the current process state change is above cutpoint, so callback will be invoked with the
        // current process state change.
        verify(observer).onUidStateChanged(TEST_UID,
                changeItem.processState, changeItem.procStateSeq);
        verifyNoMoreInteractions(observer);

        changeItem.processState = ActivityManager.PROCESS_STATE_TOP;
        mAms.mPendingUidChanges.add(changeItem);
        mAms.dispatchUidsChanged();
        // Previous process state change is above cutpoint (PROCESS_STATE_SERVICE) and
        // the current process state change is also above cutpoint, so no callback will be invoked.
        verifyNoMoreInteractions(observer);

        changeItem.processState = ActivityManager.PROCESS_STATE_CACHED_EMPTY;
        mAms.mPendingUidChanges.add(changeItem);
        mAms.dispatchUidsChanged();
        // Previous process state change is above cutpoint (PROCESS_STATE_SERVICE) and
        // the current process state change is below cutpoint, so callback will be invoked with the
        // current process state change.
        verify(observer).onUidStateChanged(TEST_UID,
                changeItem.processState, changeItem.procStateSeq);
        verifyNoMoreInteractions(observer);
    }

    /**
     * This test verifies that {@link ActivityManagerService#mValidateUids} which is a
     * part of dumpsys is correctly updated.
     */
    @Test
    public void testDispatchUidChanges_validateUidsUpdated() {
        final int[] changesForPendingItems = UID_RECORD_CHANGES;

        final int[] procStatesForPendingItems = {
            ActivityManager.PROCESS_STATE_FOREGROUND_SERVICE,
            ActivityManager.PROCESS_STATE_CACHED_EMPTY,
            ActivityManager.PROCESS_STATE_CACHED_ACTIVITY,
            ActivityManager.PROCESS_STATE_SERVICE,
            ActivityManager.PROCESS_STATE_RECEIVER
        };
        final ArrayList<UidRecord.ChangeItem> pendingItemsForUids =
                new ArrayList<>(changesForPendingItems.length);
        for (int i = 0; i < changesForPendingItems.length; ++i) {
            final UidRecord.ChangeItem item = new UidRecord.ChangeItem();
            item.uid = i;
            item.change = changesForPendingItems[i];
            item.processState = procStatesForPendingItems[i];
            pendingItemsForUids.add(i, item);
        }

        // Verify that when there no observers listening to uid state changes, then there will
        // be no changes to validateUids.
        mAms.mPendingUidChanges.addAll(pendingItemsForUids);
        mAms.dispatchUidsChanged();
        assertEquals("No observers registered, so validateUids should be empty",
                0, mAms.mValidateUids.size());

        final IUidObserver observer = Mockito.mock(IUidObserver.Stub.class);
        when(observer.asBinder()).thenReturn((IBinder) observer);
        mAms.registerUidObserver(observer, 0, 0, null);
        // Verify that when observers are registered, then validateUids is correctly updated.
        mAms.mPendingUidChanges.addAll(pendingItemsForUids);
        mAms.dispatchUidsChanged();
        for (int i = 0; i < pendingItemsForUids.size(); ++i) {
            final UidRecord.ChangeItem item = pendingItemsForUids.get(i);
            final UidRecord validateUidRecord = mAms.mValidateUids.get(item.uid);
            if ((item.change & UidRecord.CHANGE_GONE) != 0) {
                assertNull("validateUidRecord should be null since the change is either "
                        + "CHANGE_GONE or CHANGE_GONE_IDLE", validateUidRecord);
            } else {
                assertNotNull("validateUidRecord should not be null since the change is neither "
                        + "CHANGE_GONE nor CHANGE_GONE_IDLE", validateUidRecord);
                assertEquals("processState: " + item.processState + " curProcState: "
                        + validateUidRecord.getCurProcState() + " should have been equal",
                        item.processState, validateUidRecord.getCurProcState());
                assertEquals("processState: " + item.processState + " setProcState: "
                        + validateUidRecord.getCurProcState() + " should have been equal",
                        item.processState, validateUidRecord.setProcState);
                if (item.change == UidRecord.CHANGE_IDLE) {
                    assertTrue("UidRecord.idle should be updated to true for CHANGE_IDLE",
                            validateUidRecord.idle);
                } else if (item.change == UidRecord.CHANGE_ACTIVE) {
                    assertFalse("UidRecord.idle should be updated to false for CHANGE_ACTIVE",
                            validateUidRecord.idle);
                }
            }
        }

        // Verify that when uid state changes to CHANGE_GONE or CHANGE_GONE_IDLE, then it
        // will be removed from validateUids.
        assertNotEquals("validateUids should not be empty", 0,
                mAms.mValidateUids.size());
        for (int i = 0; i < pendingItemsForUids.size(); ++i) {
            final UidRecord.ChangeItem item = pendingItemsForUids.get(i);
            // Assign CHANGE_GONE_IDLE to some items and CHANGE_GONE to the others, using even/odd
            // distribution for this assignment.
            item.change = (i % 2) == 0 ? (UidRecord.CHANGE_GONE | UidRecord.CHANGE_IDLE)
                    : UidRecord.CHANGE_GONE;
        }
        mAms.mPendingUidChanges.addAll(pendingItemsForUids);
        mAms.dispatchUidsChanged();
        assertEquals("validateUids should be empty, size=" + mAms.mValidateUids.size(),
                0, mAms.mValidateUids.size());
    }

    @Test
    public void testEnqueueUidChangeLocked_procStateSeqUpdated() {
        final UidRecord uidRecord = new UidRecord(TEST_UID);
        uidRecord.curProcStateSeq = TEST_PROC_STATE_SEQ1;

        // Verify with no pending changes for TEST_UID.
        verifyLastProcStateSeqUpdated(uidRecord, -1, TEST_PROC_STATE_SEQ1);

        // Add a pending change for TEST_UID and verify enqueueUidChangeLocked still works as
        // expected.
        final UidRecord.ChangeItem changeItem = new UidRecord.ChangeItem();
        uidRecord.pendingChange = changeItem;
        uidRecord.curProcStateSeq = TEST_PROC_STATE_SEQ2;
        verifyLastProcStateSeqUpdated(uidRecord, -1, TEST_PROC_STATE_SEQ2);
    }

    @Test
    public void testEnqueueUidChangeLocked_nullUidRecord() {
        // Use "null" uidRecord to make sure there is no crash.
        mAms.enqueueUidChangeLocked(null, TEST_UID, UidRecord.CHANGE_ACTIVE);
    }

    private void verifyLastProcStateSeqUpdated(UidRecord uidRecord, int uid, long curProcstateSeq) {
        // Test enqueueUidChangeLocked with every UidRecord.CHANGE_*
        for (int i = 0; i < UID_RECORD_CHANGES.length; ++i) {
            final int changeToDispatch = UID_RECORD_CHANGES[i];
            // Reset lastProcStateSeqDispatchToObservers after every test.
            uidRecord.lastDispatchedProcStateSeq = 0;
            mAms.enqueueUidChangeLocked(uidRecord, uid, changeToDispatch);
            // Verify there is no effect on curProcStateSeq.
            assertEquals(curProcstateSeq, uidRecord.curProcStateSeq);
            if ((changeToDispatch & UidRecord.CHANGE_GONE) != 0) {
                // Since the change is CHANGE_GONE or CHANGE_GONE_IDLE, verify that
                // lastProcStateSeqDispatchedToObservers is not updated.
                assertNotEquals(uidRecord.curProcStateSeq,
                        uidRecord.lastDispatchedProcStateSeq);
            } else {
                // Since the change is neither CHANGE_GONE nor CHANGE_GONE_IDLE, verify that
                // lastProcStateSeqDispatchedToObservers has been updated to curProcStateSeq.
                assertEquals(uidRecord.curProcStateSeq,
                        uidRecord.lastDispatchedProcStateSeq);
            }
        }
    }

    @MediumTest
    @Test
    public void testEnqueueUidChangeLocked_dispatchUidsChanged() {
        final UidRecord uidRecord = new UidRecord(TEST_UID);
        final int expectedProcState = PROCESS_STATE_SERVICE;
        uidRecord.setProcState = expectedProcState;
        uidRecord.curProcStateSeq = TEST_PROC_STATE_SEQ1;

        // Test with no pending uid records.
        for (int i = 0; i < UID_RECORD_CHANGES.length; ++i) {
            final int changeToDispatch = UID_RECORD_CHANGES[i];

            // Reset the current state
            mHandler.reset();
            uidRecord.pendingChange = null;
            mAms.mPendingUidChanges.clear();

            mAms.enqueueUidChangeLocked(uidRecord, -1, changeToDispatch);

            // Verify that UidRecord.pendingChange is updated correctly.
            assertNotNull(uidRecord.pendingChange);
            assertEquals(TEST_UID, uidRecord.pendingChange.uid);
            assertEquals(expectedProcState, uidRecord.pendingChange.processState);
            assertEquals(TEST_PROC_STATE_SEQ1, uidRecord.pendingChange.procStateSeq);

            // Verify that DISPATCH_UIDS_CHANGED_UI_MSG is posted to handler.
            mHandler.waitForMessage(DISPATCH_UIDS_CHANGED_UI_MSG);
        }
    }

    @MediumTest
    @Test
    public void testWaitForNetworkStateUpdate() throws Exception {
        // Check there is no crash when there is no UidRecord for myUid
        mAms.waitForNetworkStateUpdate(TEST_PROC_STATE_SEQ1);

        // Verify there is no waiting when UidRecord.curProcStateSeq is greater than
        // the procStateSeq in the request to wait.
        verifyWaitingForNetworkStateUpdate(
                TEST_PROC_STATE_SEQ1, // curProcStateSeq
                TEST_PROC_STATE_SEQ1, // lastDsipatchedProcStateSeq
                TEST_PROC_STATE_SEQ1 - 4, // lastNetworkUpdatedProcStateSeq
                TEST_PROC_STATE_SEQ1 - 2, // procStateSeqToWait
                false); // expectWait

        // Verify there is no waiting when the procStateSeq in the request to wait is
        // not dispatched to NPMS.
        verifyWaitingForNetworkStateUpdate(
                TEST_PROC_STATE_SEQ1, // curProcStateSeq
                TEST_PROC_STATE_SEQ1 - 1, // lastDsipatchedProcStateSeq
                TEST_PROC_STATE_SEQ1 - 1, // lastNetworkUpdatedProcStateSeq
                TEST_PROC_STATE_SEQ1, // procStateSeqToWait
                false); // expectWait

        // Verify there is not waiting when the procStateSeq in the request already has
        // an updated network state.
        verifyWaitingForNetworkStateUpdate(
                TEST_PROC_STATE_SEQ1, // curProcStateSeq
                TEST_PROC_STATE_SEQ1, // lastDsipatchedProcStateSeq
                TEST_PROC_STATE_SEQ1, // lastNetworkUpdatedProcStateSeq
                TEST_PROC_STATE_SEQ1, // procStateSeqToWait
                false); // expectWait

        // Verify waiting for network works
        verifyWaitingForNetworkStateUpdate(
                TEST_PROC_STATE_SEQ1, // curProcStateSeq
                TEST_PROC_STATE_SEQ1, // lastDsipatchedProcStateSeq
                TEST_PROC_STATE_SEQ1 - 1, // lastNetworkUpdatedProcStateSeq
                TEST_PROC_STATE_SEQ1, // procStateSeqToWait
                true); // expectWait
    }

    private void verifyWaitingForNetworkStateUpdate(long curProcStateSeq,
            long lastDispatchedProcStateSeq, long lastNetworkUpdatedProcStateSeq,
            final long procStateSeqToWait, boolean expectWait) throws Exception {
        final UidRecord record = new UidRecord(Process.myUid());
        record.curProcStateSeq = curProcStateSeq;
        record.lastDispatchedProcStateSeq = lastDispatchedProcStateSeq;
        record.lastNetworkUpdatedProcStateSeq = lastNetworkUpdatedProcStateSeq;
        mAms.mProcessList.mActiveUids.put(Process.myUid(), record);

        CustomThread thread = new CustomThread(record.networkStateLock, new Runnable() {
            @Override
            public void run() {
                mAms.waitForNetworkStateUpdate(procStateSeqToWait);
            }
        });
        final String errMsg = "Unexpected state for " + record;
        if (expectWait) {
            thread.startAndWait(errMsg, true);
            thread.assertTimedWaiting(errMsg);
            synchronized (record.networkStateLock) {
                record.networkStateLock.notifyAll();
            }
            thread.assertTerminated(errMsg);
            assertTrue(thread.mNotified);
            assertFalse(record.waitingForNetwork);
        } else {
            thread.start();
            thread.assertTerminated(errMsg);
        }

        mAms.mProcessList.mActiveUids.clear();
    }

    private static class TestHandler extends Handler {
        private static final long WAIT_FOR_MSG_TIMEOUT_MS = 4000; // 4 sec
        private static final long WAIT_FOR_MSG_INTERVAL_MS = 400; // 0.4 sec

        private Set<Integer> mMsgsHandled = new HashSet<>();

        TestHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            mMsgsHandled.add(msg.what);
        }

        public void waitForMessage(int msg) {
            final long endTime = System.currentTimeMillis() + WAIT_FOR_MSG_TIMEOUT_MS;
            while (!mMsgsHandled.contains(msg) && System.currentTimeMillis() < endTime) {
                SystemClock.sleep(WAIT_FOR_MSG_INTERVAL_MS);
            }
            if (!mMsgsHandled.contains(msg)) {
                fail("Timed out waiting for the message to be handled, msg: " + msg);
            }
        }

        public void reset() {
            mMsgsHandled.clear();
        }
    }

    private class TestInjector extends Injector {
        private boolean mRestricted = true;

        @Override
        public Context getContext() {
            return mContext;
        }

        @Override
        public AppOpsService getAppOpsService(File file, Handler handler) {
            return mAppOpsService;
        }

        @Override
        public Handler getUiHandler(ActivityManagerService service) {
            return mHandler;
        }

        @Override
        public boolean isNetworkRestrictedForUid(int uid) {
            return mRestricted;
        }

        public void setNetworkRestrictedForUid(boolean restricted) {
            mRestricted = restricted;
        }
    }
}
