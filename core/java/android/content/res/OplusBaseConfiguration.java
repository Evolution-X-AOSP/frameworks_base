package android.content.res;

import android.annotation.SuppressLint;
import android.annotation.Nullable;
import oplus.content.res.OplusExtraConfiguration;

public abstract class OplusBaseConfiguration {
    @SuppressLint({"InternalField", "MissingNullability"})
    public final OplusExtraConfiguration mOplusExtraConfiguration = null;

    @Nullable
    public OplusExtraConfiguration getOplusExtraConfiguration() {
        return this.mOplusExtraConfiguration;
    }
}
