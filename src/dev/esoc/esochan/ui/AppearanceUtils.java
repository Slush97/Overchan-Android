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
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.view.ActionMode;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.TextView;

public class AppearanceUtils {
    private AppearanceUtils(){}
    
    public static void callWhenLoaded(final View view, final Runnable runnable) {
        view.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                runnable.run();
            }
        });
    }
    
    public static Point getDisplaySize(Display display) {
        Point size = new Point();
        display.getSize(size);
        return size;
    }

    public static boolean hasAccessStorage(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        if (activity.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[] { android.Manifest.permission.WRITE_EXTERNAL_STORAGE }, 0);
            return false;
        } else {
            return true;
        }
    }

    public static interface CustomSelectionActionModeCallback {
        void onCreate();
        void onClick();
        void onDestroy();
    }

    public static void setCustomSelectionActionModeMenuCallback(TextView textView, final int titleRes, final Drawable icon,
            final CustomSelectionActionModeCallback callback) {
        textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                try {
                    callback.onCreate();
                    MenuItem item = menu.add(Menu.NONE, 1, Menu.FIRST, titleRes).setIcon(icon);
                    item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                    menu.removeItem(android.R.id.selectAll);
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return false;
            }
            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == 1) {
                    callback.onClick();
                    mode.finish();
                    return true;
                }
                return false;
            }
            @Override
            public void onDestroyActionMode(ActionMode mode) {
                callback.onDestroy();
            }
        });
    }
}
