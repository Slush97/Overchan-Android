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

package dev.esoc.esochan.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher

/**
 * Application-wide [CoroutineScope] backed by [Async.LOW_PRIORITY_FACTORY]'s thread pool.
 *
 * Usage from Kotlin:
 * ```
 * AppScope.launch {
 *     val result = doWork()
 *     withContext(Dispatchers.Main) { updateUi(result) }
 * }
 * ```
 *
 * Java code should continue using [Async] directly — no parallel bridge API needed.
 * Convert Java callers to Kotlin when touching those files.
 */
object AppScope : CoroutineScope {

    /** Background dispatcher wrapping the existing low-priority cached thread pool. */
    @JvmField
    val background = java.util.concurrent.Executors
        .newCachedThreadPool(Async.LOW_PRIORITY_FACTORY)
        .asCoroutineDispatcher()

    /** Shorthand for [Dispatchers.Main] (convenience for `withContext`). */
    @JvmField
    val main = Dispatchers.Main

    override val coroutineContext =
        SupervisorJob() + background
}
