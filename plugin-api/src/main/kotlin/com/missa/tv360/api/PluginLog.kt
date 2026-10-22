package com.missa.tv360.api

/**
 * Journalisation des extensions.
 *
 * Le module n'a aucune dépendance (donc pas d'`android.util.Log`) : c'est
 * l'application qui branche son propre écrivain de log au démarrage via
 * [sink]. Tant que rien n'est branché (tests JVM, outils), la sortie part sur
 * la console.
 */
object PluginLog {

    /** Branché par l'application : (niveau, message) -> log natif. */
    @Volatile
    var sink: ((level: String, message: String) -> Unit)? = null

    fun i(message: String) = emit("I", message, null)

    fun e(message: String, throwable: Throwable? = null) = emit("E", message, throwable)

    private fun emit(level: String, message: String, throwable: Throwable?) {
        val line = if (throwable == null) message else "$message :: ${throwable.javaClass.simpleName}: ${throwable.message}"
        val current = sink
        if (current != null) {
            current(level, line)
        } else {
            println("[$level] $line")
        }
    }
}
