package com.taka.muzei.imgboard;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceViewHolder;

public class EditTextLongClickPreference extends EditTextPreference {
    private View.OnLongClickListener onLongClickListener;

    public EditTextLongClickPreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public EditTextLongClickPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public EditTextLongClickPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public EditTextLongClickPreference(Context context) {
        super(context);
    }

    public void setOnLongClickListener(View.OnLongClickListener listener) {
        onLongClickListener = listener;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        holder.itemView.setOnLongClickListener(v -> {
            if(null != onLongClickListener)
                return onLongClickListener.onLongClick(v);
            return false;
        });

    }
}
