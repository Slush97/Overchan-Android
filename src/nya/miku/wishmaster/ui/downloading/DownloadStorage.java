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

package nya.miku.wishmaster.ui.downloading;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import nya.miku.wishmaster.common.Logger;
import nya.miku.wishmaster.common.MainApplication;

public class DownloadStorage {
    private static final String TAG = "DownloadStorage";

    public static class DownloadTarget {
        public OutputStream stream;
        public Uri contentUri;
        public File file;
    }

    public static DownloadTarget createFile(Context ctx, String chanName, String subdirectory, String fileName, String mimeType) throws IOException {
        DownloadTarget target = new DownloadTarget();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = guessMimeType(fileName);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, mimeType);
            String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + chanName;
            if (subdirectory != null && !subdirectory.isEmpty()) {
                relativePath += "/" + subdirectory;
            }
            values.put(MediaStore.Downloads.RELATIVE_PATH, relativePath);
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            ContentResolver resolver = ctx.getContentResolver();
            target.contentUri = resolver.insert(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values);
            if (target.contentUri == null) {
                throw new IOException("Failed to create MediaStore entry for " + fileName);
            }
            target.stream = resolver.openOutputStream(target.contentUri);
            if (target.stream == null) {
                resolver.delete(target.contentUri, null, null);
                throw new IOException("Failed to open output stream for " + fileName);
            }
        } else {
            File directory = new File(MainApplication.getInstance().settings.getDownloadDirectory(), chanName);
            if (subdirectory != null && !subdirectory.isEmpty()) {
                directory = new File(directory, subdirectory);
            }
            if (!directory.mkdirs() && !directory.isDirectory()) {
                throw new IOException("Couldn't create directory: " + directory.getAbsolutePath());
            }
            target.file = new File(directory, fileName);
            target.stream = new FileOutputStream(target.file);
        }
        return target;
    }

    public static void finalizeDownload(Context ctx, DownloadTarget target) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (target.contentUri != null) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                ctx.getContentResolver().update(target.contentUri, values, null, null);
            }
        } else {
            if (target.file != null) {
                MediaScannerConnection.scanFile(ctx, new String[]{target.file.getAbsolutePath()}, null, null);
            }
        }
    }

    public static void cancelDownload(Context ctx, DownloadTarget target) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (target.contentUri != null) {
                try {
                    ctx.getContentResolver().delete(target.contentUri, null, null);
                } catch (Exception e) {
                    Logger.e(TAG, "Failed to delete partial download", e);
                }
            }
        } else {
            if (target.file != null) {
                target.file.delete();
            }
        }
    }

    public static boolean fileExists(Context ctx, String chanName, String subdirectory, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + chanName;
            if (subdirectory != null && !subdirectory.isEmpty()) {
                relativePath += "/" + subdirectory;
            }
            // MediaStore RELATIVE_PATH ends with /
            if (!relativePath.endsWith("/")) {
                relativePath += "/";
            }
            ContentResolver resolver = ctx.getContentResolver();
            String[] projection = {MediaStore.Downloads._ID};
            String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?";
            String[] selectionArgs = {fileName, relativePath};
            Cursor cursor = null;
            try {
                cursor = resolver.query(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        projection, selection, selectionArgs, null);
                return cursor != null && cursor.getCount() > 0;
            } catch (Exception e) {
                Logger.e(TAG, "Error checking file existence", e);
                return false;
            } finally {
                if (cursor != null) cursor.close();
            }
        } else {
            File directory = new File(MainApplication.getInstance().settings.getDownloadDirectory(), chanName);
            if (subdirectory != null && !subdirectory.isEmpty()) {
                directory = new File(directory, subdirectory);
            }
            return new File(directory, fileName).exists();
        }
    }

    public static File getSavedThreadsDir(Context ctx, String chanName) {
        File dir = new File(ctx.getExternalFilesDir("saved_threads"), chanName);
        if (!dir.mkdirs() && !dir.isDirectory()) {
            Logger.e(TAG, "Couldn't create saved threads directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    public static InputStream openDownloadedFile(Context ctx, String chanName, String subdirectory, String fileName) throws IOException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String relativePath = Environment.DIRECTORY_DOWNLOADS + "/" + chanName;
            if (subdirectory != null && !subdirectory.isEmpty()) {
                relativePath += "/" + subdirectory;
            }
            if (!relativePath.endsWith("/")) {
                relativePath += "/";
            }
            ContentResolver resolver = ctx.getContentResolver();
            String[] projection = {MediaStore.Downloads._ID};
            String selection = MediaStore.Downloads.DISPLAY_NAME + "=? AND " + MediaStore.Downloads.RELATIVE_PATH + "=?";
            String[] selectionArgs = {fileName, relativePath};
            Cursor cursor = null;
            try {
                cursor = resolver.query(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                        projection, selection, selectionArgs, null);
                if (cursor != null && cursor.moveToFirst()) {
                    long id = cursor.getLong(0);
                    Uri contentUri = Uri.withAppendedPath(MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), String.valueOf(id));
                    InputStream is = resolver.openInputStream(contentUri);
                    if (is == null) {
                        throw new IOException("Failed to open input stream for " + fileName);
                    }
                    return is;
                }
            } finally {
                if (cursor != null) cursor.close();
            }
            throw new IOException("File not found in MediaStore: " + fileName);
        } else {
            File directory = new File(MainApplication.getInstance().settings.getDownloadDirectory(), chanName);
            if (subdirectory != null && !subdirectory.isEmpty()) {
                directory = new File(directory, subdirectory);
            }
            return new FileInputStream(new File(directory, fileName));
        }
    }

    private static String guessMimeType(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex >= 0) {
            String ext = fileName.substring(dotIndex + 1).toLowerCase();
            String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
            if (mime != null) return mime;
        }
        return "application/octet-stream";
    }
}
