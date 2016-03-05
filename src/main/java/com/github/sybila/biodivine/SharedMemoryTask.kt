package com.github.sybila.biodivine

import com.github.sybila.checker.*
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ctl.untilNormalForm
import com.github.sybila.ode.generator.*
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import java.io.File
import java.util.concurrent.FutureTask
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

class ODEModelConfig(
    val file: File,
    val fastApproximation: Boolean,
    val cutToRange: Boolean
) {
    constructor(config: YamlMap) : this(
            config.getFile("file"),
            config.getBoolean("fastApproximation", false),
            config.getBoolean("cutToRange", false)
    )
}

/**
 * This is the main function which should execute a shared memory verification task.
 */
fun main(args: Array<String>) {
    val consoleLogLevel = args[0].toLogLevel()
    val name = args[1]
    val taskRoot = File(args[2])
    taskRoot.mkdirs()
    val config = args[3].toYamlMap()

    //Configure default logger to match main process
    val logger = Logger.getLogger("com.github.sybila")
    logger.useParentHandlers = false
    logger.addHandler(ConsoleHandler().apply {
        this.level = consoleLogLevel
        this.formatter = CleanFormatter()
    })
    logger.addHandler(FileHandler("${taskRoot.absolutePath}/$name-log.log").apply {
        this.formatter = SimpleFormatter()  //NO XML!
    })


    val taskConfig = TaskConfig(config)

    val ctlParser = CTLParser(config.getMap("ctlParser").toParserConfiguration())

    val modelType = config.getMap("model").getString("type", "none")

    val resultPrinting = config.getMap("checker").getStringList("results")
    val checkerLogLevel = config.getMap("checker").getLogLevel("logLevel", Level.INFO)

    when (modelType) {
        "ODE" -> {
            val modelConfig = ODEModelConfig(config.getMap("model"))

            val start = System.currentTimeMillis()
            val model = Parser().parse(modelConfig.file).apply {
                logger.info("Model parsing finished. Running approximation...")
            }.computeApproximation(
                    fast = modelConfig.fastApproximation,
                    cutToRange = modelConfig.cutToRange
            )
            logger.info("Model approximation finished. Elapsed time: ${System.currentTimeMillis() - start}ms")

            val nodeEncoder = NodeEncoder(model)

            val partitions = (0 until taskConfig.workers).map { id ->
                when (taskConfig.partitioning) {
                    "uniform" -> UniformPartitionFunction<IDNode>()
                    "slice" -> SlicePartitioning(id, taskConfig.workers, nodeEncoder)
                    "block" -> ChequerPartitioning(id, taskConfig.workers, config.getInt("blockSize", 100), nodeEncoder)
                    "hash" -> HashPartitioning(id, taskConfig.workers, nodeEncoder)
                    else -> error("Unknown partition function: ${taskConfig.partitioning}")
                }
            }

            val comm = when(taskConfig.communicator) {
                "SharedMemoryCommunicator" -> createSharedMemoryCommunicators(taskConfig.workers)
                else -> error("Unknown communicator type: ${taskConfig.communicator}")
            }
            val tokens = comm.toTokenMessengers()
            val terminators = tokens.toFactories()

            val queues = when(taskConfig.jobQueue) {
                "SingleThreadJobQueue" -> createSingleThreadJobQueues<IDNode, RectangleColors>(
                        taskConfig.workers, partitions, comm, terminators
                )
                else -> error("Unknown queue type: ${taskConfig.jobQueue}")
            }

            val fragments = partitions.map { OdeFragment(model, it) }

            queues.zip(fragments).mapIndexed { id, pair ->
                FutureTask {
                    val localLogger = Logger.getLogger("com.github.sybila.$id")
                    localLogger.useParentHandlers = false
                    localLogger.addHandler(ConsoleHandler().apply {
                        this.level = consoleLogLevel
                        this.formatter = CleanFormatter("$id: ")
                    })
                    localLogger.addHandler(FileHandler("${taskRoot.absolutePath}/$name.$id.log").apply {
                        this.formatter = SimpleFormatter()  //NO XML!
                    })

                    val checker = ModelChecker(pair.second, pair.first, Logger.getLogger("com.github.sybila.$id.checker").apply {
                        level = checkerLogLevel
                    })

                    val properties = config.getMapList("properties")
                    for (i in properties.indices) {
                        val result = verify(properties[i], ctlParser, checker, localLogger)
                        processResults(id, taskRoot, "query-$i", result, checker.getStats(), resultPrinting, localLogger)
                        checker.resetStats()
                    }
                }
            }.map {
                val t = guardedThread { it.run() }
                Pair(t, it)
            }.map {
                it.first.join()
                it.second.get()
            }

            tokens.map { it.close() }
            comm.map { it.close() }
        }
        else -> error("Unknown model type: $modelType")
    }
}

private fun <N: Node, C: Colors<C>> processResults(
        id: Int,
        taskRoot: File,
        queryName: String,
        results: Nodes<N, C>,
        stats: Map<String, Any>,
        printConfig: List<String>,
        logger: Logger
) {
    for (printType in printConfig) {
        when (printType) {
            "size" -> logger.info("Results size: ${results.entries.count()}")
            "stats" -> logger.info("Statistics: $stats")
            "human" -> {
                File(taskRoot, "$queryName.human.$id.txt").bufferedWriter().use {
                    for (entry in results.entries) {
                        it.write("${entry.key} - ${entry.value}\n")
                    }
                }
            }
            else -> error("Unknown print type: $printType")
        }
    }
}

private fun <N: Node, C: Colors<C>> verify(property: YamlMap, parser: CTLParser, checker: ModelChecker<N, C>, logger: Logger): Nodes<N, C> {

    val pFileName = property.getString("file")
    val verify = property.getString("verify")
    val formula = property.getString("formula")

    if (verify != null && formula != null) {
        error("Invalid property. Can't specify inlined formula and verify at the same time: $verify, $formula")
    }

    if (verify == null && formula == null) {
        error("Invalid property. Missing a formula or verify clause.")
    }

    val f = if (verify != null) {
        val formulas = parser.parse(File(pFileName))
        formulas[verify]!!
    } else if (pFileName != null) {
        val formulas = parser.parse("""
                    #include "$pFileName"
                    myLongPropertyNameThatNoOneWillUse = $formula
                """)
        formulas["myLongPropertyNameThatNoOneWillUse"]!!
    } else {
        parser.formula(formula!!)
    }

    logger.info("Start verification of $f")

    val checkStart = System.currentTimeMillis()
    val results = checker.verify(f)

    logger.info("Verification finished, elapsed time: ${System.currentTimeMillis() - checkStart}ms")

    return results

}

fun YamlMap.toParserConfiguration(): CTLParser.Configuration {
    val normalForm = this.getString("normalForm", "until")
    val logLevel = this.getLogLevel("logLevel", Level.INFO)
    return CTLParser.Configuration(
            when (normalForm) {
                "until" -> untilNormalForm
                "none" -> null
                else -> error("Unknown normal form: $normalForm")
            }, this.getBoolean("optimize", true),
            Logger.getLogger(CTLParser::class.java.canonicalName).apply { level = logLevel }
    )
}