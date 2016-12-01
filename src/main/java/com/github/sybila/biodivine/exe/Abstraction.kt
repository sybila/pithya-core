package com.github.sybila.biodivine.exe

import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import com.github.sybila.ode.model.toBio
import java.io.File

fun main(args: Array<String>) {
    try {
        if (args.isEmpty()) throw IllegalArgumentException("Missing argument: .bio file")
        val fast = if (args.size > 1 && args[1].contains("true")) true else false
        val cut = if (args.size > 2 && args[2].contains("true")) true else false
        val model = Parser().parse(File(args[0])).computeApproximation(fast, cut).toBio()
        println(model)
    } catch (e: Exception) {
        System.err.println("${e.message} (${e.javaClass.name})")
    }
}