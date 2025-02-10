package no.nav.emottak.util

import arrow.fx.coroutines.ExitCase
import arrow.fx.coroutines.ResourceScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import no.nav.emottak.log
import kotlin.coroutines.CoroutineContext

/** Installs a coroutine scope as a resource **/
suspend fun ResourceScope.coroutineScope(context: CoroutineContext): CoroutineScope {
    val currentContext = currentCoroutineContext()
    val job = currentContext[Job]?.let { Job(it) } ?: Job()
    return install({ CoroutineScope(context + currentContext + job) }) { _, exitCase ->
        log.info("Exit case: $exitCase")
        when (exitCase) {
            ExitCase.Completed -> job.cancel()
            is ExitCase.Cancelled -> job.cancel(exitCase.exception)
            is ExitCase.Failure -> job.cancel("Resource failed, so cancelling associated scope", exitCase.failure)
        }
        job.join()
    }
}
