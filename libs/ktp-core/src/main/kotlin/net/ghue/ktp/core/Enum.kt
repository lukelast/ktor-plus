package net.ghue.ktp.core

/** Converts a SCREAMING_SNAKE_CASE enum constant name to lowerCamelCase. */
fun Enum<*>.toCamelCase() =
    this.name
        .split("_")
        .map(String::lowercase)
        .mapIndexed { index, word ->
            if (0 < index) word.replaceFirstChar(Char::titlecase) else word
        }
        .joinToString("")
