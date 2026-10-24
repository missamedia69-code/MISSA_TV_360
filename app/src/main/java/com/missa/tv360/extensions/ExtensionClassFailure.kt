package com.missa.tv360.extensions

/** Résultat purement syntaxique d'un échec de résolution de classe. */
internal data class ClassFailureClassification(
    val type: ExtensionErrorType,
    val missingClass: String? = null,
)

/**
 * Distingue la classe du manifeste d'une dépendance référencée par celle-ci.
 * Aucun Android framework n'est utilisé ici afin de tester les formats JVM/ART.
 */
internal fun classifyClassFailure(
    manifestClassName: String,
    error: Throwable,
): ClassFailureClassification {
    val missing = generateSequence(error) { it.cause }.take(12)
        .firstOrNull { it is ClassNotFoundException || it is NoClassDefFoundError }
        ?: return ClassFailureClassification(ExtensionErrorType.LOAD_FAILED)
    val missingClass = extractMissingClassName(missing)
        ?: return ClassFailureClassification(ExtensionErrorType.LOAD_FAILED)
    return if (missingClass == manifestClassName) {
        ClassFailureClassification(ExtensionErrorType.BAD_MANIFEST, missingClass)
    } else {
        ClassFailureClassification(ExtensionErrorType.MISSING_DEPENDENCY, missingClass)
    }
}

/** Formats rencontrés dans PathClassLoader et les erreurs de résolution ART. */
internal fun extractMissingClassName(error: Throwable): String? {
    val message = error.message.orEmpty()
    val candidates = listOfNotNull(
        Regex("L([A-Za-z0-9_$/.-]+);").find(message)?.groupValues?.get(1),
        Regex("""["']([A-Za-z_$][A-Za-z0-9_$]*(?:[./][A-Za-z_$][A-Za-z0-9_$]*)+)["']""")
            .find(message)?.groupValues?.get(1),
        message.trim().takeIf {
            Regex("[A-Za-z_$][A-Za-z0-9_$]*(?:[./][A-Za-z_$][A-Za-z0-9_$]*)+")
                .matches(it)
        },
    )
    return candidates.firstOrNull()
        ?.replace('/', '.')
        ?.takeIf(String::isNotBlank)
}
