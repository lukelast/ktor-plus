package net.ghue.ktp.ktor.plugin

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.withContext

@OptIn(ExperimentalContracts::class)
suspend inline fun <T> withIoContext(noinline block: suspend CoroutineScope.() -> T): T {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    return withContext(context = Dispatchers.IO + MDCContext(), block = block)
}
