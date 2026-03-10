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

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import dev.esoc.esochan.common.Logger
import dev.esoc.esochan.lib.org_json.JSONObject

private const val TAG = "Chan4CaptchaData"
private val DATA_URI_PATTERN = Regex("""base64,([A-Za-z0-9+/=\s]+)""")

/**
 * Parsed 4chan captcha API response. The API returns one of several formats
 * depending on server state and user trust level.
 */
internal sealed class Chan4CaptchaData {

    /** No captcha needed (trusted user/pass). */
    data class Noop(val challenge: String, val ttl: Int) : Chan4CaptchaData()

    /** Rate-limited — must wait before requesting again. */
    data class Cooldown(val message: String, val seconds: Int) : Chan4CaptchaData()

    /** Server returned an error. */
    data class Error(val message: String) : Chan4CaptchaData()

    /**
     * Slider captcha: foreground image with transparent windows overlays a background.
     * User drags a slider to align them, then types the revealed text.
     */
    data class Slider(
        val challenge: String,
        val ttl: Int,
        val foreground: Bitmap,
        val background: Bitmap,
        val imgWidth: Int,
        val imgHeight: Int,
        val bgWidth: Int
    ) : Chan4CaptchaData()

    /**
     * Task-based image selection: one or more steps where the user picks
     * an image matching a prompt. Response is concatenated selected indices.
     */
    data class ImageSelection(
        val challenge: String,
        val ttl: Int,
        val tasks: List<CaptchaTask>
    ) : Chan4CaptchaData()

    companion object {
        /**
         * Parse the JSON response from the 4chan captcha API.
         * Handles the optional `{"twister": {...}}` wrapper.
         * Updates [Chan4Captcha.storedTicket] as a side effect.
         */
        fun parse(rawJson: String): Chan4CaptchaData {
            try {
                return doParse(rawJson)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to parse captcha JSON", e)
                return Error(e.message ?: "Failed to parse captcha")
            }
        }

        private fun doParse(rawJson: String): Chan4CaptchaData {
            Logger.d(TAG, "Raw captcha JSON: ${rawJson.take(500)}")

            // Unwrap twister envelope if present
            var json = rawJson
            if (json.contains("\"twister\"")) {
                val wrapper = JSONObject(json)
                if (wrapper.has("twister")) {
                    json = wrapper.get("twister").toString()
                }
            }

            val obj = JSONObject(json)
            Logger.d(TAG, "Parsed keys: ${obj.keys().asSequence().toList()}")

            // Extract ticket for subsequent requests
            if (obj.has("ticket") && !obj.isNull("ticket")) {
                when (val ticket = obj.get("ticket")) {
                    is String -> Chan4Captcha.storedTicket = ticket
                    is Boolean -> if (!ticket) Chan4Captcha.storedTicket = null
                }
            }

            // Error
            if (obj.has("error") && !obj.isNull("error")) {
                return Error("Captcha error: ${obj.getString("error")}")
            }

            // Cooldown — only treat as blocking if there's NO challenge alongside it
            if (obj.has("pcd_msg") && !obj.isNull("pcd_msg") && !obj.has("challenge")) {
                Logger.d(TAG, "Cooldown: pcd_msg=${obj.getString("pcd_msg")}, cd_until=${obj.optLong("cd_until", 0)}, pcd=${obj.optInt("pcd", 0)}, cd=${obj.optInt("cd", 0)}")
                val cdUntil = obj.optLong("cd_until", 0)
                val seconds = if (cdUntil > 0) {
                    maxOf((cdUntil - System.currentTimeMillis() / 1000).toInt(), 1)
                } else {
                    val cd = obj.optInt("pcd", obj.optInt("cd", 0))
                    if (cd > 0) cd else 0
                }
                return if (seconds > 0) {
                    Cooldown(obj.getString("pcd_msg"), seconds)
                } else {
                    Error(obj.getString("pcd_msg"))
                }
            }

            // Must have a challenge from here on
            if (!obj.has("challenge")) {
                return Error("Invalid captcha response (no challenge field)")
            }
            val challenge = obj.getString("challenge")
            val ttl = obj.optInt("ttl", 120)

            // Noop — no captcha required
            if (challenge.equals("noop", ignoreCase = true)) {
                return Noop(challenge, ttl)
            }

            // Slider captcha: has img + bg fields, no tasks
            if (obj.has("img") && obj.has("bg") && !obj.has("tasks")) {
                val fg = decodeBase64Bitmap(obj.getString("img"))
                val bg = decodeBase64Bitmap(obj.getString("bg"))
                if (fg != null && bg != null) {
                    return Slider(
                        challenge = challenge,
                        ttl = ttl,
                        foreground = fg,
                        background = bg,
                        imgWidth = obj.optInt("img_width", fg.width),
                        imgHeight = obj.optInt("img_height", fg.height),
                        bgWidth = obj.optInt("bg_width", bg.width)
                    )
                }
                return Error("Failed to decode slider captcha images")
            }

            // Task-based image selection
            if (obj.has("tasks") && !obj.isNull("tasks")) {
                val tasksArray = obj.getJSONArray("tasks")
                val tasks = (0 until tasksArray.length()).map { i ->
                    val taskObj = tasksArray.getJSONObject(i)
                    parseTask(taskObj)
                }
                return ImageSelection(challenge, ttl, tasks)
            }

            return Error("Unsupported captcha format")
        }

        private fun parseTask(taskObj: JSONObject): CaptchaTask {
            val strTitle = taskObj.optString("str", null)
            val imgTitle = taskObj.optString("img", null)

            var textTitle: String? = null
            var titleBitmap: Bitmap? = null

            // img field: decode as bitmap title
            if (!imgTitle.isNullOrEmpty()) {
                titleBitmap = decodeBase64Bitmap(imgTitle)
            }

            // str field: may be plain text or HTML <img> with embedded base64
            if (!strTitle.isNullOrEmpty()) {
                if (strTitle.contains("base64,")) {
                    val bmp = decodeBase64Bitmap(strTitle)
                    if (bmp != null) titleBitmap = bmp else textTitle = strTitle
                } else {
                    textTitle = strTitle
                }
            }

            // Decode item images
            val images = if (taskObj.has("items")) {
                val items = taskObj.getJSONArray("items")
                (0 until items.length()).mapNotNull { j ->
                    decodeBase64Bitmap(items.getString(j))
                }
            } else {
                emptyList()
            }

            return CaptchaTask(textTitle, titleBitmap, images)
        }

        private fun decodeBase64Bitmap(input: String): Bitmap? {
            return try {
                val raw = extractBase64(input) ?: return null
                val bytes = Base64.decode(raw, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            } catch (e: Exception) {
                Logger.e(TAG, "Failed to decode captcha image", e)
                null
            }
        }

        private fun extractBase64(input: String): String? {
            if (input.isEmpty()) return null
            val match = DATA_URI_PATTERN.find(input)
            if (match != null) {
                return match.groupValues[1].replace("\\s".toRegex(), "")
            }
            return input // already raw base64
        }
    }
}

internal data class CaptchaTask(
    val textTitle: String?,
    val titleBitmap: Bitmap?,
    val images: List<Bitmap>
)
