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

import dev.esoc.esochan.api.interfaces.CancellableTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Creates a [CoroutineScope] that cancels when the given [CancellableTask] is cancelled.
 *
 * A background coroutine polls [CancellableTask.isCancelled] every 200 ms and cancels
 * the scope's [Job] when it returns true. This bridges existing Java callers that use
 * the volatile-boolean cancellation pattern with new coroutine-based code.
 *
 * **Temporary bridge** — remove once callers migrate to structured concurrency.
 */
fun CancellableTaskScope(task: CancellableTask): CoroutineScope {
    val job = Job(parent = AppScope.coroutineContext[Job])
    val scope = CoroutineScope(job + AppScope.background)

    scope.launch {
        while (!task.isCancelled) {
            delay(200)
        }
        job.cancel()
    }

    return scope
}
