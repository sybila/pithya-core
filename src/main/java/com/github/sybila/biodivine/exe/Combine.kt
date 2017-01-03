package com.github.sybila.biodivine.exe

import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.toBio
import com.google.gson.Gson
import java.io.File

fun main(args: Array<String>) {
    try {
        if (args.isEmpty()) throw IllegalArgumentException("Missing argument: .bio file")
        if (args.size < 2) throw IllegalArgumentException("Missing argument: .ctl file")
        val propertyFile = File(args[1])
        val modelFile = File(args[0])
        val formulas = HUCTLParser().parse(propertyFile, onlyFlagged = true)
        val formulaString = formulas.entries
                .map { it.key to it.value.toString() }

        val model = Parser().parse(modelFile)

        //check missing thresholds
        val thresholdError = checkMissingThresholds(formulas.values.toList(), model)
        if (thresholdError != null) {
            System.err.println(thresholdError)
            return
        }

        val json = Gson()
        println(json.toJson(model.toBio() to formulaString))
    } catch (e: Exception) {
        System.err.println("${e.message} (${e.javaClass.name})")
    }
}