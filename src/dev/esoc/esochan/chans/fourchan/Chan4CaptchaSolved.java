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

/**
 * Stores a solved 4chan captcha challenge+response pair in memory.
 * Used between getNewCaptcha() (which solves the captcha interactively)
 * and sendPost() (which submits it with the post).
 */
class Chan4CaptchaSolved {
    private static volatile String challenge;
    private static volatile String response;

    static synchronized void store(String challenge, String response) {
        Chan4CaptchaSolved.challenge = challenge;
        Chan4CaptchaSolved.response = response;
    }

    static synchronized String[] pop() {
        if (challenge == null || response == null) return null;
        String[] result = { challenge, response };
        challenge = null;
        response = null;
        return result;
    }

    static synchronized boolean hasSolved() {
        return challenge != null && response != null;
    }

    static synchronized void clear() {
        challenge = null;
        response = null;
    }
}
