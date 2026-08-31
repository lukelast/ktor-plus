package net.ghue.ktp.core

import java.nio.file.Path

fun Path.removeFirstFolder(folder: String): Path =
    if (nameCount > 0 && getName(0).toString() == folder) {
        subpath(1, nameCount)
    } else {
        this
    }
