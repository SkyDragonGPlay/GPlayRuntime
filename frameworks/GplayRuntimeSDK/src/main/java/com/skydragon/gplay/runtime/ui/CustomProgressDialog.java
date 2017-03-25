package com.skydragon.gplay.runtime.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.StyleSpan;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.NumberFormat;

public class CustomProgressDialog extends Dialog {

    private Context mContext;
    private ProgressView mProgressView;
    private TextView mProgressTextView;
    private ProgressBar mProgressBar;

    private Handler mViewUpdateHandler;
    private NumberFormat mProgressPercentFormat;

    private double mProgressMax;
    private double mProgressVal;

    public CustomProgressDialog(Context context, String message) {
        super(context);
        mContext = context;
        mProgressMax = 100;

        mProgressView = new ProgressView(context, message);
        mProgressTextView = mProgressView.getPercentTextView();
        mProgressBar = mProgressView.getProgressBar();

        mProgressPercentFormat = NumberFormat.getPercentInstance();
        mProgressPercentFormat.setMaximumFractionDigits(2);

        mViewUpdateHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 0:
                        mProgressBar.setProgress((int)mProgressVal);
                        break;
                    case 1:
                        mProgressBar.setMax((int)mProgressMax);
                    default:
                        break;
                }

                double percent = mProgressVal / mProgressMax;
                SpannableString tmp = new SpannableString(mProgressPercentFormat.format(percent));
                tmp.setSpan(new StyleSpan(android.graphics.Typeface.BOLD),
                        0, tmp.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                mProgressTextView.setText(tmp);
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new CustomDrawable(mContext, CustomDrawable.TYPE_LAYOUYRROUND));
            window.requestFeature(Window.FEATURE_NO_TITLE);
        }
        this.setCancelable(false);
        setContentView(mProgressView);
        initProgressPosition();

        super.onCreate(savedInstanceState);
    }

    private void initProgressPosition() {
        WindowManager.LayoutParams lp = this.getWindow().getAttributes();
        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            Point size = new Point();
            display.getSize(size);
            if (size.x > size.y) {
                lp.width = (int) (size.x / 2);
            } else {
                lp.width = (int) (size.x / 1.3);
            }
            lp.y = size.y / 3;
        } else {
            if (display.getWidth() > display.getHeight()) {
                lp.width = (int) (display.getWidth() / 2);
            } else {
                lp.width = (int) (display.getWidth() / 1.3);
            }
            lp.y = display.getHeight() / 3;
        }

        this.getWindow().setAttributes(lp);
    }

    public void setProgress(double value) {
        mProgressVal = value;
        mViewUpdateHandler.sendEmptyMessage(0);
    }

    public int getProgress() {
        return mProgressBar.getProgress();
    }

    public void setProgressMax(int max) {
        mProgressMax = max;
        mViewUpdateHandler.sendEmptyMessage(1);
    }

    public int getProgressMax() {
        return mProgressBar.getMax();
    }

    public void setProgressDrawable(Drawable d) {
        mProgressBar.setProgressDrawable(d);
    }

    public void setIndeterminateDrawable(Drawable d) {
        mProgressBar.setIndeterminateDrawable(d);
    }

    public void setIndeterminate(boolean indeterminate) {
        mProgressBar.setIndeterminate(indeterminate);
    }
}
