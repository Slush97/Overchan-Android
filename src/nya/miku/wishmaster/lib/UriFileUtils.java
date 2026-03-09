/*
 * Resolve a content URI to a File, copying to a temp file if needed for scoped storage.
 */

package nya.miku.wishmaster.lib;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.common.Logger;

public class UriFileUtils {
    private static final String TAG = "UriFileUtils";

    /**
     * Get a File from a Uri. For file:// URIs, returns the file directly.
     * For content:// URIs, copies to a temp file in the app's cache directory.
     */
    public static File getFile(Context context, Uri uri) {
        if (uri == null) return null;

        // file:// URI — direct access
        if ("file".equalsIgnoreCase(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) return new File(path);
        }

        // content:// URI — copy to temp file
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            return copyToTempFile(context, uri);
        }

        return null;
    }

    private static File copyToTempFile(Context context, Uri uri) {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            String fileName = getDisplayName(context, uri);
            if (fileName == null) fileName = "attachment";

            // Preserve file extension
            String prefix = fileName;
            String suffix = "";
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                prefix = fileName.substring(0, dot);
                suffix = fileName.substring(dot);
            }
            if (prefix.length() < 3) prefix = prefix + "___";

            File tempFile = File.createTempFile(prefix, suffix, context.getCacheDir());
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            out = new FileOutputStream(tempFile);
            IOUtils.copyStream(in, out);
            return tempFile;
        } catch (Exception e) {
            Logger.e(TAG, e);
            return null;
        } finally {
            IOUtils.closeQuietly(in);
            IOUtils.closeQuietly(out);
        }
    }

    private static String getDisplayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception e) {
            Logger.e(TAG, e);
        } finally {
            if (cursor != null) cursor.close();
        }
        return null;
    }
}
