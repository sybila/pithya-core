package com.github.sybila.biodivine.exe

import com.github.sybila.checker.Checker
import com.github.sybila.checker.CheckerStats
import com.github.sybila.checker.channel.connectWithSharedMemory
import com.github.sybila.checker.partition.asUniformPartitions
import com.github.sybila.checker.solver.SolverStats
import com.github.sybila.huctl.Formula
import com.github.sybila.ode.generator.bool.BoolOdeModel
import com.github.sybila.ode.model.OdeModel
import java.io.PrintStream
import kotlin.concurrent.thread


fun boolMain(config: MainConfig, model: OdeModel, properties: Map<String, Formula>, logStream: PrintStream?) {

    val resultStream: PrintStream? = config.resultOutput.readStream()

    if (config.logLevel >= LogLevel.INFO) {
        SolverStats.reset(logStream)
    }

    if (config.logLevel >= LogLevel.VERBOSE) {
        CheckerStats.reset(logStream)
    }

    val models = (0 until config.parallelism).map {
        BoolOdeModel(model, createSelfLoops = !config.disableSelfLoops)
    }.asUniformPartitions()

    /*val start = System.currentTimeMillis()
    models.map { thread {
        it.run {
            for (s in 0 until stateCount) {
                if (s in this) {
                    s.predecessors(true)
                }
            }
        }
    } }.forEach(Thread::join)
    println("State space generation: ${System.currentTimeMillis() - start}")*/

    Checker(models.connectWithSharedMemory()).use { checker ->

        val r = checker.verify(properties)

        logStream?.println("Verification finished. Processing results...")

        for ((property, valid) in r) {
            val resultSize = valid.fold(0) { acc, map ->
                acc + map.states().asSequence().count()
            }
            println("Property $property size: $resultSize")
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