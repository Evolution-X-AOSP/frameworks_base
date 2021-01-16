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
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.systemui.AutoReinflateContainer;
import com.android.systemui.Dependency;
import com.android.systemui.R;
import com.android.systemui.ambientmusic.AmbientIndicationInflateListener;
import com.android.systemui.statusbar.phone.DozeParameters;
import com.android.systemui.statusbar.NotificationLockscreenUserManager;
import com.android.systemui.statusbar.NotificationMediaManager;
import com.android.systemui.statusbar.phone.StatusBar;
import com.android.systemui.util.wakelock.SettableWakeLock;
import com.android.systemui.util.wakelock.WakeLock;

public class AmbientIndicationContainer extends AutoReinflateContainer implements
        NotificationMediaManager.MediaListener {

    public static final boolean DEBUG_AMBIENTMUSIC = false;

    private View mAmbientIndication;
    private boolean mDozing;
    private boolean mKeyguard;
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

    public AmbientIndicationContainer(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
        initDependencies();
        initializeMedia();
        mTrackInfoSeparator = getResources().getString(R.string.ambientmusic_songinfo);
        mAmbientMusicTicker = getAmbientMusicTickerStyle();
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
        mAmbientIndication.setVisibility(View.INVISIBLE);
        if (DEBUG_AMBIENTMUSIC) {
            Log.d("AmbientIndicationContainer", "hideIndication");
        }
    }

    public void initializeView(StatusBar statusBar, Handler handler) {
        mStatusBar = statusBar;
        addInflateListener(new AmbientIndicationInflateListener(this));
        mHandler = handler;
        mCustomSettingsObserver = new CustomSettingsObserver(mHandler);
        mCustomSettingsObserver.observe();
        mCustomSettingsObserver.update();
    }

    public void updateAmbientIndicationView(View view) {
        mAmbientIndication = findViewById(R.id.ambient_indication);
        mText = (TextView)findViewById(R.id.ambient_indication_text);
        if (getAmbientMusicTickerStyle() == 1) {
            boolean nowPlayingAvailable = mMediaManager.getNowPlayingTrack() != null;
            setIndication(nowPlayingAvailable);
        } else {
            hideIndication();
        }
        if (DEBUG_AMBIENTMUSIC) {
            Log.d("AmbientIndicationContainer", "updateAmbientIndicationView");
        }
    }

    private void initializeMedia() {
        mMediaHandler = new Handler();
        mMediaWakeLock = new SettableWakeLock(WakeLock.createPartial(mContext, "media"),
                "media");
        if (DEBUG_AMBIENTMUSIC) {
            Log.d("AmbientIndicationContainer", "initializeMedia");
        }
    }

    public void initDependencies() {
        mMediaManager = Dependency.get(NotificationMediaManager.class);
        mMediaManager.addCallback(this);
    }

    public void updateKeyguardState(boolean keyguard) {
        if (keyguard && (mInfoAvailable || mNpInfoAvailable)) {
            mText.setText(mInfoToSet);
            mLastInfo = mInfoToSet;
        } else {
            mText.setText(null);
            mAmbientIndication.setVisibility(View.INVISIBLE);
        }
        mKeyguard = keyguard;
    }

    public void updateDozingState(boolean dozing) {
        if (mDozing != dozing) {
            mDozing = dozing;
        }
        mAmbientIndication.setVisibility(shouldShow() ? View.VISIBLE : View.INVISIBLE);

        if (DEBUG_AMBIENTMUSIC) {
            Log.d("AmbientIndicationContainer", "updateDozingState: dozing=" + dozing + " shouldShow=" + shouldShow());
        }
    }

    private boolean isAod() {
        return DozeParameters.getInstance(mContext).getAlwaysOn() && mDozing;
    }

    private boolean shouldShow() {
        // if not dozing, show ambient music info only for Google Now Playing,
        // not for local media players if they are showing a lockscreen media notification
        final NotificationLockscreenUserManager lockscreenManager =
                mStatusBar.getNotificationLockscreenUserManager();
        boolean filtered = lockscreenManager.shouldHideNotifications(
                lockscreenManager.getCurrentUserId()) || lockscreenManager.shouldHideNotifications(
                        mMediaManager.getMediaNotificationKey());

        if (DEBUG_AMBIENTMUSIC) {
            Log.d("AmbientIndicationContainer", "shouldShow: mKeyguard=" + mKeyguard + " isAod=" + isAod());
        }
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

        if (!TextUtils.isEmpty(mMediaArtist)) {
            mInfoToSet = String.format(mTrackInfoSeparator, mMediaTitle.toString(), mMediaArtist.toString());
        } else if (!TextUtils.isEmpty(mMediaTitle)) {
            mInfoToSet = mMediaTitle.toString();
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
            mAmbientIndication.setVisibility(shouldShow() ? View.VISIBLE : View.INVISIBLE);
        } else {
            hideIndication();
        }

        if (DEBUG_AMBIENTMUSIC) {
            Log.d("AmbientIndicationContainer", "setIndication: nowPlaying=" + nowPlaying);
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
            if (DEBUG_AMBIENTMUSIC) {
                Log.d("AmbientIndicationContainer", "onMetadataOrStateChanged: Now Playing: track=" + mMediaTitle);
            }
        } else if (mShowMusicTicker && !TextUtils.isEmpty(mMediaTitle) && mMediaState == 3) {
            setIndication(false);
            if (DEBUG_AMBIENTMUSIC) {
                Log.d("AmbientIndicationContainer", "onMetadataOrStateChanged: Music Ticker: artist=" + mMediaArtist + "; title=" + mMediaTitle);
            }
        } else {
            // Make sure that track info is hidden when playback is paused or stopped
            hideIndication();
            if (DEBUG_AMBIENTMUSIC) {
                Log.d("AmbientIndicationContainer", "onMetadataOrStateChanged: hideIndication(); mShowMusicTicker = " + mShowMusicTicker);
            }           
        }
    }
}
