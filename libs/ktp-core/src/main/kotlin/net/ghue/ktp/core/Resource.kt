package net.ghue.ktp.core

object Resource {
    fun read(name: String): String {
        return readOrNull(name) ?: error("Unable to find resource file named: $name")
    }

    fun readOrNull(name: String): String? {
        val path = if (name.startsWith("/")) name else "/$name"
        return javaClass.getResource(path)?.readText()
    }
}
