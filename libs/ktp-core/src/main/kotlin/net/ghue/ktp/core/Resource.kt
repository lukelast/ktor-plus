package net.ghue.ktp.core

import java.net.URL

object Resource {
    fun read(name: String): String {
        return readOrNull(name) ?: error("Unable to find resource file named: $name")
    }

    fun readOrNull(name: String): String? {
        return urlOrNull(name)?.readText()
    }

    /** The classpath resource at [name], with or without a leading slash, or null if absent. */
    fun urlOrNull(name: String): URL? {
        val path = if (name.startsWith("/")) name else "/$name"
        return javaClass.getResource(path)
    }
}
