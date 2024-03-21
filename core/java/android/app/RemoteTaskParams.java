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
 * Helper class for building a remote task options that can be used as return value
 * {@link com.app.IRemoteTaskHandler#verifyRemoteTask(android.app.SystemTaskContext)
 * @hide
 */
public final class RemoteTaskParams implements Parcelable {

    private String uuid;
    private int launchScenario;
    private int displayId;

    private RemoteTaskParams(@Nullable String uuid, int launchScenario,int displayId) {
        this.uuid = uuid;
        this.launchScenario = launchScenario;
        this.displayId = displayId;
    }

    public static @NonNull RemoteTaskParams create(@Nullable String uuid, int launchScenario,int displayId) {
        return new RemoteTaskParams(uuid, launchScenario, displayId);
    }

    private RemoteTaskParams(Parcel in) {
        uuid = in.readString();
        launchScenario = in.readInt();
        displayId = in.readInt();
    }

    public @Nullable String getUuid() {
        return uuid;
    }

    public void setUuid(@Nullable String uuid) {
        this.uuid = uuid;
    }

    public int getLaunchScenario() {
        return launchScenario;
    }

    public void setLaunchScenario(int launchScenario) {
        this.launchScenario = launchScenario;
    }

    public int getDisplayId() {
        return displayId;
    }

    public void setDisplayId(int displayId) {
        this.displayId = displayId;
    }

    public static final @NonNull Creator<RemoteTaskParams> CREATOR = new Parcelable.Creator<RemoteTaskParams>() {
        @Override
        public RemoteTaskParams createFromParcel(Parcel in) {
            return new RemoteTaskParams(in);
        }

        @Override
        public RemoteTaskParams[] newArray(int size) {
            return new RemoteTaskParams[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(uuid);
        dest.writeInt(launchScenario);
        dest.writeInt(displayId);
    }
}
