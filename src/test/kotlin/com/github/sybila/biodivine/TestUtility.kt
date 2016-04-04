package com.github.sybila.biodivine

import java.io.File

fun <R> withFile(content: String, action: (File) -> R) {
    val file = File("tmp")
    file.delete()
    try {
        file.createNewFile()
        file.bufferedWriter().use {
            it.write(content)
        }
        action(file)
    } finally {
        file.delete()
    }
}