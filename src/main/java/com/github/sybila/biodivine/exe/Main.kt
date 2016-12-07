package com.github.sybila.biodivine.exe

import com.github.daemontus.jafra.Terminator
import com.github.daemontus.jafra.Token
import com.github.sybila.biodivine.ctl.EmptyCommunicator
import com.github.sybila.biodivine.ctl.createMergeQueues
import com.github.sybila.checker.*
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ode.generator.NodeEncoder
import com.github.sybila.ode.generator.rect.RectangleColors
import com.github.sybila.ode.generator.rect.RectangleOdeFragment
import com.github.sybila.ode.generator.smt.SMTColors
import com.github.sybila.ode.generator.smt.SMTOdeFragment
import com.github.sybila.ode.model.Evaluable
import com.github.sybila.ode.model.Model
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.RampApproximation
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.InstanceCreator
import com.microsoft.z3.Expr
import java.io.File
import java.lang.management.ManagementFactory
import java.util.*
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

fun main(args: Array<String>) {
    try {
        if (args.isEmpty()) throw IllegalArgumentException("Missing argument: .json config file")
        val pid = ManagementFactory.getRuntimeMXBean().name.takeWhile { it != '@' }
        System.err.println("PID: $pid")
        System.err.flush()
        val file = File(args[0]).readText()
        val builder = GsonBuilder()

        builder.registerTypeAdapter(Evaluable::class.java, InstanceCreator<Evaluable> {
            RampApproximation(0, doubleArrayOf(), doubleArrayOf())  //no other evaluables are supported
        })
        val parser = CTLParser()
        val modelParser = Parser()
        val gson = builder.create()
        //val typeToken = object : TypeToken<Pair<Model, List<InputPair>>>() {}.type
        val data: MainPair = gson.fromJson(file, MainPair::class.java)


        val model = modelParser.parse(data.first)
        val formulas = data.second.map {
            it.first to parser.formula(it.second)
        }
        val isRectangular = model.variables.all {
            it.equation.map { it.paramIndex }.filter { it >= 0 }.toSet().size <= 1
        }

        val logger = Logger.getLogger("main").apply {
            this.level = Level.INFO
            this.useParentHandlers = false
            this.addHandler(object : Handler() {
                override fun publish(record: LogRecord) {
                    System.err.println(record.message)
                }

                override fun flush() {
                    System.err.flush()
                }

                override fun close() {}

            })
        }

        val partition = UniformPartitionFunction<IDNode>()

        val comm = EmptyCommunicator()
        val tokens = CommunicatorTokenMessenger(comm.id, comm.size)
        tokens.comm = comm
        comm.addListener(Token::class.java) { m -> tokens.invoke(m) }
        val terminator = Terminator.Factory(tokens)

        if (isRectangular) {
            val f = RectangleOdeFragment(model, partition, true)
            val q = createMergeQueues<IDNode, RectangleColors>(1, listOf(partition),
                listOf(comm), listOf(terminator), logger
            ).first()
            val checker = ModelChecker(f, q, logger)

            val results = formulas.map {
                it.first to checker.verify(it.second)
            }

            printRectResults(results, model)
        } else {
            val f = SMTOdeFragment(model, partition, true)
            val q = createMergeQueues<IDNode, SMTColors>(1, listOf(partition),
                    listOf(comm), listOf(terminator), logger
            ).first()
            val checker = ModelChecker(f, q, logger)

            val results = formulas.map {
                it.first to checker.verify(it.second)
            }

            printSMTResults(results, model)
        }

        System.err.println("!!DONE!!")

    } catch (e: Exception) {
        e.printStackTrace()
        System.err.println("${e.message} (${e.javaClass.name})")
    }
}

private fun printRectResults(result: List<Pair<String, Nodes<IDNode, RectangleColors>>>, model: Model) {
    val states = ArrayList<IDNode>()
    val params = ArrayList<RectangleColors>()
    val map = ArrayList<Result>()
    for ((f, r) in result) {
        val rMap = ArrayList<List<Int>>()
        for ((s, p) in r.entries) {
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
                it.asRectangleList().map { it.asIntervals() }
            }
    )
    val gson = Gson()
    println(gson.toJson(r))
}

private fun printSMTResults(result: List<Pair<String, Nodes<IDNode, SMTColors>>>, model: Model) {
    val states = ArrayList<IDNode>()
    val params = ArrayList<SMTColors>()
    val map = ArrayList<Result>()
    for ((f, r) in result) {
        val rMap = ArrayList<List<Int>>()
        for ((s, pp) in r.entries) {
            val ii = states.indexOf(s)
            val i = if (ii < 0) {
                states.add(s)
                states.size - 1
            } else ii
            val p = pp.normalize().purify()
            val jj = params.indexOf(p)
            val j = if (jj < 0) {
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
                val f = it.prettyFormula()
                SMTResult(
                        smtlib2Formula = f.toString(),
                        Rexpression = f.toR()
                )
            }
    )
    val gson = Gson()
    println(gson.toJson(r))
}

private fun IDNode.expand(model: Model, encoder:NodeEncoder): State {
    return State(id = this.id.toLong(), bounds = model.variables.mapIndexed { i, variable ->
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
        val parameterValues: List<Any>,
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
        this.isAnd -> "(" + this.args.map(Expr::toR).joinToString(separator = " && ") + ")"
        this.isOr -> "(" + this.args.map(Expr::toR).joinToString(separator = " || ") + ")"
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