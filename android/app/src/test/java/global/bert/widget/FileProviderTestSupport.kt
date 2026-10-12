package global.bert.widget

/**
 * FileProvider caches each authority's paths in a static map. Robolectric gives every test a new data directory in
 * the same JVM, so a second test that shares a card would be handed the first test's cache path. Tests that share
 * cards forget the cached paths before and after.
 */
internal fun forgetFileProviderPaths() {
    val cache = androidx.core.content.FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
    synchronized(cache.get(null) as Any) { (cache.get(null) as MutableMap<*, *>).clear() }
}
