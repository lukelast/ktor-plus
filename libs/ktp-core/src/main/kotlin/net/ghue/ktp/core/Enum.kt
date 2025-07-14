package net.ghue.ktp.core

/** Turn enum name to camel case. */
fun Enum<*>.enumToCamelCase() =
    this.name
        .split("_")
        .map(String::lowercase)
        .mapIndexed { index, word ->
            if (0 < index) word.replaceFirstChar(Char::titlecase) else word
        }
        .joinToString("")
