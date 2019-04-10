package com.taka.muzei.imgboard;

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;

/**
 * Created by Kenneth on 7/28/2014.
 */
public class OptionDialogPreference extends DialogPreference {

    public OptionDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult){
        super.onDialogClosed(positiveResult);
        persistBoolean(positiveResult);
    }
}
