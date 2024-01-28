/*
 * Copyright (C) 2023 The LibreMobileOS Foundation
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
package com.android.systemui.statusbar.notification.collection.coordinator

import android.app.Notification

import com.android.systemui.evolution.pulselight.PulseLightNotifManager
import com.android.systemui.statusbar.notification.collection.NotifPipeline
import com.android.systemui.statusbar.notification.collection.NotificationEntry
import com.android.systemui.statusbar.notification.collection.coordinator.dagger.CoordinatorScope
import com.android.systemui.statusbar.notification.collection.notifcollection.NotifCollectionListener

import javax.inject.Inject

@CoordinatorScope
class PulseLightCoordinator @Inject constructor(
    private val manager: PulseLightNotifManager
) : Coordinator {

    private val notifCollectionListener = object : NotifCollectionListener {

        override fun onEntryAdded(entry: NotificationEntry) {
            manager.onNotificationPosted(entry)
        }

        override fun onEntryUpdated(entry: NotificationEntry) {
            // Pass notification only if it's have to alert user again.
            if (shouldAlertAgain(entry)) {
                manager.onNotificationPosted(entry)
            }
        }

    }

    /**
     * Checks whether an update for a notification warrants an alert for the user.
     */
    private fun shouldAlertAgain(entry: NotificationEntry): Boolean {
        return (!entry.hasInterrupted() ||
                (entry.sbn.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) == 0)
    }


    override fun attach(pipeline: NotifPipeline) {
        pipeline.addCollectionListener(notifCollectionListener)
    }

}
