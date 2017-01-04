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
import com.github.sybila.ode.generator.smt.Z3OdeFragment
import com.github.sybila.ode.generator.smt.Z3Params
import com.github.sybila.ode.model.Evaluable
import com.github.sybila.ode.model.OdeModel
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.RampApproximation
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import com.google.gson.annotations.SerializedName
import com.microsoft.z3.Expr
import java.io.File
import java.lang.management.ManagementFactory
import java.util.*

fun main(args: Array<String>) {
    try {
        if (args.isEmpty()) throw IllegalArgumentException("Missing argument: .json config file")
        val cores = if (args.size > 1) args[1].toInt() else 1
        val pid = ManagementFactory.getRuntimeMXBean().name.takeWhile { it != '@' }
        System.err.println("PID: $pid")
        System.err.flush()
        val file = File(args[0]).readText()
        val builder = GsonBuilder()

        builder.registerTypeAdapter(Evaluable::class.java, InstanceCreator<Evaluable> {
            RampApproximation(0, doubleArrayOf(), doubleArrayOf())  //no other evaluables are supported
        })

        val parser = HUCTLParser()
        val modelParser = Parser()
        val gson = builder.create()
        //val typeToken = object : TypeToken<Pair<Model, List<InputPair>>>() {}.type
        val data: MainPair = gson.fromJson(file, MainPair::class.java)


        val model = modelParser.parse(data.first)
        val formulas = data.second.associateBy({it.first}, { parser.formula(it.second) })

        //check missing thresholds
        val thresholdError = checkMissingThresholds(formulas.values.toList(), model)
        if (thresholdError != null) {
            System.err.println(thresholdError)
            return
        }

        val isRectangular = model.variables.all {
            it.equation.map { it.paramIndex }.filter { it >= 0 }.toSet().size <= 1
        }

        if (isRectangular) {
            val models = (0 until cores).map { RectangleOdeModel(model) }.asUniformPartitions()
            Checker(models.connectWithSharedMemory()).use { checker ->
                System.err.println("Verification started...")
                val r = checker.verify(formulas)
                System.err.println("Verification finished. Printing results...")
                printRectResults(model, r)
            }
        } else {
            val fragment = Z3OdeFragment(model)
            SequentialChecker(fragment).use { checker ->
                fragment.run {
                    System.err.println("Verification started...")
                    val r = checker.verify(formulas)
                    System.err.println("Verification finished. Printing results...")
                    printSMTResults(model, r)
                }
            }
        }

        System.err.println("!!DONE!!")

    } catch (e: Exception) {
        e.printStackTrace()
        System.err.println("${e.message} (${e.javaClass.name})")
    }
}

private fun printRectResults(model: OdeModel, result: Map<String, List<StateMap<Set<Rectangle>>>>) {
    val states = ArrayList<Int>()
    val params = ArrayList<Set<Rectangle>>()
    val map = ArrayList<Result>()
    for ((f, r) in result) {
        val rMap = ArrayList<List<Int>>()
        for (partitionResult in r) {
            for ((s, p) in partitionResult.entries()) {
                val ii = states.indexOf(s)
                val i = if (ii < 0) {
                    states.add(s)
                    states.size - 1
                } else ii
                val jj = params.indexOf(p)
                val j = if (jj < 0) {
                    params.add(p)
                    params.size - 1
                } else jj
                rMap.add(listOf(i, j))
            }
        }
        map.add(Result(f, rMap))
    }
    val coder = NodeEncoder(model)
    val r = ResultSet(
            variables = model.variables.map { it.name },
            parameters = model.parameters.map { it.name },
            thresholds = model.variables.map { it.thresholds },
            states = states.map { it.expand(model, coder) },
            type = "rectangular",
            results = map,
            parameterValues = params.map {
                it.map { it.asIntervals() }
            },
            parameterBounds = model.parameters.map { listOf(it.range.first, it.range.second) }
    )
    val gson = Gson()
    println(gson.toJson(r))
}

private fun Z3OdeFragment.printSMTResults(model: OdeModel, result: Map<String, StateMap<Z3Params>>) {
    val states = ArrayList<Int>()
    val params = ArrayList<Z3Params>()
    val map = ArrayList<Result>()
    for ((f, r) in result) {
        val rMap = ArrayList<List<Int>>()
        for ((s, p) in r.entries()) {
            val ii = states.indexOf(s)
            val i = if (ii < 0) {
                states.add(s)
                states.size - 1
            } else ii
            val jj = params.indexOf(p)
            val j = if (jj < 0) {
                p.minimize()
                params.add(p)
                params.size - 1
            } else jj
            rMap.add(listOf(i, j))
        }
        map.add(Result(f, rMap))
    }
    val coder = NodeEncoder(model)
    val r = ResultSet(
            variables = model.variables.map { it.name },
            parameters = model.parameters.map { it.name },
            thresholds = model.variables.map { it.thresholds },
            states = states.map { it.expand(model, coder) },
            type = "smt",
            results = map,
            parameterValues = params.map {
                SMTResult(
                        smtlib2Formula = it.formula.toString(),
                        Rexpression = it.formula.toR()
                )
            },
            parameterBounds = model.parameters.map { listOf(it.range.first, it.range.second) }
    )
    val printer = Gson()
    println(printer.toJson(r))
}

private fun Int.expand(model: OdeModel, encoder:NodeEncoder): State {
    return State(id = this.toLong(), bounds = model.variables.mapIndexed { i, variable ->
        val c = encoder.coordinate(this, i)
        listOf(variable.thresholds[c], variable.thresholds[c+1])
    })
}

private class ResultSet(
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

private class Result(
        val formula: String,
        val data: List<List<Int>>
)

private class State(
        val id: Long,
        val bounds: List<List<Double>>
)

private class SMTResult(
        val smtlib2Formula: String,
        val Rexpression: String
)

private class InputPair(
        val first: String,  //name
        val second: String  //formula
)

private class MainPair(
        val first: String,
        val second: List<InputPair>
)

private fun Expr.toR(): String {
    return when {
        //boolean
        this.isAnd -> "(" + this.args.map(Expr::toR).joinToString(separator = " & ") + ")"
        this.isOr -> "(" + this.args.map(Expr::toR).joinToString(separator = " | ") + ")"
        this.isAdd -> "("+ this.args.map(Expr::toR).joinToString(separator = " + ")+")"
        this.isSub -> "("+ this.args.map(Expr::toR).joinToString(separator = " - ")+")"
        this.isMul -> "("+ this.args.map(Expr::toR).joinToString(separator = " * ")+")"
        this.isDiv -> "("+ this.args.map(Expr::toR).joinToString(separator = " / ")+")"
        this.isGT -> "(${this.args[0].toR()} > ${this.args[1].toR()})"
        this.isGE -> "(${this.args[0].toR()} >= ${this.args[1].toR()})"
        this.isLT -> "(${this.args[0].toR()} < ${this.args[1].toR()})"
        this.isLE -> "(${this.args[0].toR()} <= ${this.args[1].toR()})"
        this.isNot -> "(!${this.args[0].toR()})"
        this.isTrue -> "TRUE"
        this.isFalse -> "FALSE"
        this.isConst -> "ip\$$this"
        this.isInt || this.isReal -> this.toString()
        else -> throw IllegalStateException("Unsupported formula: $this")
    }
}

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
