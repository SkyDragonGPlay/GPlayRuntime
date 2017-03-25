package com.skydragon.gplay.runtime.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class PromptDialog extends Dialog {

    private Context mContext;
    private PromptView mPromtView;
    private TextView mTextView;
    private Button mNegativeButton;
    private Button mPositiveButton;

    public PromptDialog(Context context) {
        super(context);
        mContext = context;
        mPromtView = new PromptView(mContext);
        mTextView = mPromtView.getTextView();
        mNegativeButton = mPromtView.getNegativeButton();
        mPositiveButton = mPromtView.getPositiveButton();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new CustomDrawable(mContext, CustomDrawable.TYPE_LAYOUYRROUND));
            window.requestFeature(Window.FEATURE_NO_TITLE);
        }
        this.setCancelable(false);
        setContentView(mPromtView);
        setDialogPositon();

        super.onCreate(savedInstanceState);
    }

    public void setPositiveButton(String text, View.OnClickListener onClickListener) {
        mPositiveButton.setVisibility(View.VISIBLE);
        mPositiveButton.setText(text);
        mPositiveButton.setOnClickListener(onClickListener);
    }

    public void setNegativeButton(String text, View.OnClickListener onClickListener) {
        mNegativeButton.setVisibility(View.VISIBLE);
        mNegativeButton.setText(text);
        mNegativeButton.setOnClickListener(onClickListener);
    }

    private void setDialogPositon() {
        WindowManager.LayoutParams lp = this.getWindow().getAttributes();
        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            Point size = new Point();
            display.getSize(size);
            if (size.x > size.y) {
                lp.width = (int) (size.x / 2);
                lp.height = (int) (size.y / 2);
            } else {
                lp.width = (int) (size.x / 1.3);
                lp.height = (int) (size.y / 2.8);
            }
        } else {
            if (display.getWidth() > display.getHeight()) {
                lp.width = (int) (display.getWidth() / 2);
                lp.height = display.getHeight() / 2;
            } else {
                lp.width = (int) (display.getWidth() / 1.3);
                lp.height = (int) (display.getHeight() / 2.8);
            }
        }

        this.getWindow().setAttributes(lp);
    }

    public void setMessage(String message) {
        mTextView.setText(message);
    }

}
