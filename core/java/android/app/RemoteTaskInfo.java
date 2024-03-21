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

import android.os.Parcel;
import android.os.Parcelable;
import android.annotation.NonNull;
import android.annotation.Nullable;

/**
 * Helper class for building a remote task info that can be used as parameter of
 * {@link com.app.ICrossDeviceService#activateRemoteTask(android.app.RemoteTaskInfo)
 * to declare the remote task detail information that has been activated.
 * @hide
 */
public final class RemoteTaskInfo implements Parcelable {
    private  String uuid;
    private String packageName;
    private  int displayId;
    private  int taskId;

    private RemoteTaskInfo(@Nullable String uuid, @Nullable String packageName,int displayId, int taskId) {
        this.uuid = uuid;
        this.packageName = packageName;
        this.displayId = displayId;
        this.taskId = taskId;
    }

    public static @NonNull RemoteTaskInfo create(@Nullable String uuid, @Nullable String packageName,int displayId, int taskId) {
        return new RemoteTaskInfo(uuid, packageName, displayId, taskId);
    }

    private RemoteTaskInfo(Parcel in) {
        uuid = in.readString();
        packageName = in.readString();
        displayId = in.readInt();
        taskId = in.readInt();
    }

    public @Nullable String getUuid() {
        return uuid;
    }

    public void setUuid(@Nullable String uuid) {
        this.uuid = uuid;
    }

    public @Nullable String getPackageName() {
        return packageName;
    }

    public void setPackageName(@Nullable String packageName) {
        this.packageName = packageName;
    }

    public int getDisplayId() {
        return displayId;
    }

    public void setDisplayId(int displayId) {
        this.displayId = displayId;
    }

    public int getTaskId() {
        return taskId;
    }

    public void setTaskId(int taskId) {
        this.taskId = taskId;
    }

    public static final @NonNull Creator<RemoteTaskInfo> CREATOR = new Creator<RemoteTaskInfo>() {
        @Override
        public RemoteTaskInfo createFromParcel(Parcel in) {
            return new RemoteTaskInfo(in);
        }

        @Override
        public RemoteTaskInfo[] newArray(int size) {
            return new RemoteTaskInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(uuid);
        dest.writeString(packageName);
        dest.writeInt(displayId);
        dest.writeInt(taskId);
    }
}
