package com.github.sybila.biodivine

import com.github.sybila.biodivine.exe.checkMissingThresholds
import com.github.sybila.checker.Checker
import com.github.sybila.checker.SequentialChecker
import com.github.sybila.checker.channel.connectWithSharedMemory
import com.github.sybila.checker.partition.asUniformPartitions
import com.github.sybila.huctl.HUCTLParser
import com.github.sybila.ode.generator.rect.RectangleOdeModel
import com.github.sybila.ode.generator.smt.Z3OdeFragment
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import java.io.File
import java.lang.management.ManagementFactory


fun main(args: Array<String>) {
    val start = System.currentTimeMillis()
    try {
        val cores = 1
        val pid = ManagementFactory.getRuntimeMXBean().name.takeWhile { it != '@' }
        System.err.println("PID: $pid")
        System.err.flush()

        val model = Parser().parse(File("/Users/daemontus/heap/sybila/tcbb.bio")).computeApproximation()
        val formulas = HUCTLParser().parse(File("/Users/daemontus/heap/sybila/test_prop.ctl"))

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
                for ((f, g) in r) {
                    println("$f: ${g.zip(models).map {
                        val (result, model) = it
                        model.run {
                            result.entries().asSequence().filter { it.second.isSat() }.count()
                        }
                    }}")
                }
                println("Elapsed: ${System.currentTimeMillis() - start}")
            }
        } else {
            val fragment = Z3OdeFragment(model)
            SequentialChecker(fragment).use { checker ->
                fragment.run {
                    System.err.println("Verification started...")
                    val r = checker.verify(formulas)
                    System.err.println("Verification finished. Printing results...")
                    println("Elapsed: ${System.currentTimeMillis() - start}")
                }
            }
        }

        System.err.println("!!DONE!!")

    } catch (e: Exception) {
        e.printStackTrace()
        System.err.println("${e.message} (${e.javaClass.name})")
    }
}