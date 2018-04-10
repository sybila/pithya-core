package com.github.sybila.biodivine.exe

import com.github.sybila.ode.generator.rect.RectangleOdeModel
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser

/**
 * Dump transition system without parameters.
 */

fun runDumpStates(args: Array<String>) {
    val config = ApproximationConfig()
    val parser = CmdLineParser(config)

    try {
        parser.parseArgument(*args)

        val model = Parser().parse(System.`in`).computeApproximation(
                fast = config.fast,
                cutToRange = config.cutToRange
        )

        if (model.parameters.isNotEmpty()) {
            println("====== WARNING ======")
            println("Model contains parameters, but these are not dumped!")
        }

        RectangleOdeModel(model, true).run {
            println("states:")
            for (s in 0 until stateCount) {
                println("- id: $s")
                println("  next: ${s
                        .successors(true)
                        .asSequence()
                        .joinToString(separator = ", ", prefix = "[", postfix = "]") {
                    it.target.toString()
                }}")
            }
        }

    } catch (e : CmdLineException) {
        // if there's a problem in the command line,
        // you'll get this exception. this will report
        // an error message.
        System.err.println(e.message)
        System.err.println("pithyaApproximation [options...]")
        // print the list of available options
        parser.printUsage(System.err)
        System.err.println()

        return
    }
}

fun main(args: Array<String>) {
    runDumpStates(args)
}