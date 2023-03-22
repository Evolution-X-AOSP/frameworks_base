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

package android.hardware.display;

import android.os.IBinder;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.content.Context;
import android.app.ICrossDeviceService;

/**
 * Manager communication with the Cross Device Service on behalf of
 * Remote task issues.  You're probably looking for {@link ICrossDeviceService}.
 *
 * @hide
 */
public class RemoteTaskHelper {

    public static boolean isFromRemoteTaskWhiteList() {
        IBinder b = ServiceManager.getService(Context.CROSS_DEVICE_SERVICE);
        if (b != null) {
            ICrossDeviceService crossDeviceService = ICrossDeviceService.Stub.asInterface(b);
            try {
                return crossDeviceService.isFromBackgroundWhiteList();
            }  catch (RemoteException ex) {
                throw ex.rethrowFromSystemServer();
            }
        }
        return false;
    }
}