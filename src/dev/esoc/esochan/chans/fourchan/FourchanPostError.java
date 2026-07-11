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

import android.content.res.Resources;

import dev.esoc.esochan.R;

/**
 * Maps 4chan {@code span#errmsg} text to stable kinds and user-visible messages.
 * Only used for {@link FourchanPostResponse.Type#SERVER_ERROR}; unexpected bodies stay unmapped.
 */
final class FourchanPostError {

    enum Kind {
        CAPTCHA,
        BANNED,
        FLOOD,
        DUPLICATE,
        FILE_TOO_LARGE,
        VERIFY_EMAIL,
        PASS,
        OTHER
    }

    private FourchanPostError() {}

    static Kind classifyServerMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return Kind.OTHER;
        }
        String lower = message.toLowerCase();

        if (containsAny(lower, "banned", "4chan.org/banned", "ban page")) {
            return Kind.BANNED;
        }
        if ((lower.contains("verify") || lower.contains("confirmation"))
                && (lower.contains("email") || lower.contains("e-mail") || lower.contains("address"))) {
            return Kind.VERIFY_EMAIL;
        }
        if (containsAny(lower, "captcha", "mistyped", "solve the")) {
            return Kind.CAPTCHA;
        }
        if (containsAny(lower, "flood", "wait a while", "before posting again", "before posting")) {
            return Kind.FLOOD;
        }
        if (containsAny(lower, "duplicate", "identical to")) {
            return Kind.DUPLICATE;
        }
        if (containsAny(lower, "file too large", "max file", "maximum file", "image is too large")) {
            return Kind.FILE_TOO_LARGE;
        }
        if (containsAny(lower, "4chan pass", "not authenticated")
                || (lower.contains("pass") && lower.contains("authenticated"))) {
            return Kind.PASS;
        }
        return Kind.OTHER;
    }

    /**
     * Localized kind prefix + raw server text so copy-details stays useful.
     */
    static String formatUserMessage(Resources res, Kind kind, String rawServerText) {
        String raw = rawServerText != null ? rawServerText.trim() : "";
        int titleRes;
        switch (kind) {
            case CAPTCHA:
                titleRes = R.string.posting_error_kind_captcha;
                break;
            case BANNED:
                titleRes = R.string.posting_error_kind_banned;
                break;
            case FLOOD:
                titleRes = R.string.posting_error_kind_flood;
                break;
            case DUPLICATE:
                titleRes = R.string.posting_error_kind_duplicate;
                break;
            case FILE_TOO_LARGE:
                titleRes = R.string.posting_error_kind_file_too_large;
                break;
            case VERIFY_EMAIL:
                titleRes = R.string.posting_error_kind_verify_email;
                break;
            case PASS:
                titleRes = R.string.posting_error_kind_pass;
                break;
            case OTHER:
            default:
                titleRes = R.string.posting_error_kind_other;
                break;
        }
        String title = res.getString(titleRes);
        if (raw.isEmpty()) {
            return title;
        }
        return title + "\n\n" + raw;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) {
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
