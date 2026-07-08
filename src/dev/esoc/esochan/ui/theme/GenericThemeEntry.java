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

package dev.esoc.esochan.ui.theme;

import java.util.Locale;
import java.util.Iterator;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.SparseIntArray;
import dev.esoc.esochan.R;
import dev.esoc.esochan.common.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class GenericThemeEntry implements Parcelable {
    private static final int BASE_THEME_LIGHT = R.style.Theme_Futaba;
    private static final int BASE_THEME_DARK = R.style.Theme_Neutron;
    
    private static GenericThemeEntry cachedInstance = null;
    private static String cachedJsonString = null;
    
    public final int themeId;
    public final int fontSizeStyleId;
    public final SparseIntArray customAttrs;
    
    private GenericThemeEntry(int themeId, int fontSizeStyleId, SparseIntArray customAttrs) {
        this.themeId = themeId;
        this.fontSizeStyleId = fontSizeStyleId;
        this.customAttrs = customAttrs;
    }
    
    private GenericThemeEntry(Parcel in) {
        this.themeId = in.readInt();
        this.fontSizeStyleId = in.readInt();
        int n = in.readInt();
        if (n == -1) {
            this.customAttrs = null;
        } else {
            SparseIntArray attrs = new SparseIntArray();
            for (int i=0; i<n; ++i) {
                int key = in.readInt();
                int value = in.readInt();
                attrs.put(key, value);
            }
            this.customAttrs = attrs;
        }
    }
    
    public static final Parcelable.Creator<GenericThemeEntry> CREATOR = new Parcelable.Creator<GenericThemeEntry>() {
        @Override
        public GenericThemeEntry createFromParcel(Parcel source) {
            return new GenericThemeEntry(source);
        }
        
        @Override
        public GenericThemeEntry[] newArray(int size) {
            return new GenericThemeEntry[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(themeId);
        dest.writeInt(fontSizeStyleId);
        if (customAttrs == null) {
            dest.writeInt(-1);
        } else {
            int n = customAttrs.size();
            dest.writeInt(n);
            for (int i=0; i<n; ++i) {
                dest.writeInt(customAttrs.keyAt(i));
                dest.writeInt(customAttrs.valueAt(i));
            }
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (o instanceof GenericThemeEntry) {
            GenericThemeEntry other = (GenericThemeEntry) o;
            return other.themeId == this.themeId &&
                    other.fontSizeStyleId == this.fontSizeStyleId &&
                    sparseIntArrayEquals(other.customAttrs, this.customAttrs); 
        } else {
            return false;
        }
    }
    
    public static GenericThemeEntry standartTheme(int themeId, int fontSizeStyleId) {
        return new GenericThemeEntry(themeId, fontSizeStyleId, null);
    }
    
    public static GenericThemeEntry customTheme(String jsonData, int fontSizeStyleId) {
        GenericThemeEntry cached = cachedInstance;
        if (cached != null && cached.fontSizeStyleId == fontSizeStyleId && jsonData.equals(cachedJsonString)) return cached;
        
        SparseIntArray customAttrs = new SparseIntArray();
        int themeId = parseTheme(jsonData, customAttrs);
        GenericThemeEntry theme = new GenericThemeEntry(themeId, fontSizeStyleId, customAttrs);
        cachedInstance = theme;
        cachedJsonString = jsonData;
        return theme;
    }
    
    public void setTo(Context context, int... applyStyles) {
        setBaseStyle(context, themeId, fontSizeStyleId);
        if (customAttrs != null) {
            Resources.Theme theme = context.getTheme();
            if (themeId == BASE_THEME_LIGHT) theme.applyStyle(R.style.Custom_Theme_Light, true);
            else if (themeId == BASE_THEME_DARK) theme.applyStyle(R.style.Custom_Theme_Dark, true);
        }
        if (applyStyles != null) {
            Resources.Theme theme = context.getTheme();
            for (int i : applyStyles) {
                theme.applyStyle(i, true);
            }
        }
        CustomThemeHelper.setCustomTheme(context, customAttrs);
    }
    
    public void setToPreferencesActivity(Context context) {
        setTo(context);
    }
    
    private static void setBaseStyle(Context context, int themeId, int fontSizeStyleId) {
        context.setTheme(themeId);
        context.getTheme().applyStyle(fontSizeStyleId, true);
    }
    
    public String toJsonString() {
        if (customAttrs == null) throw new IllegalStateException("this is not a custom theme");
        JSONObject theme = new JSONObject();
        putThemeValue(theme, "baseTheme", themeId == BASE_THEME_DARK ? "dark" : "light");
        for (int i=0, size=customAttrs.size(); i<size; ++i) {
            switch (customAttrs.keyAt(i)) {
                case android.R.attr.textColorPrimary: putThemeValue(theme, "textColorPrimary", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.materialPrimary: putThemeValue(theme, "materialPrimary", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.materialPrimaryDark: putThemeValue(theme, "materialPrimaryDark", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.materialNavigationBar: putThemeValue(theme, "materialNavigationBar", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.activityRootBackground: putThemeValue(theme, "activityRootBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.sidebarBackground: putThemeValue(theme, "sidebarBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.sidebarSelectedItem: putThemeValue(theme, "sidebarSelectedItem", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.listSeparatorBackground: putThemeValue(theme, "listSeparatorBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postUnreadOverlay: putThemeValue(theme, "postUnreadOverlay", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postBackground: putThemeValue(theme, "postBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postForeground: putThemeValue(theme, "postForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postIndexForeground: putThemeValue(theme, "postIndexForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postIndexOverBumpLimit: putThemeValue(theme, "postIndexOverBumpLimit", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postNumberForeground: putThemeValue(theme, "postNumberForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postNameForeground: putThemeValue(theme, "postNameForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postOpForeground: putThemeValue(theme, "postOpForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postSageForeground: putThemeValue(theme, "postSageForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postTripForeground: putThemeValue(theme, "postTripForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postTitleForeground: putThemeValue(theme, "postTitleForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.postQuoteForeground: putThemeValue(theme, "postQuoteForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.spoilerForeground: putThemeValue(theme, "spoilerForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.spoilerBackground: putThemeValue(theme, "spoilerBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.urlLinkForeground: putThemeValue(theme, "urlLinkForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.refererForeground: putThemeValue(theme, "refererForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.itemInfoForeground: putThemeValue(theme, "itemInfoForeground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.searchHighlightBackground: putThemeValue(theme, "searchHighlightBackground", colorToString(customAttrs.valueAt(i))); break;
                case R.attr.subscriptionBackground: putThemeValue(theme, "subscriptionBackground", colorToString(customAttrs.valueAt(i))); break;
                default: Logger.e("TAG", "unknown attribute: " + customAttrs.keyAt(i));
            }
        }
        return theme.toString();
    }
    
    private static int parseTheme(String customThemeJson, SparseIntArray attrs) {
        JSONObject theme;
        try {
            theme = new JSONObject(customThemeJson);
        } catch (JSONException e) {
            throw new IllegalArgumentException(e);
        }
        String baseTheme = null;
        for (Iterator<String> iterator = theme.keys(); iterator.hasNext(); ) {
            String key = iterator.next();
            switch (key.toLowerCase(Locale.US)) {
                case "basetheme":
                    if (baseTheme != null) throwDefinedMoreThenOnce("baseTheme");
                    String value = theme.optString(key);
                    switch (value.toLowerCase(Locale.US)) {
                        case "light": baseTheme = "light"; break;
                        case "dark": baseTheme = "dark"; break;
                        default: throw new IllegalArgumentException("Illegal value for baseTheme: " + value);
                    }
                    break;
                case "textcolorprimary": parseColor(key, theme.optString(key), android.R.attr.textColorPrimary, attrs); break;
                case "materialprimary": parseColor(key, theme.optString(key), R.attr.materialPrimary, attrs); break;
                case "materialprimarydark": parseColor(key, theme.optString(key), R.attr.materialPrimaryDark, attrs); break;
                case "materialnavigationbar": parseColor(key, theme.optString(key), R.attr.materialNavigationBar, attrs); break;
                case "activityrootbackground": parseColor(key, theme.optString(key), R.attr.activityRootBackground, attrs); break;
                case "sidebarbackground": parseColor(key, theme.optString(key), R.attr.sidebarBackground, attrs); break;
                case "sidebarselecteditem": parseColor(key, theme.optString(key), R.attr.sidebarSelectedItem, attrs); break;
                case "listseparatorbackground": parseColor(key, theme.optString(key), R.attr.listSeparatorBackground, attrs); break;
                case "postunreadoverlay": parseColor(key, theme.optString(key), R.attr.postUnreadOverlay, attrs); break;
                case "postbackground": parseColor(key, theme.optString(key), R.attr.postBackground, attrs); break;
                case "postforeground": parseColor(key, theme.optString(key), R.attr.postForeground, attrs); break;
                case "postindexforeground": parseColor(key, theme.optString(key), R.attr.postIndexForeground, attrs); break;
                case "postindexoverbumplimit": parseColor(key, theme.optString(key), R.attr.postIndexOverBumpLimit, attrs); break;
                case "postnumberforeground": parseColor(key, theme.optString(key), R.attr.postNumberForeground, attrs); break;
                case "postnameforeground": parseColor(key, theme.optString(key), R.attr.postNameForeground, attrs); break;
                case "postopforeground": parseColor(key, theme.optString(key), R.attr.postOpForeground, attrs); break;
                case "postsageforeground": parseColor(key, theme.optString(key), R.attr.postSageForeground, attrs); break;
                case "posttripforeground": parseColor(key, theme.optString(key), R.attr.postTripForeground, attrs); break;
                case "posttitleforeground": parseColor(key, theme.optString(key), R.attr.postTitleForeground, attrs); break;
                case "postquoteforeground": parseColor(key, theme.optString(key), R.attr.postQuoteForeground, attrs); break;
                case "spoilerforeground": parseColor(key, theme.optString(key), R.attr.spoilerForeground, attrs); break;
                case "spoilerbackground": parseColor(key, theme.optString(key), R.attr.spoilerBackground, attrs); break;
                case "urllinkforeground": parseColor(key, theme.optString(key), R.attr.urlLinkForeground, attrs); break;
                case "refererforeground": parseColor(key, theme.optString(key), R.attr.refererForeground, attrs); break;
                case "iteminfoforeground": parseColor(key, theme.optString(key), R.attr.itemInfoForeground, attrs); break;
                case "searchhighlightbackground": parseColor(key, theme.optString(key), R.attr.searchHighlightBackground, attrs); break;
                case "subscriptionbackground": parseColor(key, theme.optString(key), R.attr.subscriptionBackground, attrs); break;
                default: throw new IllegalArgumentException("Unknown key: " + key);
            }
        }
        if (baseTheme == null) throw new IllegalArgumentException("Base theme not set");
        return baseTheme.equals("light") ? BASE_THEME_LIGHT : BASE_THEME_DARK;
    }
    
    private static void parseColor(String key, String value, int attrId, SparseIntArray array) {
        if (array.indexOfKey(attrId) >= 0) throwDefinedMoreThenOnce(key);
        try {
            array.put(attrId, Color.parseColor(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown color: "+value);
        }
    }
    
    private static String colorToString(int color) {
        if (Color.alpha(color) == 0xFF) return String.format("#%06X", color & 0xFFFFFF);
        return String.format("#%08X", color);
    }
    
    private static void putThemeValue(JSONObject theme, String key, String value) {
        try {
            theme.put(key, value);
        } catch (JSONException ignored) {
        }
    }
    
    private static void throwDefinedMoreThenOnce(String key) {
        throw new IllegalArgumentException(String.format("Key %s is defined more than once", key));
    }
    
    private static boolean sparseIntArrayEquals(SparseIntArray a, SparseIntArray b) {
        if (a == b) return true;
        if (a == null) return b == null;
        if (b == null) return false;
        int size = a.size();
        if (size != b.size()) return false;
        for (int i=0; i<size; ++i) {
            if (a.keyAt(i) != b.keyAt(i)) return false;
            if (a.valueAt(i) != b.valueAt(i)) return false; 
        }
        return true;
    }
}
