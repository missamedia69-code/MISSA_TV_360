package com.lagradost.cloudstream3.utils

/** Petit événement synchrone ; invoke(value) correspond à la référence du DEX. */
class Event<T> {
    private val observers = mutableListOf<(T) -> Unit>()

    operator fun invoke(value: T) {
        observers.toList().forEach { it(value) }
    }

    operator fun plusAssign(observer: (T) -> Unit) {
        observers += observer
    }

    operator fun minusAssign(observer: (T) -> Unit) {
        observers -= observer
    }
}
