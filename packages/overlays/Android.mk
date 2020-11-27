# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE := frameworks-base-overlays
LOCAL_REQUIRED_MODULES := \
	AccentColorAmethystOverlay \
	AccentColorAquamarineOverlay \
	AccentColorBlackOverlay \
	AccentColorCarbonOverlay \
	AccentColorCinnamonOverlay \
	AccentColorGreenOverlay \
	AccentColorOceanOverlay \
	AccentColorOrchidOverlay \
	AccentColorPaletteOverlay \
	AccentColorPurpleOverlay \
	AccentColorSandOverlay \
	AccentColorSpaceOverlay \
	AccentColorTangerineOverlay \
	AccentColorBlueGrayOverlay \
	AccentColorCyanOverlay \
	AccentColorDorsetGoldOverlay \
	AccentColorFlatPinkOverlay \
	AccentColorIndigoOverlay \
	AccentColorInfernoRedOverlay \
	AccentColorLightPurpleOverlay \
	AccentColorMetallicGoldOverlay \
	AccentColorPinkOverlay \
	AccentColorRedOverlay \
	AccentColorTealOverlay \
	AccentColorCocaColaOverlay \
	AccentColorCoralOverlay \
	AccentColorDiscordOverlay \
	AccentColorEvolutionBlueOverlay \
	AccentColorEvolutionGreenOverlay \
	AccentColorEvolutionRedOverlay \
	AccentColorEvolutionYellowOverlay \
	AccentColorFerrariRedOverlay \
	AccentColorGoldenShowerOverlay \
	AccentColorJollibeeOverlay \
	AccentColorMatrixOverlay \
	AccentColorNextbitOverlay \
	AccentColorOnePlusOverlay \
	AccentColorOrangeOverlay \
	AccentColorParanoidOverlay \
	AccentColorPepsiOverlay \
	AccentColorPixelBlueOverlay \
	AccentColorRazerOverlay \
	AccentColorSalmonOverlay \
	AccentColorStarbucksOverlay \
	AccentColorUbuntuOverlay \
	AccentColorXboxOverlay \
	AccentColorXiaomiOverlay \
	DisplayCutoutEmulationCornerOverlay \
	DisplayCutoutEmulationDoubleOverlay \
	DisplayCutoutEmulationHoleOverlay \
	DisplayCutoutEmulationTallOverlay \
	DisplayCutoutEmulationWaterfallOverlay \
	FontArbutusSourceOverlay \
	FontArvoLatoOverlay \
	FontKaiOverlay \
	FontRubikRubikOverlay \
	FontSamOverlay \
	FontVictorOverlay \
	FontAclonicaSourceOverlay \
	FontAmaranteSourceOverlay \
	FontAnaheimSourceOverlay \
	FontBariolSourceOverlay \
	FontCagliostroSourceOverlay \
	FontCircularStdOverlay \
	FontCoolstorySourceOverlay \
	FontLGSmartGothicSourceOverlay \
	FontLinotteOverlay \
	FontRosemarySourceOverlay \
	FontSlateForOnePlusOverlay \
	FontSonySketchSourceOverlay \
	FontSurferSourceOverlay \
	FontTinkerbellSourceOverlay \
	IconPackCircularAndroidOverlay \
	IconPackCircularPixelLauncherOverlay \
	IconPackCircularPixelThemePickerOverlay \
	IconPackCircularSettingsOverlay \
	IconPackCircularSystemUIOverlay \
	IconPackFilledAndroidOverlay \
	IconPackFilledPixelLauncherOverlay \
	IconPackFilledPixelThemePickerOverlay \
	IconPackFilledSettingsOverlay \
	IconPackFilledSystemUIOverlay \
	IconPackKaiAndroidOverlay \
	IconPackKaiPixelLauncherOverlay \
	IconPackKaiPixelThemePickerOverlay \
	IconPackKaiSettingsOverlay \
	IconPackKaiSystemUIOverlay \
	IconPackRoundedAndroidOverlay \
	IconPackRoundedPixelLauncherOverlay \
	IconPackRoundedPixelThemePickerOverlay \
	IconPackRoundedSettingsOverlay \
	IconPackRoundedSystemUIOverlay \
	IconPackSamAndroidOverlay \
	IconPackSamPixelLauncherOverlay \
	IconPackSamPixelThemePickerOverlay \
	IconPackSamSettingsOverlay \
	IconPackSamSystemUIOverlay \
	IconPackVictorAndroidOverlay \
	IconPackVictorPixelLauncherOverlay \
	IconPackVictorPixelThemePickerOverlay \
	IconPackVictorSettingsOverlay \
	IconPackVictorSystemUIOverlay \
	IconShapeHeartOverlay \
	IconShapeMallowOverlay \
	IconShapePebbleOverlay \
	IconShapeRoundedRectOverlay \
	IconShapeSquareOverlay \
	IconShapeSquircleOverlay \
	IconShapeTaperedRectOverlay \
	IconShapeTeardropOverlay \
	IconShapeVesselOverlay \
	NavigationBarMode2ButtonOverlay \
	NavigationBarMode3ButtonOverlay \
	NavigationBarModeGesturalOverlay \
	NavigationBarModeGesturalOverlayNarrowBack \
	NavigationBarModeGesturalOverlayWideBack \
	NavigationBarModeGesturalOverlayExtraWideBack \
	preinstalled-packages-platform-overlays.xml

include $(BUILD_PHONY_PACKAGE)
include $(CLEAR_VARS)

LOCAL_MODULE := frameworks-base-overlays-debug

include $(BUILD_PHONY_PACKAGE)
include $(call first-makefiles-under,$(LOCAL_PATH))
