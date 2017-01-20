package com.github.sybila.biodivine.exe

import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import com.github.sybila.ode.model.toBio
import java.io.File

fun main(shinyArgs: Array<String>) {
    startShiny(shinyArgs) { args ->
        //TODO: This needs to be much more robust!
        if (args.isEmpty()) throw IllegalArgumentException("Missing argument: .bio file")
        val fast = args.size > 1 && args[1].contains("true")
        val cut = args.size > 2 && args[2].contains("true")
        val model = Parser().parse(File(args[0])).computeApproximation(fast, cut).toBio()
        println(model)
    }
}