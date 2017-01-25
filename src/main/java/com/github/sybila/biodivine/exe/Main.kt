package com.github.sybila.biodivine.exe

import com.github.sybila.checker.Checker
import com.github.sybila.checker.SequentialChecker
import com.github.sybila.checker.StateMap
import com.github.sybila.checker.channel.connectWithSharedMemory
import com.github.sybila.checker.partition.asUniformPartitions
import com.github.sybila.huctl.Expression
import com.github.sybila.huctl.Formula
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.huctl.fold
import com.github.sybila.ode.generator.NodeEncoder
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleOdeModel
import com.github.sybila.ode.generator.smt.local.Z3OdeFragment
import com.github.sybila.ode.generator.smt.local.Z3Params
import com.github.sybila.ode.model.OdeModel
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import com.github.sybila.ode.model.toBio
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.microsoft.z3.Expr
import org.kohsuke.args4j.CmdLineException
import org.kohsuke.args4j.CmdLineParser
import org.kohsuke.args4j.Option
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.*

enum class ResultType { HUMAN, JSON }
enum class LogLevel { NONE, INFO, VERBOSE, DEBUG }

internal fun String.readStream(): PrintStream? = when (this) {
    "none" -> null
    "stdout" -> System.out
    "stderr" -> System.err
    else -> PrintStream(File(this).apply { this.createNewFile() }.outputStream())
}

data class MainConfig(
        @field:Option(
                name = "-m", aliases = arrayOf("--model"),
                usage = "Path to the .bio file from which the model should be loaded."
        )
        var model: File? = null,
        @field:Option(
                name = "-p", aliases = arrayOf("--property"),
                usage = "Path to the .ctl file from which the properties should be loaded."
        )
        var property: File? = null,
        @field:Option(
                name = "-ro", aliases = arrayOf("--result-output"),
                usage = "Name of stream to which the results should be printed. Filename, stdout, stderr or null."
        )
        var resultOutput: String = "stdout",
        @field:Option(
                name = "-r", aliases = arrayOf("--result"),
                usage = "Type of result format. Accepted values: human, json."
        )
        var resultType: ResultType = ResultType.HUMAN,
        @field:Option(
                name = "-lo", aliases = arrayOf("--log-output"),
                usage = "Name of stream to which logging info should be printed. Filename, stdout, stderr or null."
        )
        var logOutput: String = "stdout",
        @field:Option(
                name = "-l", aliases = arrayOf("--log"),
                usage = "Log level: none, info, verbose, debug."
        )
        var logLevel: LogLevel = LogLevel.VERBOSE,
        @field:Option(
                name = "--parallelism",
                usage = "Recommended number of used threads."
        )
        var parallelism: Int = Runtime.getRuntime().availableProcessors(),
        @field:Option(
                name = "--z3-path",
                usage = "Path to z3 executable."
        )
        var z3Path: String = "z3",
        @field:Option(
                name = "--fast-approximation",
                usage = "Use faster, but less precise version of linear approximation."
        )
        var fastApproximation: Boolean = false,
        @field:Option(
                name = "--cut-to-range",
                usage = "Thresholds above and below original variable range will be discarded."
        )
        var cutToRange: Boolean = false,
        @field:Option(
                name = "--disable-self-loops",
                usage = "Disable selfloop creation in transition system."
        )
        var disableSelfLoops: Boolean = false
    )

fun main(args: Array<String>) {
    val config = MainConfig()
    val parser = CmdLineParser(config)

    try {
        parser.parseArgument(*args)

        val modelFile = config.model ?: throw IllegalArgumentException("Missing model file.")
        val propFile = config.property ?: throw IllegalArgumentException("Missing property file.")

        val model = Parser().parse(modelFile).computeApproximation(
                fast = config.fastApproximation, cutToRange = config.cutToRange
        )
        val properties = HUCTLParser().parse(propFile, onlyFlagged = true)

        //check missing thresholds
        val thresholdError = checkMissingThresholds(properties.values.toList(), model)
        if (thresholdError != null) {
            throw IllegalStateException(thresholdError)
        }

        val isRectangular = model.variables.all {
            it.equation.map { it.paramIndex }.filter { it >= 0 }.toSet().size <= 1
        }

        if (isRectangular) {
            rectangleMain(config, model, properties)
        } else {
            z3Main(config, model, properties)
        }
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

internal fun Int.prettyPrint(model: OdeModel, encoder: NodeEncoder): String = "State($this)${
model.variables.map { it.thresholds }.mapIndexed { dim, thresholds ->
    val t = encoder.coordinate(this, dim)
    "[${thresholds[t]}, ${thresholds[t+1]}]"
}.joinToString()}"

internal fun Int.expand(model: OdeModel, encoder:NodeEncoder): State {
    return State(id = this.toLong(), bounds = model.variables.mapIndexed { i, variable ->
        val c = encoder.coordinate(this, i)
        listOf(variable.thresholds[c], variable.thresholds[c+1])
    })
}

internal class ResultSet(
        val variables: List<String>,
        val parameters: List<String>,
        val thresholds: List<List<Double>>,
        val states: List<State>,
        val type: String,
        @SerializedName("parameter_values")
        val parameterValues: List<Any>,
        @SerializedName("parameter_bounds")
        val parameterBounds: List<List<Double>>,
        val results: List<Result>
)

internal class Result(
        val formula: String,
        val data: List<List<Int>>
)

internal class State(
        val id: Long,
        val bounds: List<List<Double>>
)

private fun Formula.invalidThresholds(model: OdeModel): Map<String, Set<Double>> {
    return this.fold<List<Pair<String, Double>>>({
        if (this is Formula.Atom.Float) {
            val left = this.left
            val right = this.right
            val (name, value) = if (left is Expression.Constant && right is Expression.Variable) {
                right.name to left.value
            } else if (left is Expression.Variable && right is Expression.Constant) {
                left.name to right.value
            } else throw IllegalArgumentException("Unsupported proposition type: $this")
            val thresholds = model.variables.find { it.name == name }?.thresholds
                ?: throw IllegalArgumentException("Unknown variable: $name")
            val threshold = thresholds.find { it == value }
            if (threshold != null) listOf() else listOf(name to value)
        } else listOf()
    }, { it }, { a, b -> a + b })
            .groupBy { it.first }
            .mapValues { it.value.map { it.second }.toSet() }
}

private fun <K, V> Map<K, V>.mergeReduce(other: Map<K, V>, reduce: (V, V) -> V = { a, b -> b }): Map<K, V> {
    val result = LinkedHashMap<K, V>(this.size + other.size)
    result.putAll(this)
    other.forEach { e ->
        val existing = result[e.key]

        if (existing == null) {
            result[e.key] = e.value
        } else {
            result[e.key] = reduce(e.value, existing)
        }
    }

    return result
}

fun checkMissingThresholds(formulas: List<Formula>, model: OdeModel): String? {
    val missing = formulas.map { it.invalidThresholds(model) }.fold(mapOf<String, Set<Double>>()) { acc, map ->
        acc.mergeReduce(map) { a, b -> a + b }
    }
    return if (missing.isNotEmpty()) {
        //report missing thresholds
        "Missing thresholds: " + missing.entries.map { "${it.key}: ${it.value.joinToString()}" }.joinToString(separator = "; ")
    } else null
}
