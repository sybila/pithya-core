package com.github.sybila.biodivine.ctl

import com.github.sybila.biodivine.*
import com.github.sybila.checker.*
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ode.generator.*
import com.github.sybila.ode.model.Parser
import com.github.sybila.ode.model.computeApproximation
import java.io.File
import java.util.concurrent.FutureTask
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

/**
 * This is the main function which should execute a shared memory verification task.
 */
fun main(args: Array<String>) {
    val consoleLogLevel = args[0].toLogLevel()
    val name = args[1]
    val taskRoot = File(args[2])
    taskRoot.mkdirs()
    val yamlConfig = args[3].toYamlMap()

    //Configure default logger to match main process
    val logger = Logger.getLogger(rootPackage)
    logger.useParentHandlers = false
    logger.addHandler(ConsoleHandler().apply {
        this.level = consoleLogLevel
        this.formatter = CleanFormatter()
    })
    logger.addHandler(FileHandler("${taskRoot.absolutePath}/$name-log.log").apply {
        this.formatter = SimpleFormatter()  //NO XML!
    })

    val config = CTLParameterEstimationConfig(yamlConfig)

    val ctlParser = CTLParser(config.parser)

    when (config.model) {
        is ODEModelConfig -> {
            //copy model file into task folder
            config.model.file.copyTo(File(taskRoot, config.model.file.name), overwrite = true)

            val start = System.currentTimeMillis()
            val model = Parser().parse(config.model.file).apply {
                logger.info("Model parsing finished. Running approximation...")
            }.computeApproximation(
                    fast = config.model.fastApproximation,
                    cutToRange = config.model.cutToRange
            )
            val nodeEncoder = NodeEncoder(model)
            logger.info("Model approximation finished. Elapsed time: ${System.currentTimeMillis() - start}ms")

            //this has to be valid if starter is working fine
            val commConfig = config.communicator as SharedCommunicatorConfig

            if (commConfig.workers == 1) {
                logger.warning("Running shared memory verification on one worker. Are you sure you don't want no communicator?")
            }

            val partitions = (0 until commConfig.workers).map { id ->
                when(config.partitioning) {
                    is UniformPartitionConfig -> UniformPartitionFunction<IDNode>()
                    is SlicePartitionConfig -> SlicePartitioning(id, commConfig.workers, nodeEncoder)
                    is HashPartitionConfig -> HashPartitioning(id, commConfig.workers, nodeEncoder)
                    is BlockPartitionConfig -> ChequerPartitioning(id, commConfig.workers, config.partitioning.blockSize, nodeEncoder)
                    else -> throw IllegalArgumentException("Unsupported partitioning: ${config.partitioning}")
                }
            }

            val comms = createSharedMemoryCommunicators(commConfig.workers)
            val tokens = comms.toTokenMessengers()
            val terminators = tokens.toFactories()
            val fragments = partitions.map { p -> OdeFragment(model, p) }

            fun <T: Colors<T>> runModelChecking(queues: List<JobQueue.Factory<IDNode, T>>, fragments: List<KripkeFragment<IDNode, T>>) {
                queues.zip(fragments).mapIndexed { id, pair ->
                    FutureTask {
                        //init local task handler
                        val localLogger = Logger.getLogger("$rootPackage.$id")
                        localLogger.useParentHandlers = false
                        localLogger.addHandler(ConsoleHandler().apply {
                            this.level = consoleLogLevel
                            this.formatter = CleanFormatter("$id: ")
                        })
                        localLogger.addHandler(FileHandler("${taskRoot.absolutePath}/$name.$id.log").apply {
                            this.formatter = SimpleFormatter()  //NO XML!
                        })

                        val checker = ModelChecker(pair.second, pair.first, Logger.getLogger("$rootPackage.$id.checker").apply {
                            level = config.checker.logLevel
                        })

                        val properties = yamlConfig.loadPropertyList()
                        for (i in properties.indices) {
                            val result = verify(properties[i], ctlParser, checker, localLogger)
                            processResults(id, taskRoot, "query-$i", result, checker.getStats(), properties[i].results, localLogger)
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
            }

            when (config.colors) {
                is RectangularColorsConfig -> {
                    val queues = when (config.jobQueue) {
                        is BlockingJobQueueConfig -> createSingleThreadJobQueues<IDNode, RectangleColors>(
                                commConfig.workers, partitions, comms, terminators
                        )
                        else -> throw IllegalArgumentException("Unsupported job queue ${config.jobQueue}")
                    }
                    runModelChecking(queues, fragments)
                }
                is SMTColorsConfig -> {
                    logger.severe("SMT colors are currently not supported")
                    listOf<JobQueue.Factory<IDNode, *>>()
                }
                else -> throw IllegalArgumentException("Unsupported colors ${config.colors}")
            }

            tokens.map { it.close() }
            comms.map { it.close() }
        }
    }

}

private fun <N: Node, C: Colors<C>> processResults(
        id: Int,
        taskRoot: File,
        queryName: String,
        results: Nodes<N, C>,
        stats: Map<String, Any>,
        printConfig: Set<String>,
        logger: Logger
) {
    for (printType in printConfig) {
        when (printType) {
            c.size -> logger.info("Results size: ${results.entries.count()}")
            c.stats -> logger.info("Statistics: $stats")
            c.human -> {
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

private fun <N: Node, C: Colors<C>> verify(property: PropertyConfig, parser: CTLParser, checker: ModelChecker<N, C>, logger: Logger): Nodes<N, C> {

    when {
        property.verify != null && property.formula != null ->
                error("Invalid property. Can't specify inlined formula and verify at the same time: $property")
        property.verify != null && property.file == null ->
                error("Can't resolve ${property.verify}, no CTL file provided")
        property.verify == null && property.formula == null ->
                error("Invalid property. Missing a formula or verify clause. $property")
    }

    val f = if (property.verify != null) {
        val formulas = parser.parse(property.file!!)
        formulas[property.verify] ?: error("Property ${property.verify} not found in file ${property.file}")
    } else if (property.file != null) {
        val formulas = parser.parse("""
                    #include "${property.file.absolutePath}"
                    myLongPropertyNameThatNoOneWillUse = ${property.formula}
                """)
        formulas["myLongPropertyNameThatNoOneWillUse"]!!
    } else {
        parser.formula(property.formula!!)
    }

    logger.info("Start verification of $f")

    val checkStart = System.currentTimeMillis()
    val results = checker.verify(f)

    logger.info("Verification finished, elapsed time: ${System.currentTimeMillis() - checkStart}ms")

    return results
}
