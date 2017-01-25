package com.github.sybila.biodivine.exe

import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import com.github.sybila.ode.model.toBio
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option

data class ApproximationConfig(
    @field:Option(
            name = "--fast",
            usage = "Use faster but less precise approximation method."
    )
    var fast: Boolean = false,
    @field:Option(
            name = "--cut-to-range",
            usage = "Thresholds above and below original variable range will be discarded."
    )
    var cutToRange: Boolean = false
)

fun main(args: Array<String>) {
    val config = ApproximationConfig()
    val parser = CmdLineParser(config)

    try {
        parser.parseArgument(*args)

        println(Parser().parse(System.`in`).computeApproximation(
                fast = config.fast,
                cutToRange = config.cutToRange
        ).toBio())
    } catch (e : CmdLineException) {
        // if there's a problem in the command line,
        // you'll get this exception. this will report
        // an error message.
        System.err.println(e.message);
        System.err.println("pithyaApproximation [options...]");
        // print the list of available options
        parser.printUsage(System.err);
        System.err.println();

        return;
    }
}