/*
 * esochan (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.esoc.esochan.ui;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Point;
import android.view.Display;
import android.view.View;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ImageView;
import android.widget.TextView;

public class CompatibilityUtils {

    public static void getDisplaySize(Display display, Point outSize) {
        display.getSize(outSize);
    }

    public static void removeOnGlobalLayoutListener(View view, OnGlobalLayoutListener onGlobalLayoutListener) {
        view.getViewTreeObserver().removeOnGlobalLayoutListener(onGlobalLayoutListener);
    }

    public static void setImageAlpha(ImageView imageView, int alphaValue) {
        imageView.setImageAlpha(alphaValue);
    }

    public static int getColor(Resources resources, int id) {
        return resources.getColor(id, null);
    }

    public static ColorStateList getColorStateList(Resources resources, int id) {
        return resources.getColorStateList(id, null);
    }

    public static void setTextAppearance(TextView textView, int resId) {
        textView.setTextAppearance(resId);
    }

    public static boolean hasAccessStorage(Activity activity) {
        return CompatibilityImpl.hasAccessStorage(activity);
    }
}
