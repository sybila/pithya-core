package com.github.sybila.biodivine.exe

import com.github.sybila.checker.solver.SolverStats
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.ode.generator.rect.rectangleOf
import kotlin.concurrent.thread


fun main(args: Array<String>) {
    SolverStats.reset(System.out)
    (1..(args[0].toInt())).map {
        thread {
            val limit = 20000
            var counter = 0L
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() < start + limit) {
                repeat(100) {
                    val k = LongArray(4) { counter.also { counter += 1 } }
                    counter += k.sum()
                }
            }
            /*RectangleSolver(rectangleOf(0.0, 10.0, 0.0, 10.0)).run {
                repeat(10000000) {
                    val a = rectangleOf(0.0, 5.0, 0.0, 5.0).asParams()
                    val b = rectangleOf(3.0, 6.0, 2.0, 7.0).asParams()
                    (tt and (a or b).not()).isSat()
                }
            }*/
            println("Done in ${System.currentTimeMillis() - start} and counter ${counter}")
        }
    }
}