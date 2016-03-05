package com.github.sybila.biodivine

import com.github.sybila.checker.*
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ctl.untilNormalForm
import com.github.sybila.ode.generator.*
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.concurrent.FutureTask
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

/**
 * This is the main function which should execute a shared memory verification task.
 */
fun main(args: Array<String>) {
    val consoleLogLevel = args[0]
    val name = args[1]
    val taskRoot = File(args[2])
    taskRoot.mkdirs()
    val config = Yaml().load(args[3]) as Map<*, *>

    //Configure default logger to match main process
    val logger = Logger.getLogger("com.github.sybila")
    logger.useParentHandlers = false    //disable default top level logger
    logger.addHandler(ConsoleHandler().apply {
        this.level = consoleLogLevel.toLogLevel()
        this.formatter = CleanFormatter()
    })
    logger.addHandler(FileHandler("${taskRoot.absolutePath}/$name-log.log").apply {
        this.formatter = SimpleFormatter()  //NO XML!
    })

    val modelConfig = config["model"] as Map<*, *>? ?: error("No model specified!")

    if (modelConfig["type"] != "ODE") {
        error("Unsupported model type: ${modelConfig["type"]}")
    }

    val modelFile = File(modelConfig["file"] as String? ?: error("Missing model file"))


    val m = Parser().parse(modelFile)

    logger.info("Model parsing finished. Running approximation...")
    val start = System.currentTimeMillis()
    val model = m.computeApproximation(
            modelConfig["fastApproximation"] as Boolean? ?: false,
            modelConfig["cutToRange"] as Boolean? ?: false
    )
    logger.info("Model approximation finished. Elapsed time: ${System.currentTimeMillis() - start}ms")

    val parserConfig = config["propertyParser"] as Map<*, *>?

    val ctlParser = CTLParser(parserConfig?.toParserConfiguration() ?: CTLParser.Configuration(
            untilNormalForm, true, Logger.getLogger(CTLParser::class.java.canonicalName).apply { this.level = Level.INFO }
    ))

    val distConfig = config["distribution"] as Map<*, *>?
    val distribution = if (distConfig == null) DistributionSettings() else DistributionSettings(distConfig)

    if (distribution.communicator != "SharedMemoryCommunicator") {
        error("Unsupported communicator: ${distribution.communicator}")
    }
    if (distribution.jobQueue != "SingleThreadJobQueue") {
        error("Unsupported job queue: ${distribution.jobQueue}")
    }

    val nodeEncoder = NodeEncoder(model)

    val partitions = (0 until distribution.workers).map { id ->
        when (distribution.partitioning) {
            "uniform" -> UniformPartitionFunction<IDNode>()
            "slice" -> SlicePartitioning(id, distribution.workers, nodeEncoder)
            "block" -> ChequerPartitioning(id, distribution.workers, distConfig?.get("blockSize") as Int? ?: 100, nodeEncoder)
            "hash" -> HashPartitioning(id, distribution.workers, nodeEncoder)
            else -> error("Unknown partition function: ${distribution.partitioning}")
        }
    }
    val fragments = partitions.map {
        OdeFragment(model, it)
    }

    val checkerConfig = config["checker"] as Map<*,*>?

    val checkerLogLevel = checkerConfig?.get("logLevel") as String? ?: "info"

    val comm = createSharedMemoryCommunicators(distribution.workers)
    val tokens = comm.toTokenMessengers()
    val terminators = tokens.toFactories()
    val queues = createSingleThreadJobQueues<IDNode, RectangleColors>(distribution.workers, partitions, comm, terminators)

    val resultPrinting = (checkerConfig?.get("results") as List<*>? ?: listOf("size")).map { it.toString() }

    queues.zip(fragments).map {
        ModelChecker(it.second, it.first, Logger.getLogger(ModelChecker::class.java.canonicalName).apply {
            level = checkerLogLevel.toLogLevel()
        })
    }.mapIndexed { id, checker ->
        FutureTask {
            val localLogger = Logger.getLogger("com.github.sybila.$id")
            localLogger.useParentHandlers = false
            localLogger.addHandler(ConsoleHandler().apply {
                this.level = consoleLogLevel.toLogLevel()
                this.formatter = CleanFormatter("$id: ")
            })
            localLogger.addHandler(FileHandler("${taskRoot.absolutePath}/$name-$id-log.log").apply {
                this.formatter = SimpleFormatter()  //NO XML!
            })

            val properties = config["properties"] as List<*>

            var pI = 0
            for (p in properties) {
                pI += 1
                val property = p as Map<*,*>

                val pFileName = property["file"] as String?
                val verify = property["verify"] as String?
                val formula = property["formula"] as String?

                if (verify != null && formula != null) {
                    error("Invalid property. Can't specify inlined formula and verify at the same time: $verify, $formula")
                }

                if (verify == null && formula == null) {
                    error("Invalid property. Missing a formula or verify clause.")
                }

                val f = if (verify != null) {
                    val formulas = ctlParser.parse(File(pFileName))
                    formulas[verify]!!
                } else if (pFileName != null) {
                    val formulas = ctlParser.parse("""
                    #include "$pFileName"
                    myLongPropertyName = $formula
                """)
                    formulas["myLongPropertyName"]!!
                } else {
                    ctlParser.formula(formula!!)
                }

                localLogger.info("Start verification of $f")

                val checkStart = System.currentTimeMillis()
                val results = checker.verify(f)

                localLogger.info("Verification finished, elapsed time: ${System.currentTimeMillis() - checkStart}ms")

                for (printType in resultPrinting) {
                    when (printType) {
                        "size" -> localLogger.info("Results size: ${results.entries.count()}")
                        "stats" -> {
                            localLogger.info("Model checker stats: ${checker.getStats()}")
                        }
                        "human" -> {
                            File(taskRoot, "query-$pI.$id.human.txt").bufferedWriter().use {
                                for (entry in results.entries) {
                                    it.write("${entry.key} - ${entry.value}\n")
                                }
                            }
                        }
                        "json" -> {
                            error("Json isn't supported just yet!")
                            /* This does not work...
                            File(taskRoot, "query-$pI.$id.json").bufferedWriter().use {
                                val resultList = results.entries.toList()
                                it.write(Gson().toJson(resultList))
                            }*/
                        }
                        else -> error("Unsupported print type: $printType")
                    }
                }

                checker.resetStats()
            }
        }
        //rethink join strategy so that it actually quits
    }.map { val t = guardedThread { it.run() }; Pair(it, t) }.map { it.first.get(); it.second.join() }

    tokens.map { it.close() }
    comm.map { it.close() }

}

fun Map<*,*>.toParserConfiguration(): CTLParser.Configuration {
    val normalForm = this["normalForm"] as String? ?: "until"
    val logLevel = this["logLevel"] as String? ?: "info"
    return CTLParser.Configuration(
            when (normalForm) {
                "until" -> untilNormalForm
                "none" -> null
                else -> error("Unknown normal form: $normalForm")
            }, this["optimize"] as Boolean? ?: true,
            Logger.getLogger(CTLParser::class.java.canonicalName).apply { level = logLevel.toLogLevel() }
    )
}