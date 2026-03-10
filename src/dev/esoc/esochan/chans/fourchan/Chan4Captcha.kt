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

package dev.esoc.esochan.chans.fourchan

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.CountDownTimer
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import dev.esoc.esochan.api.interfaces.CancellableTask
import dev.esoc.esochan.common.CancellableTaskScope
import dev.esoc.esochan.common.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.esoc.esochan.http.interactive.InteractiveException

private const val TAG = "Chan4Captcha"

/**
 * Handles 4chan's custom captcha system.
 *
 * Supports two captcha formats:
 * - **Slider**: Background + foreground images aligned via slider, user types revealed text
 * - **Image selection**: Grid of images, user picks the matching one per task step
 *
 * Uses a WebView to fetch the captcha JSON (bypasses Cloudflare TLS fingerprinting),
 * then displays the appropriate UI and stores the solved response.
 */
internal class Chan4Captcha(
    private val boardName: String,
    private val threadNumber: String?
) : InteractiveException() {

    override fun getServiceName(): String = "4chan Captcha"

    override fun handle(activity: Activity, task: CancellableTask, callback: Callback) {
        activity.runOnUiThread { fetchCaptchaViaWebView(activity, task, callback) }
    }

    // ── WebView fetch ───────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchCaptchaViaWebView(activity: Activity, task: CancellableTask, callback: Callback) {
        val url = buildString {
            append("https://sys.4chan.org/captcha?board=").append(boardName)
            threadNumber?.let { append("&thread_id=").append(it) }
            storedTicket?.let { append("&ticket=").append(it) }
        }

        val webView = WebView(activity).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
        }

        val container = FrameLayout(activity).apply {
            minimumHeight = (activity.resources.displayMetrics.heightPixels * 0.6).toInt()
            addView(webView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle("Loading captcha…")
            .setView(container)
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                webView.stopLoading()
                callback.onError("Cancelled")
            }
            .setOnCancelListener {
                webView.stopLoading()
                callback.onError("Cancelled")
            }
            .create()
            .apply { setCanceledOnTouchOutside(false) }

        var handled = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, pageUrl: String) {
                if (handled || task.isCancelled) return

                view.evaluateJavascript(
                    "(function(){try{return document.documentElement.outerHTML}catch(e){return ''}})()"
                ) { value ->
                    if (value == null || handled) return@evaluateJavascript
                    val pageSource = unescapeJs(value) ?: return@evaluateJavascript
                    if (pageSource.isEmpty()) return@evaluateJavascript

                    // Cloudflare challenge — let user interact
                    if ("Just a moment" in pageSource || "challenge-platform" in pageSource) {
                        dialog.setTitle("Cloudflare Verification")
                        return@evaluateJavascript
                    }

                    val json = extractJsonFromPage(pageSource)
                    if (json == null) {
                        Logger.d(TAG, "No JSON found in page (${pageSource.length} chars)")
                        return@evaluateJavascript
                    }
                    handled = true

                    // Parse off UI thread
                    val scope = CancellableTaskScope(task)
                    scope.launch {
                        val data = Chan4CaptchaData.parse(json)
                        withContext(Dispatchers.Main) {
                            cleanupWebView(webView, dialog)
                            handleParsedCaptcha(activity, task, callback, data)
                        }
                    }
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                Logger.e(TAG, "WebView error: $errorCode $description url=$failingUrl")
                if (!handled && failingUrl != null && failingUrl.startsWith(url)) {
                    handled = true
                    cleanupWebView(webView, dialog)
                    callback.onError("Network error: $description")
                }
            }
        }

        dialog.show()
        webView.loadUrl(url)
    }

    // ── Dispatch ────────────────────────────────────────────────────────

    private fun handleParsedCaptcha(
        activity: Activity, task: CancellableTask, callback: Callback, data: Chan4CaptchaData
    ) {
        when (data) {
            is Chan4CaptchaData.Noop -> {
                Chan4CaptchaSolved.store(data.challenge, "noop")
                callback.onSuccess()
            }
            is Chan4CaptchaData.Error -> callback.onError(data.message)
            is Chan4CaptchaData.Cooldown -> showCooldown(activity, task, callback, data.seconds, data.message)
            is Chan4CaptchaData.Slider -> showSliderCaptcha(activity, task, callback, data)
            is Chan4CaptchaData.ImageSelection -> showImageSelection(activity, task, callback, data)
        }
    }

    // ── Slider captcha UI ───────────────────────────────────────────────

    private fun showSliderCaptcha(
        activity: Activity, task: CancellableTask, callback: Callback, data: Chan4CaptchaData.Slider
    ) {
        val pad = 12.dp(activity)
        val maxOffset = maxOf(data.bgWidth - data.imgWidth, 1)

        // Composite image view that renders bg + fg at current offset
        val compositeView = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }

        // Render initial composite at offset 0
        fun renderComposite(offset: Int) {
            val composite = Bitmap.createBitmap(data.bgWidth, data.imgHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(composite)
            canvas.drawBitmap(data.background, 0f, 0f, null)
            canvas.drawBitmap(data.foreground, offset.toFloat(), 0f, null)
            compositeView.setImageBitmap(composite)
        }
        renderComposite(0)

        val slider = SeekBar(activity).apply {
            max = maxOffset
            progress = 0
        }

        val answerInput = EditText(activity).apply {
            hint = "Type the text you see"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            isSingleLine = true
            filters = arrayOf(android.text.InputFilter.AllCaps(), android.text.InputFilter.LengthFilter(10))
        }

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                renderComposite(progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)

            // Instructions
            addView(TextView(activity).apply {
                text = "Slide to align, then type the text"
                gravity = Gravity.CENTER
                textSize = 14f
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp(activity) })

            // Composite image
            addView(compositeView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8.dp(activity) })

            // Slider
            addView(slider, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 12.dp(activity) })

            // Text input
            addView(answerInput, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }

        AlertDialog.Builder(activity)
            .setTitle("4chan Captcha")
            .setView(layout)
            .setPositiveButton("Submit") { _, _ ->
                val answer = answerInput.text.toString().trim()
                if (answer.isEmpty()) {
                    Toast.makeText(activity, "Please type the captcha text", Toast.LENGTH_SHORT).show()
                    showSliderCaptcha(activity, task, callback, data)
                } else {
                    Chan4CaptchaSolved.store(data.challenge, answer)
                    callback.onSuccess()
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> callback.onError("Cancelled") }
            .setOnCancelListener { callback.onError("Cancelled") }
            .create()
            .apply { setCanceledOnTouchOutside(false) }
            .show()
    }

    // ── Image selection captcha UI ──────────────────────────────────────
    //
    // 4chan's native UI shows ONE image at a time with a scrollbar to cycle
    // through options. We replicate this: large image + SeekBar + step counter.

    private fun showImageSelection(
        activity: Activity, task: CancellableTask, callback: Callback, data: Chan4CaptchaData.ImageSelection
    ) {
        if (data.tasks.isEmpty()) {
            callback.onError("No captcha tasks received")
            return
        }
        showTaskStep(activity, task, callback, data, taskIndex = 0, solution = StringBuilder())
    }

    private fun showTaskStep(
        activity: Activity, task: CancellableTask, callback: Callback,
        data: Chan4CaptchaData.ImageSelection, taskIndex: Int, solution: StringBuilder
    ) {
        if (taskIndex >= data.tasks.size) {
            Chan4CaptchaSolved.store(data.challenge, solution.toString())
            callback.onSuccess()
            return
        }

        val currentTask = data.tasks[taskIndex]
        val pad = 12.dp(activity)

        val layout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }

        // Title: image (with text instructions) or plain text
        if (currentTask.titleBitmap != null) {
            layout.addView(
                ImageView(activity).apply {
                    setImageBitmap(currentTask.titleBitmap)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8.dp(activity) }
            )
        } else if (!currentTask.textTitle.isNullOrEmpty()) {
            layout.addView(
                TextView(activity).apply {
                    text = currentTask.textTitle
                    gravity = Gravity.CENTER
                    textSize = 15f
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = 8.dp(activity) }
            )
        }

        if (currentTask.images.isNotEmpty()) {
            val imageCount = currentTask.images.size

            // Single large image view — scrollbar cycles through images
            val imageView = ImageView(activity).apply {
                setImageBitmap(currentTask.images[0])
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }

            // Image counter: "Image 1 of 5"
            val counterText = TextView(activity).apply {
                text = "Image 1 of $imageCount"
                gravity = Gravity.CENTER
                textSize = 14f
            }

            // SeekBar to scroll through images
            val scrollbar = SeekBar(activity).apply {
                max = imageCount - 1
                progress = 0
            }

            var currentIndex = 0

            scrollbar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    currentIndex = progress
                    imageView.setImageBitmap(currentTask.images[progress])
                    counterText.text = "Image ${progress + 1} of $imageCount"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) {}
                override fun onStopTrackingTouch(seekBar: SeekBar) {}
            })

            layout.addView(imageView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp(activity) })

            layout.addView(counterText, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 4.dp(activity) })

            layout.addView(scrollbar, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))

            // Step indicator for multi-step captchas
            if (data.tasks.size > 1) {
                layout.addView(
                    TextView(activity).apply {
                        text = "Step ${taskIndex + 1} of ${data.tasks.size}"
                        gravity = Gravity.CENTER
                        textSize = 13f
                        setTextColor(Color.GRAY)
                    },
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 6.dp(activity) }
                )
            }

            val isLastStep = taskIndex >= data.tasks.size - 1

            AlertDialog.Builder(activity)
                .setTitle("4chan Captcha")
                .setView(layout)
                .setPositiveButton(if (isLastStep) "Submit" else "Next") { _, _ ->
                    solution.append(currentIndex)
                    showTaskStep(activity, task, callback, data, taskIndex + 1, solution)
                }
                .setNegativeButton(android.R.string.cancel) { _, _ -> callback.onError("Cancelled") }
                .setOnCancelListener { callback.onError("Cancelled") }
                .create()
                .apply { setCanceledOnTouchOutside(false) }
                .show()
        }
    }

    // ── Cooldown ────────────────────────────────────────────────────────

    private fun showCooldown(activity: Activity, task: CancellableTask, callback: Callback, seconds: Int, serverMessage: String? = null) {
        val displayMsg = if (serverMessage != null) {
            // Strip HTML tags for display
            val clean = serverMessage.replace(Regex("<[^>]*>"), "").trim()
            "$clean\n\nRetrying in ${seconds}s…"
        } else {
            "Please wait ${seconds}s…"
        }
        val dialog = AlertDialog.Builder(activity)
            .setTitle("4chan Captcha")
            .setMessage(displayMsg)
            .setNegativeButton(android.R.string.cancel) { _, _ -> callback.onError("Cancelled") }
            .setOnCancelListener { callback.onError("Cancelled") }
            .create()
            .apply { setCanceledOnTouchOutside(false) }

        dialog.show()

        object : CountDownTimer(seconds * 1000L, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                if (task.isCancelled) { cancel(); return }
                val remaining = millisUntilFinished / 1000 + 1
                val msg = if (serverMessage != null) {
                    val clean = serverMessage.replace(Regex("<[^>]*>"), "").trim()
                    "$clean\n\nRetrying in ${remaining}s…"
                } else {
                    "Please wait ${remaining}s…"
                }
                dialog.setMessage(msg)
            }
            override fun onFinish() {
                if (task.isCancelled) return
                try { dialog.dismiss() } catch (_: Exception) {}
                fetchCaptchaViaWebView(activity, task, callback)
            }
        }.start()
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    companion object {
        private const val serialVersionUID = 1L

        /** Stored ticket from captcha responses, sent with subsequent requests. */
        @Volatile
        @JvmStatic
        var storedTicket: String? = null
            internal set
    }
}

// ── Utility functions ───────────────────────────────────────────────────

private fun cleanupWebView(webView: WebView, dialog: AlertDialog) {
    try { dialog.dismiss() } catch (_: Exception) {}
    try { webView.stopLoading(); webView.destroy() } catch (_: Exception) {}
}

/**
 * Extracts captcha JSON from the page source.
 * 4chan's captcha endpoint returns HTML with: `window.parent.postMessage({json}, '*');`
 */
private fun extractJsonFromPage(pageSource: String): String? {
    // Look for postMessage({...}, '*')
    val msgIdx = pageSource.indexOf(".postMessage(")
    if (msgIdx >= 0) {
        val start = pageSource.indexOf('{', msgIdx)
        if (start >= 0) {
            var depth = 0
            for (i in start until pageSource.length) {
                when (pageSource[i]) {
                    '{' -> depth++
                    '}' -> { depth--; if (depth == 0) return pageSource.substring(start, i + 1) }
                }
            }
        }
    }

    // Fallback: find raw JSON in page
    val braceStart = pageSource.indexOf('{')
    val braceEnd = pageSource.lastIndexOf('}')
    if (braceStart >= 0 && braceEnd > braceStart) {
        val candidate = pageSource.substring(braceStart, braceEnd + 1)
        if ("\"challenge\"" in candidate || "\"twister\"" in candidate ||
            "\"error\"" in candidate || "\"pcd_msg\"" in candidate) {
            return candidate
        }
    }

    return null
}

private fun unescapeJs(jsString: String): String? {
    var s = jsString
    if (s.startsWith("\"") && s.endsWith("\"")) {
        s = s.substring(1, s.length - 1)
    }
    if (s == "null") return null
    return s.replace("\\\\", "\\")
        .replace("\\n", "\n")
        .replace("\\t", "\t")
        .replace("\\\"", "\"")
}

private fun Int.dp(activity: Activity): Int =
    (activity.resources.displayMetrics.density * this + 0.5f).toInt()
