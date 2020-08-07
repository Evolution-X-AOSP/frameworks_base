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
	AccentColorBlackOverlay \
	AccentColorBlueGrayOverlay \
	AccentColorCinnamonOverlay \
	AccentColorCocaColaOverlay \
	AccentColorCoralOverlay \
	AccentColorCyanOverlay \
	AccentColorDiscordOverlay \
	AccentColorDorsetGoldOverlay \
	AccentColorEvolutionBlueOverlay \
	AccentColorEvolutionGreenOverlay \
	AccentColorEvolutionRedOverlay \
	AccentColorEvolutionYellowOverlay \
	AccentColorFerrariRedOverlay \
	AccentColorFlatPinkOverlay \
	AccentColorGoldenShowerOverlay \
	AccentColorGreenOverlay \
	AccentColorIndigoOverlay \
	AccentColorInfernoRedOverlay \
	AccentColorJollibeeOverlay \
	AccentColorLightPurpleOverlay \
	AccentColorMatrixOverlay \
	AccentColorMetallicGoldOverlay \
	AccentColorNextbitOverlay \
	AccentColorOceanOverlay \
	AccentColorOnePlusOverlay \
	AccentColorOrangeOverlay \
	AccentColorOrchidOverlay \
	AccentColorParanoidOverlay \
	AccentColorPepsiOverlay \
	AccentColorPinkOverlay \
	AccentColorPixelBlueOverlay \
	AccentColorPurpleOverlay \
	AccentColorRazerOverlay \
	AccentColorRedOverlay \
	AccentColorSalmonOverlay \
	AccentColorSpaceOverlay \
	AccentColorStarbucksOverlay \
	AccentColorTealOverlay \
	AccentColorUbuntuOverlay \
	AccentColorXboxOverlay \
	AccentColorXiaomiOverlay \
	DisplayCutoutEmulationCornerOverlay \
	DisplayCutoutEmulationDoubleOverlay \
	DisplayCutoutEmulationTallOverlay \
	FontAclonicaSourceOverlay \
	FontAmaranteSourceOverlay \
	FontAnaheimSourceOverlay \
	FontArbutusSourceOverlay \
	FontArvoLatoOverlay \
	FontBariolSourceOverlay \
	FontCagliostroSourceOverlay \
	FontCircularStdOverlay \
	FontCoolstorySourceOverlay \
	FontGoogleSansOverlay \
	FontLGSmartGothicSourceOverlay \
	FontLinotteSourceOverlay \
	FontNotoSerifSourceOverlay \
	FontRosemarySourceOverlay \
	FontRubikRubikOverlay \
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
	IconPackRoundedAndroidOverlay \
	IconPackRoundedPixelLauncherOverlay \
	IconPackRoundedPixelThemePickerOverlay \
	IconPackRoundedSettingsOverlay \
	IconPackRoundedSystemUIOverlay \
	IconShapeCylinderOverlay \
	IconShapeHexagonOverlay \
	IconShapePebbleOverlay \
	IconShapeRoundedHexagonOverlay \
	IconShapeRoundedRectOverlay \
	IconShapeSquareOverlay \
	IconShapeSquircleOverlay \
	IconShapeTaperedRectOverlay \
	IconShapeTeardropOverlay \
	IconShapeVesselOverlay \
	NavigationBarMode2ButtonOverlay \
	NavigationBarMode3ButtonOverlay \
	NavigationBarModeGesturalOverlay \
	NavigationBarModeGesturalOverlayExtraWideBack \
	NavigationBarModeGesturalOverlayNarrowBack \
	NavigationBarModeGesturalOverlayWideBack

include $(BUILD_PHONY_PACKAGE)
include $(CLEAR_VARS)

LOCAL_MODULE := frameworks-base-overlays-debug

include $(BUILD_PHONY_PACKAGE)
include $(call first-makefiles-under,$(LOCAL_PATH))
