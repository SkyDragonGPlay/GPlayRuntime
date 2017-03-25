package com.skydragon.gplay.runtime.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PromptView extends LinearLayout {

    private LinearLayout mLinearLayout;
    private TextView mTextView;
    private Button mNegativeButton;
    private Button mPositiveButton;

    public PromptView(Context context) {
        super(context);
        this.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            this.setBackground(new CustomDrawable(context,CustomDrawable.TYPE_LAYOUYRROUND));
        }else {
            this.setBackgroundDrawable(new CustomDrawable(context,CustomDrawable.TYPE_LAYOUYRROUND));
        }
        this.setGravity(Gravity.CENTER_HORIZONTAL);
        this.setOrientation(LinearLayout.VERTICAL);

        mTextView = new TextView(context);
        mTextView.setText("提示");
        mTextView.setTextSize(AdaptationSize.getX(context, 10));
        mTextView.setMaxEms(15);
        LayoutParams layoutParam = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1);

        mTextView.setLayoutParams(layoutParam);
        mTextView.setTextColor(Color.argb(255,74,74,74));
        mTextView.setGravity(Gravity.CENTER);

        mLinearLayout = new LinearLayout(context);
        mLinearLayout.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mLinearLayout.setOrientation(LinearLayout.HORIZONTAL);

        mNegativeButton = new Button(context);
        mNegativeButton.setText("取消");
        mNegativeButton.setTextSize(AdaptationSize.getX(context, 12));
        LayoutParams layoutParams = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        layoutParams.setMargins((int)AdaptationSize.getX(context, 23), (int)AdaptationSize.getY(context, 25), (int)AdaptationSize.getX(context, 23), (int)AdaptationSize.getY(context, 25));
        mNegativeButton.setLayoutParams(layoutParams);
        mNegativeButton.setTextColor(Color.argb(255, 244,191,86));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mNegativeButton.setBackground(new CustomDrawable(context,CustomDrawable.TYPE_BUTTON_STROKE));
        }else {
            mNegativeButton.setBackgroundDrawable(new CustomDrawable(context,CustomDrawable.TYPE_BUTTON_STROKE));
        }
        mNegativeButton.setVisibility(View.GONE);

        mPositiveButton = new Button(context);
        mPositiveButton.setText("确定");
        mPositiveButton.setTextSize(AdaptationSize.getX(context, 12));
        mPositiveButton.setLayoutParams(layoutParams);
        mPositiveButton.setTextColor(Color.WHITE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            mPositiveButton.setBackground(new CustomDrawable(context,CustomDrawable.TYPE_BUTTON_FILL));
        }else {
            mPositiveButton.setBackgroundDrawable(new CustomDrawable(context,CustomDrawable.TYPE_BUTTON_FILL));
        }
        mPositiveButton.setVisibility(View.GONE);


        mLinearLayout.addView(mPositiveButton);
        mLinearLayout.addView(mNegativeButton);

        this.addView(mTextView);
        this.addView(mLinearLayout);
    }

    public TextView getTextView() {
        return mTextView;
    }

    public Button getNegativeButton() {
        return mNegativeButton;
    }

    public Button getPositiveButton() {
        return mPositiveButton;
    }

}
