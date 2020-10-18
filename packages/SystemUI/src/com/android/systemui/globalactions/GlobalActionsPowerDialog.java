/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.systemui.globalactions;

import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.annotation.NonNull;
import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;

import com.android.systemui.MultiListLayout;
import com.android.systemui.MultiListLayout.MultiListAdapter;
import com.android.systemui.R;

/**
 * Creates a customized Dialog for displaying the Shut Down and Restart actions.
 */
public class GlobalActionsPowerDialog {

    /**
     * Create a dialog for displaying Shut Down and Restart actions.
     */
    public static Dialog create(@NonNull Context context, MultiListAdapter adapter) {
        final ViewGroup view = (ViewGroup) LayoutInflater.from(context).inflate(
                R.layout.global_actions_grid_lite, null);

        final MultiListLayout multiListLayout = view.findViewById(R.id.global_actions_view);
        multiListLayout.setAdapter(adapter);

        final View overflowButton = view.findViewById(R.id.global_actions_overflow_button);
        if (overflowButton != null) {
            overflowButton.setVisibility(View.GONE);
            final LinearLayout.LayoutParams params =
                (LinearLayout.LayoutParams) multiListLayout.getLayoutParams();
            params.setMarginEnd(context.getResources().getDimensionPixelSize(
                    R.dimen.global_actions_side_margin));
            multiListLayout.setLayoutParams(params);
        }

        final Dialog dialog = new Dialog(context) {
            @Override
            protected void onStart() {
                super.onStart();
                multiListLayout.updateList();
            }

            @Override
            public void show() {
                super.show();
                view.setOnApplyWindowInsetsListener((v, windowInsets) -> {
                    view.setPadding(
                        windowInsets.getStableInsetLeft(),
                        windowInsets.getStableInsetTop(),
                        windowInsets.getStableInsetRight(),
                        windowInsets.getStableInsetBottom()
                    );
                    return WindowInsets.CONSUMED;
                });
            }
        };

        final Window window = dialog.getWindow();
        window.setLayout(WRAP_CONTENT, WRAP_CONTENT);
        window.setType(LayoutParams.TYPE_VOLUME_OVERLAY);
        window.addFlags(
            LayoutParams.FLAG_ALT_FOCUSABLE_IM |
            LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            LayoutParams.FLAG_HARDWARE_ACCELERATED
        );

        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        return dialog;
    }
}