package net.ghue.ktp.core

/** Convert an enum constant name to camel case. */
fun Enum<*>.toCamelCase() =
    this.name
        .split("_")
        .map(String::lowercase)
        .mapIndexed { index, word ->
            if (0 < index) word.replaceFirstChar(Char::titlecase) else word
        }
        .joinToString("")
