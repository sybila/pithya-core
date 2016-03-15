package com.github.sybila.biodivine.ctl

import com.github.daemontus.jafra.Terminator
import com.github.sybila.biodivine.c
import com.github.sybila.biodivine.rootPackage
import com.github.sybila.biodivine.toLogLevel
import com.github.sybila.biodivine.toYamlMap
import com.github.sybila.checker.*
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ode.generator.*
import java.io.File
import java.util.logging.Logger

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

            if (config.partitioning !is UniformPartitionConfig) {
                logger.warning("Using non-trivial partitioning with empty communicator setting. This will have no effect.")
            }

            val partition = when(config.partitioning) {
                is UniformPartitionConfig -> UniformPartitionFunction<IDNode>()
                is SlicePartitionConfig -> SlicePartitioning(0, 1, nodeEncoder)
                is HashPartitionConfig -> HashPartitioning(0, 1, nodeEncoder)
                is BlockPartitionConfig -> ChequerPartitioning(0, 1, config.partitioning.blockSize, nodeEncoder)
                else -> throw IllegalArgumentException("Unsupported partitioning: ${config.partitioning}")
            }

            val comm = EmptyCommunicator()
            val token = CommunicatorTokenMessenger(comm)
            val terminator = Terminator.Factory(token)
            val fragment = OdeFragment(model, partition)

            fun <T: Colors<T>> runModelChecking(queue: JobQueue.Factory<IDNode, T>, fragment: KripkeFragment<IDNode, T>) {
                val checker = ModelChecker(fragment, queue, Logger.getLogger("$rootPackage.checker").apply {
                    level = config.checker.logLevel
                })

                val properties = yamlConfig.loadPropertyList()
                for (i in properties.indices) {
                    val result = verify(properties[i], ctlParser, checker, logger)
                    processResults(taskRoot, "query-$i", result, checker.getStats(), properties[i].results, logger)
                    checker.resetStats()
                }
            }

            when (config.colors) {
                is RectangularColorsConfig -> {
                    val queues = when (config.jobQueue) {
                        is BlockingJobQueueConfig -> createSingleThreadJobQueues<IDNode, RectangleColors>(
                                1, listOf(partition), listOf(comm), listOf(terminator)
                        )
                        else -> throw IllegalArgumentException("Unsupported job queue ${config.jobQueue}")
                    }
                    runModelChecking(queues.first(), fragment)
                }
                is SMTColorsConfig -> {
                    logger.severe("SMT colors are currently not supported")
                    listOf<JobQueue.Factory<IDNode, *>>()
                }
                else -> throw IllegalArgumentException("Unsupported colors ${config.colors}")
            }

            token.close()
            comm.close()
        }
    }

}

private fun <N: Node, C: Colors<C>> processResults(
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
                File(taskRoot, "$queryName.human.txt").bufferedWriter().use {
                    for (entry in results.entries) {
                        it.write("${entry.key} - ${entry.value}\n")
                    }
                }
            }
            else -> error("Unknown print type: $printType")
        }
    }
}