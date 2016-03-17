package com.github.sybila.biodivine.ctl

import com.github.sybila.biodivine.*
import com.github.sybila.checker.*
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ode.generator.*
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
    val logger = getGlobalLogger(consoleLogLevel, taskRoot, name)

    val config = CTLParameterEstimationConfig(yamlConfig)

    val ctlParser = CTLParser(config.parser)

    when (config.model) {
        is ODEModelConfig -> {
            val model = loadModel(config.model, taskRoot, logger)
            val nodeEncoder = NodeEncoder(model)

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
            val fragments = partitions.map { p -> RectangleOdeFragment(model, p) }

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
                        localLogger.addHandler(FileHandler("${taskRoot.absolutePath}/worker.$id.log").apply {
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


