package com.missa.tv360.data.source

/**
 * Santé observée d'une source, conservée en mémoire pour la session.
 *
 * Trois échecs consécutifs mettent la source en quarantaine quelques minutes :
 * une source morte ne doit pas ralentir chaque ouverture d'écran. C'est le
 * garde-fou qui manque à un agrégateur naïf — et le seul moyen de savoir
 * qu'une source est tombée sans surveiller les logs.
 */
data class SourceHealth(
    val sourceId: String,
    val successes: Int = 0,
    val failures: Int = 0,
    val consecutiveFailures: Int = 0,
    val lastLatencyMs: Long = 0,
    val lastSuccessAt: Long = 0,
    val lastFailureAt: Long = 0,
    val lastError: String? = null,
) {
    val total: Int get() = successes + failures

    val errorRate: Double get() = if (total == 0) 0.0 else failures.toDouble() / total

    val isHealthy: Boolean get() = consecutiveFailures == 0
}

/** Registre global de santé des sources (thread-safe, non persistant). */
object SourceHealthTracker {
    private const val FAILURE_THRESHOLD = 3
    private const val COOLDOWN_MS = 5 * 60_000L
    private const val MAX_ERROR_LENGTH = 180

    private val entries = LinkedHashMap<String, SourceHealth>()

    @Synchronized
    fun recordSuccess(sourceId: String, latencyMs: Long): SourceHealth =
        update(sourceId) { current ->
            current.copy(
                successes = current.successes + 1,
                consecutiveFailures = 0,
                lastLatencyMs = latencyMs,
                lastSuccessAt = System.currentTimeMillis(),
            )
        }

    @Synchronized
    fun recordFailure(sourceId: String, error: Throwable?, latencyMs: Long): SourceHealth =
        update(sourceId) { current ->
            current.copy(
                failures = current.failures + 1,
                consecutiveFailures = current.consecutiveFailures + 1,
                lastLatencyMs = latencyMs,
                lastFailureAt = System.currentTimeMillis(),
                lastError = describe(error),
            )
        }

    /** Vrai si la source vient d'échouer plusieurs fois de suite. */
    @Synchronized
    fun isCoolingDown(sourceId: String, now: Long = System.currentTimeMillis()): Boolean {
        val entry = entries[sourceId] ?: return false
        if (entry.consecutiveFailures < FAILURE_THRESHOLD) return false
        return now - entry.lastFailureAt < COOLDOWN_MS
    }

    @Synchronized
    fun snapshot(): List<SourceHealth> = entries.values.toList()

    @Synchronized
    fun reset(sourceId: String? = null) {
        if (sourceId == null) entries.clear() else entries.remove(sourceId)
    }

    private fun update(sourceId: String, block: (SourceHealth) -> SourceHealth): SourceHealth {
        val next = block(entries[sourceId] ?: SourceHealth(sourceId))
        entries[sourceId] = next
        return next
    }

    private fun describe(error: Throwable?): String? {
        if (error == null) return null
        val message = error.message?.replace('\n', ' ')?.trim()
        return (message?.takeIf { it.isNotEmpty() } ?: error.javaClass.simpleName)
            .take(MAX_ERROR_LENGTH)
    }
}
