package net.ghue.ktp.core

/** A lazy property delegate, but with an expiration time. */
// class LazyWithExpiration<T>(
//    timeout: Duration,
//    loader: () -> T
// ) : ReadOnlyProperty<Any, T> {
//    private val supplier: Supplier<T> = Suppliers.memoizeWithExpiration(
//        loader,
//        timeout.inWholeMilliseconds,
//        TimeUnit.MILLISECONDS
//    )
//
//    override fun getValue(thisRef: Any, property: KProperty<*>): T = supplier.get()
// }
