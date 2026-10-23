package com.lagradost.cloudstream3

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

/** Version binaire historique utilisée par BuzzMonclick (List, Function2) -> List. */
fun <A, R> List<A>.apmap(transform: suspend (A) -> R): List<R> = runBlocking {
    map { async { transform(it) } }.map { it.await() }
}
