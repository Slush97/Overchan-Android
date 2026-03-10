/*
 * esochan (Meta Imageboard Client)
 * Copyright (C) 2024-2026  esoc <https://github.com/esoc-dev>
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

package dev.esoc.esochan.chans.fourchan;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import dev.esoc.esochan.R;
import dev.esoc.esochan.api.interfaces.CancellableTask;
import dev.esoc.esochan.common.Async;
import dev.esoc.esochan.common.Logger;
import dev.esoc.esochan.http.interactive.InteractiveException;
import dev.esoc.esochan.lib.org_json.JSONArray;
import dev.esoc.esochan.lib.org_json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles 4chan's custom captcha system (post-2024).
 * Uses a WebView to fetch the captcha JSON (bypasses Cloudflare TLS fingerprinting),
 * then displays the task-based image selection challenge interactively
 * and stores the solved response (t-challenge / t-response).
 */
class Chan4Captcha extends InteractiveException {
    private static final long serialVersionUID = 1L;
    private static final String TAG = "Chan4Captcha";

    /** Stored ticket from captcha responses, sent back with subsequent requests. */
    private static volatile String storedTicket;

    private final String boardName;
    private final String threadNumber;

    Chan4Captcha(String boardName, String threadNumber) {
        this.boardName = boardName;
        this.threadNumber = threadNumber;
    }

    @Override
    public String getServiceName() {
        return "4chan Captcha";
    }

    @Override
    public void handle(final Activity activity, final CancellableTask task, final Callback callback) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                fetchCaptchaViaWebView(activity, task, callback);
            }
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void fetchCaptchaViaWebView(final Activity activity, final CancellableTask task, final Callback callback) {
        StringBuilder urlBuilder = new StringBuilder("https://sys.4chan.org/captcha?board=");
        urlBuilder.append(boardName);
        if (threadNumber != null) {
            urlBuilder.append("&thread_id=").append(threadNumber);
        }
        if (storedTicket != null) {
            urlBuilder.append("&ticket=").append(storedTicket);
        }
        final String captchaUrl = urlBuilder.toString();

        final WebView webView = new WebView(activity);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        // Container for WebView — needs explicit height for AlertDialog
        final FrameLayout container = new FrameLayout(activity);
        int dialogHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.6);
        container.setMinimumHeight(dialogHeight);
        webView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        container.addView(webView);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Loading captcha...")
                .setView(container)
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dlg, int which) {
                        webView.stopLoading();
                        callback.onError("Cancelled");
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dlg) {
                        webView.stopLoading();
                        callback.onError("Cancelled");
                    }
                })
                .create();
        dialog.setCanceledOnTouchOutside(false);

        final boolean[] handled = {false};

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (handled[0] || task.isCancelled()) return;

                // The captcha response is a <script> tag with postMessage({json}, '*')
                // Extract JSON from the page source (not body text, which is empty)
                view.evaluateJavascript(
                        "(function() { try { return document.documentElement.outerHTML; } catch(e) { return ''; } })()",
                        new android.webkit.ValueCallback<String>() {
                            @Override
                            public void onReceiveValue(String value) {
                                if (value == null || handled[0]) return;
                                String pageSource = unescapeJs(value);
                                if (pageSource == null || pageSource.isEmpty()) return;

                                // Check for Cloudflare challenge
                                if (pageSource.contains("Just a moment") || pageSource.contains("challenge-platform")) {
                                    dialog.setTitle("Cloudflare Verification");
                                    return;
                                }

                                // Extract JSON from postMessage script or page content
                                try {
                                    final String json = extractJsonFromPage(pageSource);
                                    if (json == null) return;
                                    handled[0] = true;
                                    // Decode images off the UI thread
                                    Async.runAsync(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                final CaptchaData data = parseCaptchaJson(json);
                                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        cleanupWebView(webView, dialog);
                                                        if (data.cooldownSeconds > 0) {
                                                            showCooldownThenRetry(activity, task, callback, data.cooldownSeconds);
                                                        } else if (data.errorMessage != null) {
                                                            callback.onError(data.errorMessage);
                                                        } else {
                                                            showCaptchaDialog(activity, task, callback, data);
                                                        }
                                                    }
                                                });
                                            } catch (final Exception e) {
                                                Logger.e(TAG, "Parse error", e);
                                                new Handler(Looper.getMainLooper()).post(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        cleanupWebView(webView, dialog);
                                                        callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to parse captcha");
                                                    }
                                                });
                                            }
                                        }
                                    });
                                } catch (Exception e) {
                                    Logger.e(TAG, "Extract error", e);
                                    handled[0] = true;
                                    cleanupWebView(webView, dialog);
                                    callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to parse captcha");
                                }
                            }
                        });
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                android.util.Log.e(TAG, "WebView error: " + errorCode + " " + description + " url=" + failingUrl);
                // Only treat main page errors as fatal, ignore sub-resource failures
                if (!handled[0] && failingUrl != null && failingUrl.startsWith(captchaUrl)) {
                    handled[0] = true;
                    cleanupWebView(webView, dialog);
                    callback.onError("Network error: " + description);
                }
            }
        });

        dialog.show();
        webView.loadUrl(captchaUrl);
    }

    private static String unescapeJs(String jsString) {
        if (jsString == null) return null;
        // Remove surrounding quotes from evaluateJavascript
        if (jsString.startsWith("\"") && jsString.endsWith("\"")) {
            jsString = jsString.substring(1, jsString.length() - 1);
        }
        if ("null".equals(jsString)) return null;
        return jsString
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"");
    }

    /**
     * Extracts captcha JSON from the HTML page source.
     * 4chan's captcha endpoint returns HTML with a script like:
     * {@code window.parent.postMessage({"twister":{...}}, '*');}
     */
    private static String extractJsonFromPage(String pageSource) {
        if (pageSource == null) return null;

        // Look for postMessage pattern: .postMessage({...}, '*')
        int msgIdx = pageSource.indexOf(".postMessage(");
        if (msgIdx >= 0) {
            int start = pageSource.indexOf('{', msgIdx);
            if (start >= 0) {
                // Find matching closing brace, accounting for nesting
                int depth = 0;
                for (int i = start; i < pageSource.length(); i++) {
                    char c = pageSource.charAt(i);
                    if (c == '{') depth++;
                    else if (c == '}') {
                        depth--;
                        if (depth == 0) {
                            return pageSource.substring(start, i + 1);
                        }
                    }
                }
            }
        }

        // Fallback: try to find raw JSON in page
        int braceStart = pageSource.indexOf('{');
        int braceEnd = pageSource.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            String candidate = pageSource.substring(braceStart, braceEnd + 1);
            if (candidate.contains("\"challenge\"") || candidate.contains("\"twister\"")
                    || candidate.contains("\"error\"") || candidate.contains("\"pcd_msg\"")) {
                return candidate;
            }
        }

        return null;
    }

    private static void cleanupWebView(WebView webView, AlertDialog dialog) {
        try {
            dialog.dismiss();
        } catch (Exception ignored) {}
        try {
            webView.stopLoading();
            webView.destroy();
        } catch (Exception ignored) {}
    }

    private static CaptchaData parseCaptchaJson(String json) throws Exception {
        // Strip twister wrapper if present: {"twister": {...}}
        if (json.contains("\"twister\"")) {
            JSONObject wrapper = new JSONObject(json);
            if (wrapper.has("twister")) {
                json = wrapper.get("twister").toString();
            }
        }

        JSONObject obj = new JSONObject(json);

        // Extract and store ticket for subsequent requests
        if (obj.has("ticket") && !obj.isNull("ticket")) {
            Object ticketVal = obj.get("ticket");
            if (ticketVal instanceof String) {
                storedTicket = (String) ticketVal;
                android.util.Log.d(TAG, "Stored ticket: " + storedTicket.substring(0, Math.min(20, storedTicket.length())) + "...");
            } else if (Boolean.FALSE.equals(ticketVal)) {
                storedTicket = null;
                android.util.Log.d(TAG, "Ticket removed (false)");
            }
        }

        // Check for errors / cooldowns
        if (obj.has("error") && !obj.isNull("error")) {
            CaptchaData data = new CaptchaData();
            data.errorMessage = "Captcha error: " + obj.getString("error");
            return data;
        }
        if (obj.has("pcd_msg") && !obj.isNull("pcd_msg")) {
            CaptchaData data = new CaptchaData();
            // cd_until is a Unix timestamp; pcd/cd are durations in seconds
            long cdUntil = obj.optLong("cd_until", 0);
            if (cdUntil > 0) {
                int remaining = (int) (cdUntil - System.currentTimeMillis() / 1000);
                data.cooldownSeconds = Math.max(remaining, 1);
            } else {
                int cd = obj.optInt("pcd", obj.optInt("cd", 0));
                if (cd > 0) {
                    data.cooldownSeconds = cd;
                } else {
                    data.errorMessage = obj.getString("pcd_msg");
                }
            }
            return data;
        }

        CaptchaData data = new CaptchaData();
        if (!obj.has("challenge")) {
            data.errorMessage = "Invalid captcha response (no challenge field)";
            return data;
        }
        data.challenge = obj.getString("challenge");
        data.ttl = obj.optInt("ttl", 120);

        // "noop" challenge means no captcha needed
        if ("noop".equalsIgnoreCase(data.challenge)) {
            data.isNoop = true;
            return data;
        }

        // Parse task-based captcha (image selection)
        if (!obj.has("tasks") || obj.isNull("tasks")) {
            data.errorMessage = "Unsupported captcha format (no tasks)";
            return data;
        }

        JSONArray tasksArray = obj.getJSONArray("tasks");
        data.tasks = new ArrayList<>();
        for (int i = 0; i < tasksArray.length(); i++) {
            JSONObject taskObj = tasksArray.getJSONObject(i);
            CaptchaTask captchaTask = new CaptchaTask();
            String strTitle = taskObj.optString("str", null);
            String imgTitle = taskObj.optString("img", null);
            // img field: decode as bitmap
            if (imgTitle != null && !imgTitle.isEmpty()) {
                captchaTask.titleBitmap = decodeBase64Bitmap(imgTitle);
            }
            // str field: may be plain text or an HTML <img> tag with embedded base64
            if (strTitle != null && !strTitle.isEmpty()) {
                if (strTitle.contains("base64,")) {
                    Bitmap strBitmap = decodeBase64Bitmap(strTitle);
                    if (strBitmap != null) {
                        captchaTask.titleBitmap = strBitmap;
                    } else {
                        captchaTask.textTitle = strTitle;
                    }
                } else {
                    captchaTask.textTitle = strTitle;
                }
            }
            if (taskObj.has("items")) {
                JSONArray items = taskObj.getJSONArray("items");
                captchaTask.images = new ArrayList<>();
                for (int j = 0; j < items.length(); j++) {
                    Bitmap bmp = decodeBase64Bitmap(items.getString(j));
                    if (bmp != null) {
                        captchaTask.images.add(bmp);
                    }
                }
            }
            data.tasks.add(captchaTask);
        }

        return data;
    }

    private static final Pattern DATA_URI_PATTERN = Pattern.compile("base64,([A-Za-z0-9+/=\\s]+)");

    /**
     * Extracts raw base64 from a string that may be:
     * - Raw base64 data
     * - A data URI ("data:image/png;base64,...")
     * - An HTML img tag ({@code <img src="data:image/png;base64,...">})
     */
    private static String extractBase64(String input) {
        if (input == null || input.isEmpty()) return null;
        Matcher m = DATA_URI_PATTERN.matcher(input);
        if (m.find()) {
            return m.group(1).replaceAll("\\s", "");
        }
        // Already raw base64
        return input;
    }

    private static Bitmap decodeBase64Bitmap(String b64) {
        try {
            String raw = extractBase64(b64);
            if (raw == null) return null;
            byte[] bytes = Base64.decode(raw, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            Logger.e(TAG, "Failed to decode captcha image", e);
            return null;
        }
    }

    private void showCooldownThenRetry(final Activity activity, final CancellableTask task,
                                        final Callback callback, int cooldownSeconds) {
        final AlertDialog cooldownDialog = new AlertDialog.Builder(activity)
                .setTitle("4chan Captcha")
                .setMessage("Please wait " + cooldownSeconds + "s...")
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dlg, int which) {
                        callback.onError("Cancelled");
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dlg) {
                        callback.onError("Cancelled");
                    }
                })
                .create();
        cooldownDialog.setCanceledOnTouchOutside(false);
        cooldownDialog.show();

        new CountDownTimer(cooldownSeconds * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (task.isCancelled()) {
                    cancel();
                    return;
                }
                int secs = (int) (millisUntilFinished / 1000) + 1;
                cooldownDialog.setMessage("Please wait " + secs + "s...");
            }

            @Override
            public void onFinish() {
                if (task.isCancelled()) return;
                try {
                    cooldownDialog.dismiss();
                } catch (Exception ignored) {}
                fetchCaptchaViaWebView(activity, task, callback);
            }
        }.start();
    }

    @SuppressLint("SetTextI18n")
    private void showCaptchaDialog(final Activity activity, final CancellableTask task,
                                    final Callback callback, final CaptchaData data) {
        if (data.isNoop) {
            Chan4CaptchaSolved.store(data.challenge, "noop");
            callback.onSuccess();
            return;
        }

        showImageSelectionDialog(activity, task, callback, data);
    }

    private void showImageSelectionDialog(final Activity activity, final CancellableTask task,
                                           final Callback callback, final CaptchaData data) {
        if (data.tasks == null || data.tasks.isEmpty()) {
            callback.onError("No captcha tasks received");
            return;
        }

        final StringBuilder solution = new StringBuilder();
        showTaskStep(activity, task, callback, data, 0, solution);
    }

    private void showTaskStep(final Activity activity, final CancellableTask task,
                               final Callback callback, final CaptchaData data,
                               final int taskIndex, final StringBuilder solution) {
        if (taskIndex >= data.tasks.size()) {
            Chan4CaptchaSolved.store(data.challenge, solution.toString());
            callback.onSuccess();
            return;
        }

        final CaptchaTask currentTask = data.tasks.get(taskIndex);

        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 12);
        layout.setPadding(pad, pad, pad, pad);

        // Title: image or text
        if (currentTask.titleBitmap != null) {
            ImageView titleImg = new ImageView(activity);
            titleImg.setImageBitmap(currentTask.titleBitmap);
            titleImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
            titleImg.setAdjustViewBounds(true);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(activity, 48));
            titleParams.bottomMargin = dp(activity, 8);
            layout.addView(titleImg, titleParams);
        } else if (currentTask.textTitle != null && !currentTask.textTitle.isEmpty()) {
            TextView titleText = new TextView(activity);
            titleText.setText(currentTask.textTitle);
            titleText.setGravity(Gravity.CENTER);
            titleText.setTextSize(15);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            titleParams.bottomMargin = dp(activity, 8);
            layout.addView(titleText, titleParams);
        }

        if (currentTask.images != null && !currentTask.images.isEmpty()) {
            // Determine grid dimensions
            int imageCount = currentTask.images.size();
            boolean wideImages = false;
            Bitmap first = currentTask.images.get(0);
            if (first != null && first.getHeight() > 0) {
                wideImages = (float) first.getWidth() / first.getHeight() > 1.5f;
            }
            int columns = wideImages ? 2 : (imageCount <= 4 ? 2 : 3);
            int rows = (imageCount + columns - 1) / columns;

            // Calculate cell size to fit within dialog (use ~85% of screen width, minus padding)
            int screenWidth = activity.getResources().getDisplayMetrics().widthPixels;
            int dialogWidth = (int) (screenWidth * 0.85) - pad * 2;
            int gap = dp(activity, 3);
            int cellSize = (dialogWidth - gap * (columns - 1)) / columns;

            GridLayout grid = new GridLayout(activity);
            grid.setColumnCount(columns);
            grid.setRowCount(rows);

            final int[] selectedIndex = {-1};
            final List<ImageView> imageViews = new ArrayList<>();

            for (int i = 0; i < imageCount; i++) {
                final ImageView imgView = new ImageView(activity);
                imgView.setImageBitmap(currentTask.images.get(i));
                imgView.setScaleType(ImageView.ScaleType.CENTER_CROP);
                imgView.setPadding(dp(activity, 2), dp(activity, 2), dp(activity, 2), dp(activity, 2));

                GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
                glp.width = cellSize;
                glp.height = cellSize;
                glp.rowSpec = GridLayout.spec(i / columns);
                glp.columnSpec = GridLayout.spec(i % columns);
                glp.setMargins(i % columns > 0 ? gap : 0, i / columns > 0 ? gap : 0, 0, 0);
                grid.addView(imgView, glp);
                imageViews.add(imgView);

                final int index = i;
                imgView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Single select: deselect previous, select this one
                        if (selectedIndex[0] == index) {
                            // Deselect
                            selectedIndex[0] = -1;
                            imgView.setAlpha(1.0f);
                            imgView.setBackgroundColor(0);
                        } else {
                            // Deselect previous
                            if (selectedIndex[0] >= 0 && selectedIndex[0] < imageViews.size()) {
                                ImageView prev = imageViews.get(selectedIndex[0]);
                                prev.setAlpha(1.0f);
                                prev.setBackgroundColor(0);
                            }
                            // Select this
                            selectedIndex[0] = index;
                            imgView.setAlpha(0.6f);
                            imgView.setBackgroundColor(0x440088FF);
                        }
                    }
                });
            }

            layout.addView(grid);

            // Step indicator for multi-step captchas
            if (data.tasks.size() > 1) {
                TextView stepInfo = new TextView(activity);
                stepInfo.setText("Step " + (taskIndex + 1) + " of " + data.tasks.size());
                stepInfo.setGravity(Gravity.CENTER);
                stepInfo.setTextSize(13);
                LinearLayout.LayoutParams stepParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                stepParams.topMargin = dp(activity, 6);
                layout.addView(stepInfo, stepParams);
            }

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setTitle("4chan Captcha")
                    .setView(layout)
                    .setPositiveButton(taskIndex < data.tasks.size() - 1 ? "Next" : "Submit",
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dlg, int which) {
                                    if (selectedIndex[0] < 0) {
                                        Toast.makeText(activity, "Please select an image", Toast.LENGTH_SHORT).show();
                                        showTaskStep(activity, task, callback, data, taskIndex, solution);
                                        return;
                                    }
                                    solution.append(selectedIndex[0]);
                                    showTaskStep(activity, task, callback, data, taskIndex + 1, solution);
                                }
                            })
                    .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dlg, int which) {
                            callback.onError("Cancelled");
                        }
                    })
                    .setOnCancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dlg) {
                            callback.onError("Cancelled");
                        }
                    })
                    .create();
            dialog.setCanceledOnTouchOutside(false);
            dialog.show();
        }
    }

    private static int dp(Activity activity, int value) {
        return (int) (activity.getResources().getDisplayMetrics().density * value + 0.5f);
    }

    // --- Data models ---

    static class CaptchaData {
        String challenge;
        int ttl;
        boolean isNoop;
        List<CaptchaTask> tasks;
        String errorMessage;
        /** Cooldown in seconds before captcha can be requested again. 0 = no cooldown. */
        int cooldownSeconds;
    }

    static class CaptchaTask {
        String textTitle;
        Bitmap titleBitmap;
        List<Bitmap> images;
    }
}
