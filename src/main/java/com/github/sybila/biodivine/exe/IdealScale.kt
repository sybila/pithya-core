package com.github.sybila.biodivine.exe

import com.github.sybila.checker.solver.SolverStats
import com.github.sybila.ode.generator.rect.RectangleSolver
import com.github.sybila.ode.generator.rect.rectangleOf
import kotlin.concurrent.thread


fun main(args: Array<String>) {
    SolverStats.reset(System.out)
    (1..(args[0].toInt())).map {
        thread {
            RectangleSolver(rectangleOf(0.0, 10.0, 0.0, 10.0)).run {
                while (true) {
                    val a = rectangleOf(0.0, 5.0, 0.0, 5.0).asParams()
                    val b = rectangleOf(3.0, 6.0, 2.0, 7.0).asParams()
                    (tt and (a or b).not()).isSat()
                }
            }
        }
    }
}