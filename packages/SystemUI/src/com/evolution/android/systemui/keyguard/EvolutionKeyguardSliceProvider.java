package com.evolution.android.systemui.keyguard;

import android.app.PendingIntent;
import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Trace;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.graphics.drawable.IconCompat;
import androidx.slice.Slice;
import androidx.slice.builders.ListBuilder;
import androidx.slice.builders.SliceAction;

import com.android.systemui.R;
import com.android.systemui.dagger.qualifiers.Background;
import com.android.systemui.keyguard.KeyguardSliceProvider;
import com.google.android.systemui.smartspace.SmartSpaceCard;
import com.google.android.systemui.smartspace.SmartSpaceController;
import com.google.android.systemui.smartspace.SmartSpaceData;
import com.google.android.systemui.smartspace.SmartSpaceUpdateListener;

import java.util.concurrent.Executor;

import javax.inject.Inject;

public class EvolutionKeyguardSliceProvider extends KeyguardSliceProvider implements SmartSpaceUpdateListener {
    private static final String TAG = "EvolutionKeyguardSliceProvider";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);
    private static final Uri sWeatherUri = Uri.parse("content://com.android.systemui.keyguard/smartSpace/weather");
    private static final Uri sCalendarUri = Uri.parse("content://com.android.systemui.keyguard/smartSpace/calendar");

    @Inject
    public SmartSpaceController mSmartSpaceController;

    @Inject
    @Background
    public Executor mBackgroundExecutor;

    private SmartSpaceData mSmartSpaceData;
    private boolean mHideSensitiveContent = false;
    private boolean mHideWorkContent = true;

    @Override // com.android.systemui.keyguard.KeyguardSliceProvider, androidx.slice.SliceProvider
    public boolean onCreateSliceProvider() {
        if (!super.onCreateSliceProvider()) {
            return false;
        }
        mSmartSpaceData = new SmartSpaceData();
        mSmartSpaceController.addListener(this);
        return true;
    }

    @Override // com.android.systemui.keyguard.KeyguardSliceProvider
    public void onDestroy() {
        mSmartSpaceController.removeListener(this);
        super.onDestroy();
    }

    @Override // com.android.systemui.keyguard.KeyguardSliceProvider, androidx.slice.SliceProvider
    public Slice onBindSlice(Uri uri) {
        Trace.beginSection(TAG + "#onBindSlice");
        ListBuilder listBuilder = new ListBuilder(getContext(), mSliceUri, -1);
        synchronized (this) {
            SmartSpaceCard currentCard = mSmartSpaceData.getCurrentCard();
            boolean show = false;
            if (currentCard != null && !currentCard.isExpired() && !TextUtils.isEmpty(currentCard.getTitle())) {
                final boolean isSensitive = currentCard.isSensitive();
                final boolean showSensitiveContent = isSensitive &&
                    !mHideSensitiveContent && !currentCard.isWorkProfile();
                final boolean showWorkContent = isSensitive &&
                    !mHideWorkContent && currentCard.isWorkProfile();
                if (!isSensitive || showSensitiveContent || showWorkContent) {
                    if (needsMediaLocked()) {
                        addMediaLocked(listBuilder);
                    } else {
                        listBuilder.addRow(new ListBuilder.RowBuilder(mDateUri).setTitle(getFormattedDateLocked()));
                    }
                    addWeather(listBuilder);
                    addNextAlarmLocked(listBuilder);
                    addZenModeLocked(listBuilder);
                    addPrimaryActionLocked(listBuilder);
                    final Slice slice = listBuilder.build();
                    if (DEBUG) Log.d(TAG, "Binding slice: " + slice);
                    Trace.endSection();
                    return slice;
                }
            }
            IconCompat iconCompat = null;
            final Bitmap icon = currentCard.getIcon();
            if (icon != null) {
                iconCompat = IconCompat.createWithBitmap(icon);
            }
            final PendingIntent pendingIntent = currentCard.getPendingIntent();
            SliceAction sliceAction = null;
            final ListBuilder.HeaderBuilder title = new ListBuilder.HeaderBuilder(mHeaderUri).setTitle(currentCard.getFormattedTitle());
            if (iconCompat != null && pendingIntent != null) {
                sliceAction = SliceAction.create(pendingIntent, iconCompat, 1, currentCard.getTitle());
                title.setPrimaryAction(sliceAction);
            }
            listBuilder.setHeader(title);
            final String subtitle = currentCard.getSubtitle();
            if (subtitle != null) {
                final ListBuilder.RowBuilder title2 = new ListBuilder.RowBuilder(sCalendarUri).setTitle(subtitle);
                if (iconCompat != null) {
                    title2.addEndItem(iconCompat, 1);
                }
                if (sliceAction != null) {
                    title2.setPrimaryAction(sliceAction);
                }
                listBuilder.addRow(title2);
            }
            addZenModeLocked(listBuilder);
            addPrimaryActionLocked(listBuilder);
            Trace.endSection();
            return listBuilder.build();
        }
    }

    private void addWeather(ListBuilder listBuilder) {
        final SmartSpaceCard weatherCard = mSmartSpaceData.getWeatherCard();
        if (weatherCard == null || weatherCard.isExpired()) return;
        final ListBuilder.RowBuilder title = new ListBuilder.RowBuilder(sWeatherUri).setTitle(weatherCard.getTitle());
        final Bitmap icon = weatherCard.getIcon();
        if (icon != null) {
            final IconCompat createWithBitmap = IconCompat.createWithBitmap(icon);
            createWithBitmap.setTintMode(PorterDuff.Mode.DST);
            title.addEndItem(createWithBitmap, 1);
        }
        listBuilder.addRow(title);
    }

    @Override // com.google.android.systemui.smartspace.SmartSpaceUpdateListener
    public void onSensitiveModeChanged(boolean hideSensitiveContent, boolean hideWorkContent) {
        boolean changed = true;
        boolean hideSensitiveChanged = false;
        synchronized (this) {
            if (mHideSensitiveContent != hideSensitiveContent) {
                mHideSensitiveContent = hideSensitiveContent;
                if (DEBUG) Log.d(TAG, "Public mode changed, hide data: " + hideSensitiveContent);
                hideSensitiveChanged = true;
            }
            if (mHideWorkContent != hideWorkContent) {
                mHideWorkContent = hideWorkContent;
                if (DEBUG) Log.d(TAG, "Public work mode changed, hide data: " + hideWorkContent);
            } else {
                changed = hideSensitiveChanged;
            }
        }
        if (changed) {
            notifyChange();
        }
    }

    @Override // com.google.android.systemui.smartspace.SmartSpaceUpdateListener
    public void onSmartSpaceUpdated(SmartSpaceData smartSpaceData) {
        synchronized (this) {
            mSmartSpaceData = smartSpaceData;
        }
        final SmartSpaceCard weatherCard = smartSpaceData.getWeatherCard();
        if (weatherCard == null || weatherCard.getIcon() == null || weatherCard.isIconProcessed()) {
            notifyChange();
            return;
        }
        weatherCard.setIconProcessed(true);
        mBackgroundExecutor.execute(() -> {
            final float blurRadius = getContext().getResources()
                .getDimension(R.dimen.smartspace_icon_shadow);
            final Bitmap processedBitmap = applyShadow(weatherCard.getIcon(), blurRadius);
            synchronized (this) {
                weatherCard.setIcon(processedBitmap);
                notifyChange();
            }
        });
    }

    @Override // com.android.systemui.keyguard.KeyguardSliceProvider
    protected void updateClockLocked() {
        notifyChange();
    }

    private Bitmap applyShadow(Bitmap bitmap, float blurRadius) {
        final BlurMaskFilter blurMaskFilter = new BlurMaskFilter(blurRadius, BlurMaskFilter.Blur.NORMAL);
        final Paint paint = new Paint();
        paint.setMaskFilter(blurMaskFilter);
        final int[] alphaArray = new int[2];
        final Bitmap extractAlpha = bitmap.extractAlpha(paint, alphaArray);
        final Bitmap createBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(createBitmap);
        final Paint paint2 = new Paint();
        paint2.setAlpha(70);
        canvas.drawBitmap(extractAlpha, (float) alphaArray[0], (float) alphaArray[1] + (blurRadius / 2f), paint2);
        extractAlpha.recycle();
        paint2.setAlpha(255);
        canvas.drawBitmap(bitmap, 0f, 0f, paint2);
        return createBitmap;
    }
}
