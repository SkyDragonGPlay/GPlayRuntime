package com.skydragon.gplay.runtime.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

class CustomDrawable extends Drawable {

    private Context mContext;
    private Paint mPaint;
    private RectF rectF;
    private int mType;

    static final int TYPE_LAYOUYRROUND = 0;
    static final int TYPE_PROGRESS = 1;
    static final int TYPE_PROGRESS_BACKGROUND = 2;
    static final int TYPE_BUTTON_FILL = 3;
    static final int TYPE_BUTTON_STROKE = 4;

    CustomDrawable(Context context, int type) {
        mContext = context;
        mType = type;
        mPaint = new Paint();
        mPaint.setAntiAlias(true);
        setPaint();
    }

    private void setPaint() {
        switch (mType) {
            case TYPE_LAYOUYRROUND:
                mPaint.setColor(Color.WHITE);
                mPaint.setStyle(Paint.Style.FILL);
                break;
            case TYPE_PROGRESS:
                mPaint.setColor(Color.argb(255, 244,191,86));
                mPaint.setStyle(Paint.Style.FILL);

                LinearGradient linearGradient = new LinearGradient(0, 0, AdaptationSize.getGradientWidth(mContext, 200), 0, Color.argb(255, 244, 180, 56), Color.argb(255, 0, 255, 255), Shader.TileMode.CLAMP);
                mPaint.setShader(linearGradient);
                break;
            case TYPE_PROGRESS_BACKGROUND:
                mPaint.setColor(Color.argb(255, 180, 180, 180));
                mPaint.setStyle(Paint.Style.FILL);
                break;
            case TYPE_BUTTON_FILL:
                mPaint.setColor(Color.argb(255, 244,191,86));
                mPaint.setStyle(Paint.Style.FILL);
                break;
            case TYPE_BUTTON_STROKE:
                mPaint.setColor(Color.argb(255, 244,191,86));
                mPaint.setStyle(Paint.Style.STROKE);
                break;
            default:
                break;
        }
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, bottom);
        rectF = new RectF(left, top, right, bottom);
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawRoundRect(rectF, AdaptationSize.getX(mContext, 8), AdaptationSize.getX(mContext, 8), mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        mPaint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
