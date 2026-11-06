package com.ffc.banc888.fly;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class LauncherIconIntegrationTest {
    @Test public void installedIconRendersUploadedBearColors() throws Exception {
        final android.content.Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        final PackageManager pm = context.getPackageManager();
        final ApplicationInfo info = pm.getApplicationInfo(context.getPackageName(), 0);
        assertTrue("Launcher icon must be declared", info.icon != 0);
        Drawable icon = pm.getApplicationIcon(context.getPackageName());
        assertTrue("Icon drawable should exist", icon != null);
        Bitmap canvasBitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(canvasBitmap);
        icon.setBounds(0, 0, 256, 256);
        icon.draw(canvas);
        int white = 0;
        int black = 0;
        int red = 0;
        for (int y = 0; y < 256; y += 2) {
            for (int x = 0; x < 256; x += 2) {
                int pixel = canvasBitmap.getPixel(x, y);
                int r = Color.red(pixel), g = Color.green(pixel), b = Color.blue(pixel);
                if (r > 225 && g > 225 && b > 225) white++;
                if (r < 45 && g < 45 && b < 45) black++;
                if (r > 190 && g < 130 && b < 150) red++;
            }
        }
        assertTrue("White side of face not present", white > 1500);
        assertTrue("Black side of face not present", black > 1500);
        assertTrue("Red eye not present", red > 15);
    }
}
