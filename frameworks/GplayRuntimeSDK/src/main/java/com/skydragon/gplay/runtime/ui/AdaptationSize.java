package com.skydragon.gplay.runtime.ui;

import android.content.Context;
import android.graphics.Point;
import android.os.Build;
import android.view.Display;
import android.view.WindowManager;

class AdaptationSize {

    private static float sDisplayDensity;

    private static Point sScreenSize = new Point();

    private static void initScreenSizeAndDensityInfo(Context context) {
        if (sScreenSize.x <= 0 || sScreenSize.y <=0) {
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            sDisplayDensity = context.getResources().getDisplayMetrics().density;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
                display.getSize(sScreenSize);
            } else {
                sScreenSize.x = display.getWidth();
                sScreenSize.y = display.getHeight();
            }
        }
    }

    static float getX(Context context, int px) {
        initScreenSizeAndDensityInfo(context);

        if (sScreenSize.x > sScreenSize.y) {
            return sScreenSize.x / 480.0f * px / sDisplayDensity;
        } else {
            return sScreenSize.x / 320.0f * px / sDisplayDensity;
        }
    }


    static float getY(Context context, int py) {
        initScreenSizeAndDensityInfo(context);

        if (sScreenSize.x > sScreenSize.y) {
            return sScreenSize.y / 320.0f * py / sDisplayDensity;
        } else {
            return sScreenSize.y / 480.0f * py / sDisplayDensity;
        }
    }

    static int getGradientWidth(Context context, int px) {
        initScreenSizeAndDensityInfo(context);

        if (sScreenSize.x > sScreenSize.y) {
            return (int)(sScreenSize.x / 480.0f * px);
        } else {
            return (int)(sScreenSize.x / 320.0f * px);
        }
    }

    public static int getGradientHeight(Context context, int py) {
        initScreenSizeAndDensityInfo(context);

        if (sScreenSize.x > sScreenSize.y) {
            return (int)(sScreenSize.y / 320.0f * py);
        } else {
            return (int)(sScreenSize.y / 480.0f * py);
        }
    }
}
