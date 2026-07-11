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

import dev.esoc.esochan.common.SecurePreferences

/**
 * Persists 4chan captcha *tickets* (not challenge solutions) across process death.
 *
 * TTL is 6 hours when site lifetime is unobserved (product decision 2026-07-11).
 * Never log ticket values. Never store captcha challenge/response solutions here.
 */
internal object Chan4CaptchaTicketStore {

    /** Conservative TTL when live ticket lifetime is unobserved. */
    const val TTL_MS: Long = 6L * 60L * 60L * 1000L

    private const val KEY_TICKET = "chan4_captcha_ticket"
    private const val KEY_STORED_AT = "chan4_captcha_ticket_stored_at_ms"

    @JvmStatic
    @JvmOverloads
    fun isExpired(storedAtMs: Long, nowMs: Long, ttlMs: Long = TTL_MS): Boolean {
        if (storedAtMs <= 0L) return true
        return nowMs - storedAtMs > ttlMs
    }

    /**
     * Load ticket if present and within TTL. Clears store on expiry or corrupt timestamp.
     * Returns null when nothing usable is stored.
     */
    @JvmStatic
    @JvmOverloads
    fun load(nowMs: Long = System.currentTimeMillis()): String? {
        val ticket = SecurePreferences.get(KEY_TICKET)
        if (ticket.isEmpty()) {
            return null
        }
        val storedAt = SecurePreferences.get(KEY_STORED_AT).toLongOrNull()
        if (storedAt == null || isExpired(storedAt, nowMs)) {
            clear()
            return null
        }
        return ticket
    }

    @JvmStatic
    @JvmOverloads
    fun save(ticket: String, nowMs: Long = System.currentTimeMillis()) {
        if (ticket.isEmpty()) {
            clear()
            return
        }
        SecurePreferences.put(KEY_TICKET, ticket)
        SecurePreferences.put(KEY_STORED_AT, nowMs.toString())
    }

    @JvmStatic
    fun clear() {
        SecurePreferences.remove(KEY_TICKET)
        SecurePreferences.remove(KEY_STORED_AT)
    }
}
