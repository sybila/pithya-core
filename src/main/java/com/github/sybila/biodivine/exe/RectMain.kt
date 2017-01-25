package com.github.sybila.biodivine.exe

import com.github.sybila.checker.Checker
import com.github.sybila.checker.CheckerStats
import com.github.sybila.checker.StateMap
import com.github.sybila.checker.channel.connectWithSharedMemory
import com.github.sybila.checker.partition.asUniformPartitions
import com.github.sybila.checker.solver.SolverStats
import com.github.sybila.huctl.Formula
import com.github.sybila.ode.generator.NodeEncoder
import com.github.sybila.ode.generator.rect.Rectangle
import com.github.sybila.ode.generator.rect.RectangleOdeModel
import com.github.sybila.ode.model.OdeModel
import com.google.gson.Gson
import java.io.PrintStream
import java.util.*

fun rectangleMain(config: MainConfig, model: OdeModel, properties: Map<String, Formula>) {

    val logStream: PrintStream? = config.logOutput.readStream()
    val resultStream: PrintStream? = config.resultOutput.readStream()

    if (config.logLevel >= LogLevel.INFO) {
        SolverStats.reset(logStream)
    }

    if (config.logLevel >= LogLevel.VERBOSE) {
        CheckerStats.reset(logStream)
    }

    val models = (0 until config.parallelism).map {
        RectangleOdeModel(model, createSelfLoops = !config.disableSelfLoops)
    }.asUniformPartitions()
    Checker(models.connectWithSharedMemory()).use { checker ->

        val r = checker.verify(properties)

        logStream?.println("Verification finished. Processing results...")

        if (config.resultType == ResultType.JSON) {
            resultStream?.println(printJsonRectResults(model, r))
        } else {
            val encoder = NodeEncoder(model)
            for ((property, valid) in r) {
                resultStream?.println(property)
                valid.zip(models).forEach {
                    val (result, solver) = it
                    solver.run {
                        for ((state, value) in result.entries()) {
                            resultStream?.println("${state.prettyPrint(model, encoder)} -> Params(${value.prettyPrint()})")
                        }
                    }
                }
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

private fun printJsonRectResults(model: OdeModel, result: Map<String, List<StateMap<Set<Rectangle>>>>): String {
    val stateIndexMapping = HashMap<Int, Int>()
    val states = ArrayList<Int>()
    val paramsIndexMapping = HashMap<Set<Rectangle>, Int>()
    val params = ArrayList<Set<Rectangle>>()
    val map = ArrayList<Result>()
    for ((f, r) in result) {
        val rMap = ArrayList<List<Int>>()
        for (partitionResult in r) {
            for ((s, p) in partitionResult.entries()) {
                val stateIndex = stateIndexMapping.computeIfAbsent(s) {
                    states.add(s)
                    states.size - 1
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
            type = "rectangular",
            results = map,
            parameterValues = params.map {
                it.map { it.asIntervals() }
            },
            parameterBounds = model.parameters.map { listOf(it.range.first, it.range.second) }
    )

    return Gson().toJson(r)
}
