/*
 * Copyright (C) 2023 The LineageOS Project
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

package com.android.systemui.evolution

import com.android.systemui.qs.tileimpl.QSTileImpl
import com.android.systemui.qs.tiles.AmbientDisplayTile
import com.android.systemui.qs.tiles.AntiFlickerTile
import com.android.systemui.qs.tiles.AODTile
import com.android.systemui.qs.tiles.CaffeineTile
import com.android.systemui.qs.tiles.CellularTile
import com.android.systemui.qs.tiles.CompassTile
import com.android.systemui.qs.tiles.DataSwitchTile
import com.android.systemui.qs.tiles.DcDimmingTile
import com.android.systemui.qs.tiles.GloveModeTile
import com.android.systemui.qs.tiles.HeadsUpTile
import com.android.systemui.qs.tiles.LiveDisplayTile
import com.android.systemui.qs.tiles.LocaleTile
import com.android.systemui.qs.tiles.OnTheGoTile
import com.android.systemui.qs.tiles.PowerShareTile
import com.android.systemui.qs.tiles.PreferredNetworkTile
import com.android.systemui.qs.tiles.ReadingModeTile
import com.android.systemui.qs.tiles.RebootTile
import com.android.systemui.qs.tiles.RefreshRateTile
import com.android.systemui.qs.tiles.ScreenshotTile
import com.android.systemui.qs.tiles.SleepModeTile
import com.android.systemui.qs.tiles.SmartPixelsTile
import com.android.systemui.qs.tiles.SoundSearchTile
import com.android.systemui.qs.tiles.SoundTile
import com.android.systemui.qs.tiles.SyncTile
import com.android.systemui.qs.tiles.UsbTetherTile
import com.android.systemui.qs.tiles.VolumeTile
import com.android.systemui.qs.tiles.VPNTetheringTile
import com.android.systemui.qs.tiles.VpnTile
import com.android.systemui.qs.tiles.WifiTile

import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
interface EvolutionModule {
    /** Inject AmbientDisplayTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AmbientDisplayTile.TILE_SPEC)
    fun bindAmbientDisplayTile(ambientDisplayTile: AmbientDisplayTile): QSTileImpl<*>

    /** Inject AntiFlickerTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AntiFlickerTile.TILE_SPEC)
    fun bindAntiFlickerTile(antiFlickerTile: AntiFlickerTile): QSTileImpl<*>

    /** Inject AODTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(AODTile.TILE_SPEC)
    fun bindAODTile(aODTile: AODTile): QSTileImpl<*>

    /** Inject CaffeineTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CaffeineTile.TILE_SPEC)
    fun bindCaffeineTile(caffeineTile: CaffeineTile): QSTileImpl<*>

    /** Inject CellularTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CellularTile.TILE_SPEC)
    fun bindCellularTile(cellularTile: CellularTile): QSTileImpl<*>

    /** Inject CompassTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(CompassTile.TILE_SPEC)
    fun bindCompassTile(compassTile: CompassTile): QSTileImpl<*>

    /** Inject DataSwitchTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(DataSwitchTile.TILE_SPEC)
    fun bindDataSwitchTile(dataSwitchTile: DataSwitchTile): QSTileImpl<*>

    /** Inject DcDimmingTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(DcDimmingTile.TILE_SPEC)
    fun bindDcDimmingTile(dcDimmingTile: DcDimmingTile): QSTileImpl<*>

    /** Inject GloveModeTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(GloveModeTile.TILE_SPEC)
    fun bindGloveModeTile(gloveModeTile: GloveModeTile): QSTileImpl<*>

    /** Inject HeadsUpTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(HeadsUpTile.TILE_SPEC)
    fun bindHeadsUpTile(headsUpTile: HeadsUpTile): QSTileImpl<*>

    /** Inject LiveDisplayTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(LiveDisplayTile.TILE_SPEC)
    fun bindLiveDisplayTile(liveDisplayTile: LiveDisplayTile): QSTileImpl<*>

    /** Inject LocaleTile into tileMap in QSModule */
    @Binds  
    @IntoMap
    @StringKey(LocaleTile.TILE_SPEC)
    fun bindLocaleTile(localeTile: LocaleTile): QSTileImpl<*>

    /** Inject OnTheGoTile into tileMap in QSModule */
    @Binds  
    @IntoMap
    @StringKey(OnTheGoTile.TILE_SPEC)
    fun bindOnTheGoTile(onTheGoTile: OnTheGoTile): QSTileImpl<*>

    /** Inject PowerShareTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(PowerShareTile.TILE_SPEC)
    fun bindPowerShareTile(powerShareTile: PowerShareTile): QSTileImpl<*>

    /** Inject PreferredNetworkTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(PreferredNetworkTile.TILE_SPEC)
    fun bindPreferredNetworkTile(preferredNetworkTile: PreferredNetworkTile): QSTileImpl<*>

    /** Inject ReadingModeTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(ReadingModeTile.TILE_SPEC)
    fun bindReadingModeTile(readingModeTile: ReadingModeTile): QSTileImpl<*>

    /** Inject RebootTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(RebootTile.TILE_SPEC)
    fun bindRebootTile(rebootTile: RebootTile): QSTileImpl<*>

    /** Inject RefreshRateTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(RefreshRateTile.TILE_SPEC)
    fun bindRefreshRateTile(refreshRateTile: RefreshRateTile): QSTileImpl<*>

    /** Inject ScreenshotTile into tileMap in QSModule */
    @Binds  
    @IntoMap
    @StringKey(ScreenshotTile.TILE_SPEC)
    fun bindScreenshotTile(screenshotTile: ScreenshotTile): QSTileImpl<*>

    /** Inject SleepModeTile into tileMap in QSModule */
    @Binds  
    @IntoMap
    @StringKey(SleepModeTile.TILE_SPEC)
    fun bindSleepModeTile(sleepModeTile: SleepModeTile): QSTileImpl<*>

    /** Inject SmartPixelsTile into tileMap in QSModule */
    @Binds  
    @IntoMap
    @StringKey(SmartPixelsTile.TILE_SPEC)
    fun bindSmartPixelsTile(smartPixelsTile: SmartPixelsTile): QSTileImpl<*>

    /** Inject SoundSearchTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(SoundSearchTile.TILE_SPEC)
    fun bindSoundSearchTile(soundSearchTile: SoundSearchTile): QSTileImpl<*>

    /** Inject SoundTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(SoundTile.TILE_SPEC)
    fun bindSoundTile(soundTile: SoundTile): QSTileImpl<*>

    /** Inject SyncTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(SyncTile.TILE_SPEC)
    fun bindSyncTile(syncTile: SyncTile): QSTileImpl<*>

    /** Inject UsbTetherTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(UsbTetherTile.TILE_SPEC)
    fun bindUsbTetherTile(usbTetherTile: UsbTetherTile): QSTileImpl<*>

    /** Inject VPNTetheringTile into tileMap in QSModule */
    @Binds  
    @IntoMap
    @StringKey(VPNTetheringTile.TILE_SPEC)
    fun bindVPNTetheringTile(vPNTetheringTile: VPNTetheringTile): QSTileImpl<*>

    /** Inject VpnTile into tileMap in QSModule */
    @Binds  
    @IntoMap
    @StringKey(VpnTile.TILE_SPEC)
    fun bindVpnTile(vpnTile: VpnTile): QSTileImpl<*>

    /** Inject VolumeTile into tileMap in QSModule */
    @Binds  
    @IntoMap
    @StringKey(VolumeTile.TILE_SPEC)
    fun bindVolumeTile(volumeTile: VolumeTile): QSTileImpl<*>

    /** Inject WifiTile into tileMap in QSModule */
    @Binds
    @IntoMap
    @StringKey(WifiTile.TILE_SPEC)
    fun bindWifiTile(wifiTile: WifiTile): QSTileImpl<*>
}
