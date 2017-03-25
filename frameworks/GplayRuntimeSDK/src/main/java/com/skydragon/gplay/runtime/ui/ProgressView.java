package com.skydragon.gplay.runtime.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ClipDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

public class ProgressView extends LinearLayout {
    private TextView mMessageView;
    private ProgressBar mProgressBar;
    private TextView mPercent;

    public ProgressView(Context context, String message) {
        super(context);

        this.setGravity(Gravity.CENTER_HORIZONTAL);
        this.setOrientation(LinearLayout.VERTICAL);
        this.setLayoutParams(new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, 3));

        mMessageView = new TextView(context);
        mMessageView.setText(message);
        mMessageView.setTextSize(AdaptationSize.getX(context, 8));
        LayoutParams layoutParam = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1);
        layoutParam.setMargins(0, (int) AdaptationSize.getY(context, 12),
                0, 0);
        mMessageView.setLayoutParams(layoutParam);
        mMessageView.setTextColor(Color.argb(255,74,74,74));
        mMessageView.setGravity(Gravity.CENTER_HORIZONTAL);

        mPercent = new TextView(context);
        mPercent.setText("0%");
        mPercent.setLayoutParams(layoutParam);
        mPercent.setTextSize(AdaptationSize.getX(context, 8));
        mPercent.setTextColor(Color.argb(255,74,74,74));
        mPercent.setGravity(Gravity.CENTER_HORIZONTAL);

        LayoutParams layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, 1);
        layoutParams.setMargins((int) AdaptationSize.getX(context, 25), (int) AdaptationSize.getY(context, 12),
                (int) AdaptationSize.getX(context, 25), (int) AdaptationSize.getY(context, 20));
        mProgressBar = new ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal);
        mProgressBar.setLayoutParams(layoutParams);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mProgressBar.setBackground(new CustomDrawable(context, CustomDrawable.TYPE_PROGRESS_BACKGROUND));
        } else {
            mProgressBar.setBackgroundDrawable(new CustomDrawable(context, CustomDrawable.TYPE_PROGRESS_BACKGROUND));
        }
        CustomDrawable customDrawable = new CustomDrawable(context, CustomDrawable.TYPE_PROGRESS);
        ClipDrawable d = new ClipDrawable(customDrawable, Gravity.START, ClipDrawable.HORIZONTAL);
        mProgressBar.setProgressDrawable(d);

        this.addView(mMessageView);
        this.addView(mPercent);
        this.addView(mProgressBar);
    }

    public TextView getMessageView() {
        return mMessageView;
    }

    public TextView getPercentTextView() {
        return mPercent;
    }

    public ProgressBar getProgressBar() {
        return mProgressBar;
    }

}
