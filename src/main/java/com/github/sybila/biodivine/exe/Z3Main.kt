package com.github.sybila.biodivine.exe

import com.github.sybila.checker.Checker
import com.github.sybila.checker.CheckerStats
import com.github.sybila.checker.SequentialChecker
import com.github.sybila.checker.StateMap
import com.github.sybila.checker.channel.connectWithSharedMemory
import com.github.sybila.checker.partition.asUniformPartitions
import com.github.sybila.checker.solver.SolverStats
import com.github.sybila.huctl.Formula
import com.github.sybila.ode.generator.NodeEncoder
import com.github.sybila.ode.generator.smt.local.Z3OdeFragment
import com.github.sybila.ode.generator.smt.local.Z3Params
import com.github.sybila.ode.generator.smt.remote.bridge.SMT
import com.github.sybila.ode.generator.smt.remote.bridge.readSMT
import com.github.sybila.ode.model.OdeModel
import com.google.gson.Gson
import com.microsoft.z3.Expr
import java.io.PrintStream
import java.util.*

fun z3Main(config: MainConfig, model: OdeModel, properties: Map<String, Formula>) {

    val logStream: PrintStream? = config.logOutput.readStream()
    val resultStream: PrintStream? = config.resultOutput.readStream()

    if (config.logLevel >= LogLevel.INFO) {
        SolverStats.reset(logStream)
    }

    if (config.logLevel >= LogLevel.VERBOSE) {
        CheckerStats.reset(logStream)
    }

    if (config.parallelism == 1) {
        val fragment = Z3OdeFragment(model, createSelfLoops = !config.disableSelfLoops)

        SequentialChecker(fragment).use { checker ->
            fragment.run {
                val r = checker.verify(properties)
                logStream?.println("Verification finished. Processing results...")

                if (config.resultType != ResultType.JSON) {
                    val encoder = NodeEncoder(model)
                    for ((property, valid) in r) {
                        resultStream?.println(property)
                        for ((s, v) in valid.entries()) {
                            resultStream?.println("${s.prettyPrint(model, encoder)} -> Params(${v.prettyPrint()})")
                        }
                    }
                } else {
                    resultStream?.println(printZ3Results(model, r))
                }
            }
        }
    } else {
        val models = (0 until config.parallelism).map {
            com.github.sybila.ode.generator.smt.remote.Z3OdeFragment(
                    model, createSelfLoops = !config.disableSelfLoops
            )
        }.asUniformPartitions()

        Checker(models.connectWithSharedMemory()).use { checker ->
            val r = checker.verify(properties)
            logStream?.println("Verification finished. Processing results...")

            if (config.resultType != ResultType.JSON) {
                val encoder = NodeEncoder(model)
                for ((property, valid) in r) {
                    resultStream?.println(property)
                    valid.zip(models).forEach {
                        it.second.run {
                            it.first.entries().forEach {
                                resultStream?.println("${it.first.prettyPrint(model, encoder)} -> Params(${it.second.prettyPrint()})")
                            }
                        }
                    }
                }
            } else {
                resultStream?.println(printSMTResult(model, r))
            }
        }
    }

    if (config.logLevel >= LogLevel.INFO) {
        SolverStats.printGlobal()
    }

    if (config.logLevel >= LogLevel.VERBOSE) {
        CheckerStats.printGlobal()
    }

    logStream?.close()
    resultStream?.close()

}

internal class SMTResult(
        val smtlib2Formula: String,
        val Rexpression: String
)

internal fun Expr.toR(): String {
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


private fun printZ3Results(model: OdeModel, result: Map<String, StateMap<Z3Params>>): String {
    val statesIndexMapping = HashMap<Int, Int>()
    val states = ArrayList<Int>()
    val paramsIndexMapping = HashMap<Z3Params, Int>()
    val params = ArrayList<Z3Params>()
    val map = ArrayList<Result>()
    for ((f, r) in result) {
        val rMap = ArrayList<List<Int>>()
        for ((s, p) in r.entries()) {
            val stateIndex = statesIndexMapping.computeIfAbsent(s) {
                states.add(s)
                states.size -1
            }
            val paramIndex = paramsIndexMapping.computeIfAbsent(p) {
                params.add(p)
                params.size - 1
            }
            rMap.add(listOf(stateIndex, paramIndex))
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
    return printer.toJson(r)
}

private fun printSMTResult(model: OdeModel, result: Map<String, List<StateMap<com.github.sybila.ode.generator.smt.remote.Z3Params>>>): String {
    val statesIndexMapping = HashMap<Int, Int>()
    val states = ArrayList<Int>()
    val paramsIndexMapping = HashMap<com.github.sybila.ode.generator.smt.remote.Z3Params, Int>()
    val params = ArrayList<com.github.sybila.ode.generator.smt.remote.Z3Params>()
    val map = ArrayList<Result>()
    for ((f, r) in result) {
        val rMap = ArrayList<List<Int>>()
        for (partitionResult in r) {
            for ((s, p) in partitionResult.entries()) {
                val stateIndex = statesIndexMapping.computeIfAbsent(s) {
                    states.add(s)
                    states.size -1
                }
                val paramIndex = paramsIndexMapping.computeIfAbsent(p) {
                    params.add(p)
                    params.size - 1
                }
                rMap.add(listOf(stateIndex, paramIndex))
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
            type = "smt",
            results = map,
            parameterValues = params.map {
                SMTResult(
                        smtlib2Formula = it.formula,
                        Rexpression = it.formula.readSMT().toR(model)
                )
            },
            parameterBounds = model.parameters.map { listOf(it.range.first, it.range.second) }
    )
    val printer = Gson()
    return printer.toJson(r)
}


internal fun SMT.toR(model: OdeModel): String {
    val paramNames = model.parameters.map { it.name }
    return when(this) {
        is SMT.Terminal -> when(this.data) {
            "true" -> "TRUE"
            "false" -> "FALSE"
            in paramNames -> "ip\$${this.data}"
            else -> this.data
        }
        is SMT.Expression -> when (this.funName) {
            "and" -> "(" + this.funArgs.map { it.toR(model) }.joinToString(separator = " & ") + ")"
            "or" -> "(" + this.funArgs.map { it.toR(model) }.joinToString(separator = " | ") + ")"
            "+" -> "(" + this.funArgs.map { it.toR(model) }.joinToString(separator = " + ") + ")"
            "-" -> "(" + this.funArgs.map { it.toR(model) }.joinToString(separator = " - ") + ")"
            "*" -> "(" + this.funArgs.map { it.toR(model) }.joinToString(separator = " * ") + ")"
            "/" -> "(" + this.funArgs.map { it.toR(model) }.joinToString(separator = " / ") + ")"
            ">" -> "(${this.funArgs[0].toR(model)} > ${this.funArgs[1].toR(model)})"
            ">=" -> "(${this.funArgs[0].toR(model)} >= ${this.funArgs[1].toR(model)})"
            "<" -> "(${this.funArgs[0].toR(model)} < ${this.funArgs[1].toR(model)})"
            "<=" -> "(${this.funArgs[0].toR(model)} <= ${this.funArgs[1].toR(model)})"
            "not" -> "(!${this.funArgs[0].toR(model)})"
            else -> throw IllegalStateException("Unsupported formula: $this")
        }
    }
}