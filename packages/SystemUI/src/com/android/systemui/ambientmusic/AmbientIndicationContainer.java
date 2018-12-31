/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.ambientmusic;

import android.content.Context;
import android.database.ContentObserver;
import android.graphics.Color;
import android.media.MediaMetadata;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.airbnb.lottie.LottieAnimationView;

import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.ambientmusic.AmbientIndicationInflateListener;
import com.android.systemui.statusbar.KeyguardIndicationController;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.util.wakelock.SettableWakeLock;
import com.android.systemui.util.wakelock.WakeLock;

public class AmbientIndicationContainer extends AutoReinflateContainer implements
        NotificationMediaManager.MediaListener {

    private final int mFODmargin;
    private final int mKGmargin;
    private View mAmbientIndication;
    private LottieAnimationView mIcon;
    private boolean mDozing;
    private boolean mKeyguard;
    private boolean mVisible;
    private boolean mChargingIndicationChecked;
    private StatusBar mStatusBar;
    private TextView mText;
    private Context mContext;
    private Handler mHandler;
    private Handler mMediaHandler;
    private boolean mInfoAvailable;
    private String mInfoToSet;
    private String mLastInfo;
    private int mAmbientMusicTicker;

    private CustomSettingsObserver mCustomSettingsObserver;

    private CharSequence mMediaTitle;
    private CharSequence mMediaArtist;
    private String mTrackInfoSeparator;
    private boolean mNpInfoAvailable;

    protected NotificationMediaManager mMediaManager;
    protected MediaMetadata mMediaMetaData;

    private int mMediaState;
    private boolean mMediaIsVisible;
    private SettableWakeLock mMediaWakeLock;

    private KeyguardIndicationController mKeyguardIndicationController;

    public AmbientIndicationContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
        initDependencies();
        initializeMedia();
        mTrackInfoSeparator = getResources().getString(R.string.ambientmusic_songinfo);
        mAmbientMusicTicker = getAmbientMusicTickerStyle();
        mFODmargin = mContext.getResources().getDimensionPixelSize(
                R.dimen.keyguard_security_fod_view_margin);
        mKGmargin = mContext.getResources().getDimensionPixelSize(
                R.dimen.keyguard_charging_animation_margin);
    }

    private class CustomSettingsObserver extends ContentObserver {
        CustomSettingsObserver(Handler handler) {
            super(handler);
        }

        void observe() {
            mContext.getContentResolver().registerContentObserver(
                    Settings.System.getUriFor(
                    Settings.System.AMBIENT_MUSIC_TICKER),
                    false, this, UserHandle.USER_ALL);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri.equals(Settings.System.getUriFor(
                    Settings.System.AMBIENT_MUSIC_TICKER))) {
                mAmbientMusicTicker = getAmbientMusicTickerStyle();
            }
            update();
        }

        private void update() {
            updateAmbientIndicationView(AmbientIndicationContainer.this);
        }
    }

    private int getAmbientMusicTickerStyle() {
        return Settings.System.getIntForUser(mContext.getContentResolver(),
                    Settings.System.AMBIENT_MUSIC_TICKER, 1, UserHandle.USER_CURRENT);
    }

    private void hideIndication() {
        mInfoAvailable = false;
        mNpInfoAvailable = false;
        mText.setText(null);
        setVisibility(false, true);
    }

    public void initializeView(StatusBar statusBar, Handler handler, KeyguardIndicationController keyguardIndicationController) {
        mStatusBar = statusBar;
        mKeyguardIndicationController = keyguardIndicationController;
        addInflateListener(new AmbientIndicationInflateListener(this));
        mHandler = handler;
        mCustomSettingsObserver = new CustomSettingsObserver(mHandler);
        mCustomSettingsObserver.observe();
        mCustomSettingsObserver.update();
    }

    public void updateAmbientIndicationView(View view) {
        mAmbientIndication = findViewById(R.id.ambient_indication);
        mText = (TextView)findViewById(R.id.ambient_indication_text);
        mIcon = (LottieAnimationView)findViewById(R.id.ambient_indication_icon);
        if (getAmbientMusicTickerStyle() == 1) {
            boolean nowPlayingAvailable = mMediaManager.getNowPlayingTrack() != null;
            setIndication(nowPlayingAvailable);
        } else {
            hideIndication();
        }
    }

    private void initializeMedia() {
        mMediaHandler = new Handler();
        mMediaWakeLock = new SettableWakeLock(WakeLock.createPartial(mContext, "media"),
                "media");
    }

    public void initDependencies() {
        mMediaManager = Dependency.get(NotificationMediaManager.class);
        mMediaManager.addCallback(this);
    }

    private void updatePosition() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) this.getLayoutParams();
        if (hasInDisplayFingerprint()) {
            lp.setMargins(0, 0, 0, mFODmargin);
        } else if (isChargingIndicationVisible()) {
            if (!mChargingIndicationChecked) {
                mChargingIndicationChecked = true;
                lp.setMargins(0, 0, 0, mKGmargin);
            }
        } else {
            if (mChargingIndicationChecked) {
                mChargingIndicationChecked = false;
                lp.setMargins(0, 0, 0, 0);
            }
        }
        this.setLayoutParams(lp);
    }

    private boolean hasInDisplayFingerprint() {
        return mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_needCustomFODView);
    }

    private boolean isChargingIndicationVisible() {
        return mKeyguardIndicationController.isChargingIndicationVisible();
    }

    public View getTitleView() {
        return mText;
    }

    public void updateKeyguardState(boolean keyguard) {
        if (keyguard != mKeyguard) {
            mKeyguard = keyguard;
            if (keyguard && (mInfoAvailable || mNpInfoAvailable)) {
                mText.setText(mInfoToSet);
                mLastInfo = mInfoToSet;
            } else {
                mText.setText(null);
            }
            setVisibility(shouldShow(), true);
        }
        if (shouldShow()) {
            updatePosition();
        }
    }

    public void updateDozingState(boolean dozing) {
        if (mDozing != dozing) {
            mDozing = dozing;
            setVisibility(shouldShow(), true);
        }
        if (shouldShow()) {
            updatePosition();
        }
    }

    private void setVisibility(boolean shouldShow, boolean skipPosition) {
        if (mVisible != shouldShow) {
            mVisible = shouldShow;
            mAmbientIndication.setVisibility(shouldShow ? View.VISIBLE : View.INVISIBLE);
        }
        mIcon.setAnimation(R.raw.ambient_music_note);
        mIcon.playAnimation();

        if (!skipPosition && shouldShow) {
            updatePosition();
        }
    }

    private boolean isAod() {
        return DozeParameters.getInstance(mContext).getAlwaysOn() && mDozing;
    }

    public boolean shouldShow() {
        // if not dozing, show ambient music info only for Google Now Playing,
        // not for local media players if they are showing a lockscreen media notification
        final NotificationLockscreenUserManager lockscreenManager =
                mStatusBar.getNotificationLockscreenUserManager();
        boolean filtered = lockscreenManager.shouldHideNotifications(
                lockscreenManager.getCurrentUserId()) || lockscreenManager.shouldHideNotifications(
                        mMediaManager.getMediaNotificationKey());
        return (mKeyguard || isAod() || mDozing)
                && ((mDozing && (mInfoAvailable || mNpInfoAvailable))
                || (!mDozing && mNpInfoAvailable && !mInfoAvailable)
                || (!mDozing && mInfoAvailable && filtered));
    }

    public void setIndication(boolean nowPlaying) {
        // never override local music ticker but be sure to delete Now Playing info when needed
        mNpInfoAvailable = !(nowPlaying && (mMediaArtist == null || mMediaArtist != null && mMediaState != 3));
        if (nowPlaying && mInfoAvailable || mAmbientIndication == null) return;
        // make sure to show Now Playing info while local music state is paused
        if (nowPlaying && mMediaArtist != null && mMediaState != 3) {
            mMediaArtist = null;
            mMediaTitle = mMediaManager.getNowPlayingTrack();
        }

        mInfoToSet = null;
        String mTitle = null;
        if (!TextUtils.isEmpty(mMediaArtist)) {
            mTitle = String.format(mTrackInfoSeparator, "\"" + mMediaTitle.toString() + "\"", mMediaArtist.toString());
        } else if (!TextUtils.isEmpty(mMediaTitle)) {
            mTitle = mMediaTitle.toString();
        }
        if (mTitle != null) {
            if (mTitle.length() < 40) {
                mInfoToSet = mTitle;
            } else {
                mInfoToSet = shortenMediaTitle(mTitle);
            }
        }
        if (nowPlaying) {
            mNpInfoAvailable = mInfoToSet != null;
        } else {
            mInfoAvailable = mInfoToSet != null;
        }
        if (mInfoAvailable || mNpInfoAvailable) {
            boolean isAnotherTrack = (mInfoAvailable || mNpInfoAvailable)
                    && (TextUtils.isEmpty(mLastInfo) || (!TextUtils.isEmpty(mLastInfo)
                    && !mLastInfo.equals(mInfoToSet)));
            if (mKeyguard) {
                mLastInfo = mInfoToSet;
            }
        }
        if (mInfoToSet != null) {
            mText.setText(mInfoToSet);
            setVisibility(shouldShow(), false);
        } else {
            hideIndication();
        }
    }

    private String shortenMediaTitle(String input) {
        int cutPos = input.lastIndexOf("\"");
        if (cutPos > 25) { // only shorten the song title if it is too long
            String artist = input.substring(cutPos + 1, input.length());
            int artistLenght = 10;
            artistLenght = (artist.length() < artistLenght) ? artist.length() : artistLenght;
            cutPos = cutPos > 34 ? 30 - artistLenght : cutPos - artistLenght - 4;
            return input.substring(0, cutPos) + "...\"" + artist;
        } else { // otherwise the original string is returned
            return input;
        }
    }

    /**
     * Called whenever new media metadata is available.
     * @param metadata New metadata.
     */
    @Override
    public void onPrimaryMetadataOrStateChanged(MediaMetadata metadata,
            @PlaybackState.State int state) {
        synchronized (this) {
            boolean nextVisible = mMediaState == 3 || mMediaManager.getNowPlayingTrack() != null;
            if (mMediaHandler != null) {
                mMediaHandler.removeCallbacksAndMessages(null);
                if (mMediaIsVisible && !nextVisible) {
                    // We need to delay this event for a few millis when stopping to avoid jank in the
                    // animation. The media app might not send its update when buffering, and the slice
                    // would end up without a header for 0.5 second.
                    mMediaWakeLock.setAcquired(true);
                    mMediaHandler.postDelayed(() -> {
                        synchronized (this) {
                            updateMediaStateLocked(metadata, state);
                            mMediaWakeLock.setAcquired(false);
                        }
                    }, 2000);
                } else {
                    mMediaWakeLock.setAcquired(false);
                    updateMediaStateLocked(metadata, state);
                }
            }
        }
    }

    private void updateMediaStateLocked(MediaMetadata metadata, @PlaybackState.State int state) {
        CharSequence npTitle = mMediaManager.getNowPlayingTrack();
        boolean nowPlayingAvailable = npTitle != null;
        mMediaState = state;
        boolean nextVisible = mMediaState == 3 || nowPlayingAvailable;

        // Get track info from player media notification, if available
        CharSequence title = null;
        if (metadata != null) {
            title = metadata.getText(MediaMetadata.METADATA_KEY_TITLE);
            if (TextUtils.isEmpty(title)) {
                title = getContext().getResources().getString(R.string.music_controls_no_title);
            }
        }
        CharSequence artist = metadata == null ? null : metadata.getText(
                MediaMetadata.METADATA_KEY_ARTIST);

        // If Now playing is available, and there's no playing media notification, get Now Playing title
        title = nowPlayingAvailable ? npTitle : title;

        if (nextVisible == mMediaIsVisible && TextUtils.equals(title, mMediaTitle)
                && TextUtils.equals(artist, mMediaArtist)) {
            return;
        }
        mMediaTitle = title;
        mMediaArtist = artist;
        mMediaIsVisible = nextVisible;

        if (mMediaTitle == null && nowPlayingAvailable) {
            mMediaTitle = mMediaManager.getNowPlayingTrack();
            mMediaIsVisible = true;
            mMediaArtist = null;
        }
        boolean mShowMusicTicker = getAmbientMusicTickerStyle() == 1;
        if (mShowMusicTicker && nowPlayingAvailable) {
            setIndication(true);
        } else if (mShowMusicTicker && !TextUtils.isEmpty(mMediaTitle) && mMediaState == 3) {
            setIndication(false);
        } else {
            // Make sure that track info is hidden when playback is paused or stopped
            hideIndication();
        }
    }
}
