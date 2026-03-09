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

import java.io.File;

import dev.esoc.esochan.R;
import android.Manifest;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.preference.Preference;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewStub;
import android.view.Window;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.VideoView;

/**
 * Класс, содержащий вызовы методов из новых API, недоступных в ранних версиях Android
 * @author miku-nyan
 *
 */
public class CompatibilityImpl {
    private CompatibilityImpl() {}
    
    public static void setIcon(Preference preference, int iconResId) {
        preference.setIcon(iconResId);
    }
    
    public static void setIcon(Preference preference, Drawable icon) {
        preference.setIcon(icon);
    }
    
    public static void activeActionBar(Activity activity) {
        activity.getActionBar().setDisplayHomeAsUpEnabled(true);
        setHomeButtonEnabledTrue(activity.getActionBar());
    }
    
    private static void setHomeButtonEnabledTrue(ActionBar actionBar) {
        actionBar.setHomeButtonEnabled(true);
    }
    
    public static void setShowAsActionIfRoom(MenuItem item) {
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
    }
    
    public static void setShowAsActionAlways(MenuItem item) {
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }
    
    public static void setActionBarNoIcon(Activity activity) {
        ActionBar actionBar = activity.getActionBar();
        if (actionBar != null) actionBar.setDisplayShowHomeEnabled(false);
    }
    
    public static boolean hideActionBar(Activity activity) {
        ActionBar actionBar = activity.getActionBar();
        if (actionBar == null || !actionBar.isShowing()) return false;
        actionBar.hide();
        return true;
    }
    
    public static boolean showActionBar(Activity activity) {
        ActionBar actionBar = activity.getActionBar();
        if (actionBar == null || actionBar.isShowing()) return false;
        actionBar.show();
        return true;
    }
    
    public static void setActionBarCustomFavicon(Activity activity, Drawable icon) {
    }

    public static void setActionBarDefaultIcon(Activity activity) {
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
                    setShowAsActionAlways(menu.add(Menu.NONE, 1, Menu.FIRST, titleRes).setIcon(icon));
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
    
    public static void setLayoutInflater(ViewStub view, LayoutInflater inflater) {
        view.setLayoutInflater(inflater);
    }
    
    public static void clearCookies(CookieManager cookieManager) {
        cookieManager.removeAllCookies(null);
    }
    
    public static void setDimAmount(Window window, float f) {
        window.setDimAmount(f);
    }
    
    @SuppressWarnings("deprecation")
    public static File getDefaultDownloadDir() {
        // TODO: Migrate DownloadingService to MediaStore API for proper scoped storage support
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }
    
    public static void setScrollbarFadingEnabled(WebView webView, boolean fadeScrollbars) {
        webView.setScrollbarFadingEnabled(fadeScrollbars);
    }
    
    @SuppressWarnings("deprecation")
    public static void setDefaultZoomFAR(WebSettings settings) {
        settings.setDefaultZoom(WebSettings.ZoomDensity.FAR);
    }
    
    @SuppressWarnings("deprecation")
    public static void setDefaultZoomCLOSE(WebSettings settings) {
        settings.setDefaultZoom(WebSettings.ZoomDensity.CLOSE);
    }
    
    @SuppressWarnings("deprecation")
    public static void setDefaultZoomMEDIUM(WebSettings settings) {
        settings.setDefaultZoom(WebSettings.ZoomDensity.MEDIUM);
    }
    
    public static void setLoadWithOverviewMode(WebSettings settings, boolean overview) {
        settings.setLoadWithOverviewMode(overview);
    }
    
    public static void setBlockNetworkLoads(WebSettings settings, boolean flag) {
        settings.setBlockNetworkLoads(flag);
    }
    
    public static void setVideoViewZOrderOnTop(VideoView videoView) {
        videoView.setZOrderOnTop(true);
    }
    
    @TargetApi(Build.VERSION_CODES.M)
    public static int getColor(Resources resources, int id) {
        return resources.getColor(id, null);
    }
    
    @TargetApi(Build.VERSION_CODES.M)
    public static ColorStateList getColorStateList(Resources resources, int id) {
        return resources.getColorStateList(id, null);
    }
    
    @TargetApi(Build.VERSION_CODES.M)
    public static void setTextAppearance(TextView textView, int resId) {
        textView.setTextAppearance(resId);
    }
    
    @TargetApi(Build.VERSION_CODES.M)
    public static boolean hasAccessStorage(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        if (activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(new String[] { Manifest.permission.WRITE_EXTERNAL_STORAGE }, 0);
            return false;
        } else {
            return true;
        }
    }
    
}
