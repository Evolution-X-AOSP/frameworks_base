package com.android.systemui.afterlife;

import com.android.systemui.R;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.AttributeSet;
import android.view.animation.PathInterpolator;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class LsWidget extends LinearLayout {
	private Context mContext;
    private ProgressBar mBatteryProgress;
    private TextView mBatteryStatus;
    private TextView mBatteryLevel;
    protected String chargingSpeed;
    protected String chargingStatus;
    protected int extraCurrent;
    protected int extraLevel;
    protected int extraStatus;
    private BroadcastReceiver mBatteryReceiver;
    private BroadcastReceiver mVolumeReceiver;
    private ProgressBar mVolumeProgress;
    private TextView mVolumeLevel;
    protected float temperature;
    AudioManager mAudioManager;

    public LsWidget(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        mContext = context;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        mBatteryReceiver = new BroadcastReceiver() {  
            @Override  
            public void onReceive(Context context, Intent intent) {  
                String str;
                extraStatus = intent.getIntExtra("status", 1);
                extraLevel = intent.getIntExtra("level", 0);
                extraCurrent = intent.getIntExtra("max_charging_current", -1) / 1000;
                temperature = intent.getIntExtra("temperature", -1) / 10;
                Resources resources = context.getResources();
                int i = extraStatus;
                if (i == 5 || extraLevel >= 100) {
                    chargingStatus = resources.getString(R.string.battery_info_status_full);
                } else if (i == 2) {
                    chargingStatus = resources.getString(R.string.battery_info_status_charging);
                } else if (i == 3) {
                    chargingStatus = resources.getString(R.string.battery_info_status_discharging);
                } else if (i == 4) {
                    chargingStatus = resources.getString(R.string.battery_info_status_not_charging);
                }
     
                if (extraStatus != 2 || extraLevel == 100) {
                    str = "";
                } else {
                    str = " • " + extraCurrent + "mA";
                }
                chargingSpeed = str;
                initBatteryStatus();
            }  
        };  
    
        mVolumeReceiver = new BroadcastReceiver() {  
        @Override  
        public void onReceive(Context context, Intent intent) {  
                initSoundManager();
            }  
        };  
        context.registerReceiver(mBatteryReceiver, new IntentFilter("android.intent.action.BATTERY_CHANGED"));
        context.registerReceiver(mVolumeReceiver, new IntentFilter("android.media.VOLUME_CHANGED_ACTION"));
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBatteryProgress = findViewById(R.id.battery_progressbar);
        mVolumeProgress = findViewById(R.id.volume_progressbar);
        mBatteryLevel =  findViewById(R.id.battery_level);
        mBatteryStatus = findViewById(R.id.battery_status);
        mVolumeLevel = findViewById(R.id.volume_level);
        initSoundManager();
    }

    protected void initBatteryStatus() {
        mBatteryProgress.setProgress(extraLevel);
        mBatteryProgress.setInterpolator(new PathInterpolator(0.33f, 0.11f, 0.2f, 1.0f));
        if (extraStatus == 2 && extraLevel != 100) {
            mBatteryProgress.setIndeterminate(true);
        } else {
            mBatteryProgress.setIndeterminate(false);
        }
        mBatteryLevel.setText(Integer.toString(extraLevel) + "%");
        mBatteryStatus.setText(chargingStatus + chargingSpeed);
    }

    protected void initSoundManager() {
    	int volLevel = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
        int maxVolLevel = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int volPercent = (int) (((float) volLevel /maxVolLevel) * 100);
        mVolumeProgress.setProgress(volPercent);
        TextView textView = mVolumeLevel;
        textView.setText(Integer.toString(mVolumeProgress.getProgress()) + "%");
    }
}
