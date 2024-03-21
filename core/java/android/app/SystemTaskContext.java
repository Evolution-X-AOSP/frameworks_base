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
 * Helper class for building a remote task context that can be used with
 * {@link com.app.IRemoteTaskHandler#verifyRemoteTask(android.app.SystemTaskContext)
 * @hide
 */
public final class SystemTaskContext implements Parcelable {

    private  String uuid;
    private String securityToken;
    private int launchFlag;
    private String launchFromPackage;
    private int displayId;
    private int sourceDisplayId;
    private int reuseDisplayId;
    private boolean isHomeIntent;
    private boolean isEdgeCase;

    private SystemTaskContext(@Nullable String uuid, @Nullable String securityToken,
                              int launchFlag, @Nullable String launchPackage,
                              int displayId, int sourceDisplayId, int reuseDisplayId,
                              boolean isHomeIntent, boolean isEdgeCase) {
        this.uuid = uuid;
        this.securityToken = securityToken;
        this.launchFlag = launchFlag;
        this.launchFromPackage = launchPackage;
        this.displayId = displayId;
        this.sourceDisplayId = sourceDisplayId;
        this.reuseDisplayId = reuseDisplayId;
        this.isHomeIntent = isHomeIntent;
        this.isEdgeCase = isEdgeCase;
    }

    public static @Nullable SystemTaskContext create(@Nullable String uuid, @Nullable String securityToken,
                                                     int launchFlag, @Nullable String launchPackage,
                                                     int displayId, int sourceDisplayId, int reuseDisplayId,
                                                     boolean isHomeIntent, boolean isEdgeCase) {
        return new SystemTaskContext(uuid, securityToken, launchFlag, launchPackage, displayId, sourceDisplayId,
                                    reuseDisplayId, isHomeIntent, isEdgeCase);
    }

    private SystemTaskContext(Parcel in) {
        uuid = in.readString();
        securityToken = in.readString();
        launchFlag = in.readInt();
        launchFromPackage = in.readString();
        displayId = in.readInt();
        sourceDisplayId = in.readInt();
        reuseDisplayId = in.readInt();
        isHomeIntent = in.readBoolean();
        isEdgeCase = in.readBoolean();
    }

    public @Nullable String getUuid() {
        return uuid;
    }

    public void setUuid(@Nullable String uuid) {
        this.uuid = uuid;
    }

    public @Nullable String getSecurityToken() {
        return securityToken;
    }

    public void setSecurityToken(@Nullable String securityToken) {
        this.securityToken = securityToken;
    }

    public int getLaunchFlag() {
        return launchFlag;
    }

    public void setLaunchFlag(int launchFlag) {
        this.launchFlag = launchFlag;
    }

    public @Nullable String getLaunchFromPackage() {
        return launchFromPackage;
    }

    public void setLaunchFromPackage(@Nullable String launchFromPackage) {
        this.launchFromPackage = launchFromPackage;
    }

    public int getDisplayId() {
        return displayId;
    }

    public void setDisplayId(int displayId) {
        this.displayId = displayId;
    }

    public int getSourceDisplayId() {
        return sourceDisplayId;
    }

    public void setSourceDisplayId(int sourceDisplayId) {
        this.sourceDisplayId = sourceDisplayId;
    }

    public int getReuseDisplayId() {
        return reuseDisplayId;
    }

    public void setResueDisplayId(int reuseDisplayId) {
        this.reuseDisplayId = reuseDisplayId;
    }

    public boolean isHomeIntent() {
        return isHomeIntent;
    }

    public void setHomeIntent(boolean isHomeIntent) {
        this.isHomeIntent = isHomeIntent;
    }

    public boolean isEdgeCase() {
        return isEdgeCase;
    }

    public void setEdgeCase(boolean isEdgeCase) {
        this.isEdgeCase = isEdgeCase;
    }

    public static final @NonNull Creator<SystemTaskContext> CREATOR = new Parcelable.Creator<SystemTaskContext>() {
        @Override
        public SystemTaskContext createFromParcel(Parcel in) {
            return new SystemTaskContext(in);
        }

        @Override
        public SystemTaskContext[] newArray(int size) {
            return new SystemTaskContext[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(uuid);
        dest.writeString(securityToken);
        dest.writeInt(launchFlag);
        dest.writeString(launchFromPackage);
        dest.writeInt(displayId);
        dest.writeInt(sourceDisplayId);
        dest.writeInt(reuseDisplayId);
        dest.writeBoolean(isHomeIntent);
        dest.writeBoolean(isEdgeCase);
    }
}
