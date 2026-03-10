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

/**
 * Thread-safe store for a solved 4chan captcha challenge+response pair.
 * Used between getNewCaptcha() (which solves interactively) and sendPost() (which submits it).
 */
internal object Chan4CaptchaSolved {
    @Volatile private var challenge: String? = null
    @Volatile private var response: String? = null

    @JvmStatic
    @Synchronized
    fun store(challenge: String, response: String) {
        this.challenge = challenge
        this.response = response
    }

    /** Returns [challenge, response] and clears, or null if nothing stored. */
    @JvmStatic
    @Synchronized
    fun pop(): Array<String>? {
        val c = challenge ?: return null
        val r = response ?: return null
        challenge = null
        response = null
        return arrayOf(c, r)
    }

    @JvmStatic
    @Synchronized
    fun hasSolved(): Boolean = challenge != null && response != null

    @JvmStatic
    @Synchronized
    fun clear() {
        challenge = null
        response = null
    }
}
