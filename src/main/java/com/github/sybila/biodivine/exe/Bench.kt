package com.github.sybila.biodivine.exe

import com.github.sybila.checker.Checker
import com.github.sybila.checker.channel.connectWithSharedMemory
import com.github.sybila.checker.partition.asUniformPartitions
import com.github.sybila.huctl.*
import com.github.sybila.ode.generator.AbstractOdeFragment
import com.github.sybila.ode.generator.bool.BoolOdeModel
import com.github.sybila.ode.generator.rect.RectangleOdeModel
import com.github.sybila.ode.model.OdeModel
import com.github.sybila.ode.model.Summand
import com.github.sybila.ode.model.toBio
import java.io.File
import java.util.*
import kotlin.system.measureTimeMillis

val prop_1D_mid = Formula.Bool.And(
        Formula.Atom.Float("v0".asVariable(), CompareOp.GE, 5.0.asConstant()),
        Formula.Atom.Float("v0".asVariable(), CompareOp.LE, 6.0.asConstant())
)

val prop_2D_mid = Formula.Bool.And(
        Formula.Bool.And(
                Formula.Atom.Float("v0".asVariable(), CompareOp.GE, 5.0.asConstant()),
                Formula.Atom.Float("v0".asVariable(), CompareOp.LE, 6.0.asConstant())
        ),
        Formula.Bool.And(
                Formula.Atom.Float("v1".asVariable(), CompareOp.GE, 5.0.asConstant()),
                Formula.Atom.Float("v1".asVariable(), CompareOp.LE, 6.0.asConstant())
        )
)

val prop_1D_stable_mid = AG(prop_1D_mid)
val prop_1D_reach_mid = EF(prop_1D_mid)
val prop_2D_stable_mid = AG(prop_2D_mid)
val prop_2D_reach_mid = EF(prop_2D_mid)
val prop_nontrivial_cycle = bind("x", EX(EF("x".asReference())))
val prop_stable_state = bind("x", AX(AF("x".asReference())))

val extraThresholds = listOf(5.0, 6.0)

val model_1D_0P = OdeModel(
        variables = listOf(modelVarNoParam(0))
)

val model_1D_1P = OdeModel(
        variables = listOf(modelVarOneParam(0, 0)),
        parameters = listOf(modelParam(0))
)

val model_2D_0P = OdeModel(
        variables = listOf(modelVarNoParam(0), modelVarNoParam(1))
)

val model_2D_1P = OdeModel(
        variables = listOf(modelVarOneParam(0, 0), modelVarNoParam(1)),
        parameters = listOf(modelParam(0))
)

val model_2D_2P = OdeModel(
        variables = listOf(modelVarOneParam(0, 0), modelVarOneParam(1, 1)),
        parameters = listOf(modelParam(0), modelParam(1))
)

private fun modelParam(index: Int) = OdeModel.Parameter(
        name = "p$index",
        range = 0.0 to 10.0
)

private fun modelVarNoParam(index: Int) = OdeModel.Variable(
        name = "v$index",
        range = 0.0 to 10.0,
        thresholds = listOf(0.0, 10.0),
        varPoints = null,
        equation = listOf(
                Summand(constant = -1.0, variableIndices = listOf(0)),
                Summand(constant = 5.5)
        )
)

private fun modelVarOneParam(index: Int, param: Int) = OdeModel.Variable(
        name = "v$index",
        range = 0.0 to 10.0,
        thresholds = listOf(0.0, 10.0),
        varPoints = null,
        equation = listOf(
                Summand(constant = -1.0, variableIndices = listOf(0)),
                Summand(paramIndex = param)
        )
)

private fun Pair<Double, Double>.splitInto(stateCount: Int): List<Double> {
    val min = this.first
    val max = this.second

    val step = (max - min) / stateCount

    val result = ArrayList<Double>(stateCount + 1)
    result.add(min)
    while (result.size < stateCount) {
        result.add(result.last() + step)
    }
    result.add(max)

    return result
}

fun main(args: Array<String>) {
    printModels(
            property = prop_2D_reach_mid,
            timeLimit = 5000,
            modelPrefix = "2D_1P_reach_mid/model_",
            modelPrototype = model_2D_1P,
            parallelism = 1,
            constructor = { RectangleOdeModel(it) }
    )
}

fun <P: Any, T: AbstractOdeFragment<P>> printModels(
        property: Formula,
        timeLimit: Long,
        modelPrefix: String,
        modelPrototype: OdeModel,
        parallelism: Int,
        constructor: (OdeModel) -> T
) {
    var varIndex = 0
    val stateCounts = modelPrototype.variables.map { 1 }.toMutableList()
    do {
        //increase state count
        stateCounts[varIndex] = (stateCounts[varIndex] * 1.1).toInt() + 1
        varIndex = (varIndex + 1) % stateCounts.size
        val model = modelPrototype.copy(
                variables = modelPrototype.variables.zip(stateCounts).map { (variable, count) ->
                    variable.copy(
                            thresholds = (variable.range.splitInto(count) + extraThresholds).toSet().toList().sorted()
                    )
                }
        )

        val stateCount = stateCounts.fold(1) { a, b -> a * b }

        File(modelPrefix+"$stateCount.bio").writeText(model.toBio())

        // repeat 5 times and take fastest time
        val measuredTime = (1..5).map {
            measureTimeMillis {
                val models = (0 until parallelism).map { constructor(model) }.asUniformPartitions()

                Checker(models.connectWithSharedMemory()).use { checker ->
                    checker.verify(property)
                }
            }.also { println("Measured $it") }
        }.min() ?: 0L

        println("$stateCount in $measuredTime")
    } while (measuredTime < timeLimit)
}