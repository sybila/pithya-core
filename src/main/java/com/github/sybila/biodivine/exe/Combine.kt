package com.github.sybila.biodivine.exe

import com.github.sybila.ctl.CTLParser
import com.github.sybila.ctl.normalize
import com.github.sybila.ctl.optimize
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.toBio
import com.google.gson.Gson
import java.io.File

fun main(args: Array<String>) {
    try {
        if (args.isEmpty()) throw IllegalArgumentException("Missing argument: .bio file")
        if (args.size < 2) throw IllegalArgumentException("Mussing argument: .ctl file")
        val propertyFile = File(args[1])
        val modelFile = File(args[0])
        val formulas = CTLParser().parse(propertyFile).entries
                .filter { it.key.endsWith("?") }
                .map { it.key to it.value.normalize().optimize().toString() }

        val model = Parser().parse(modelFile)

        val gson = Gson()
        println(gson.toJson(model.toBio() to formulas))
    } catch (e: Exception) {
        System.err.println("${e.message} (${e.javaClass.name})")
    }
}