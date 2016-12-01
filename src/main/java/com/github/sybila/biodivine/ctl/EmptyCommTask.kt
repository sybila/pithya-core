package com.github.sybila.biodivine.ctl

import com.github.daemontus.jafra.Terminator
import com.github.daemontus.jafra.Token
import com.github.sybila.biodivine.*
import com.github.sybila.checker.*
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ode.generator.NodeEncoder
import com.github.sybila.ode.generator.partitioning.BlockPartitioning
import com.github.sybila.ode.generator.partitioning.HashPartitioning
import com.github.sybila.ode.generator.partitioning.SlicePartitioning
import com.github.sybila.ode.generator.rect.RectangleOdeFragment
import com.github.sybila.ode.generator.smt.SMTOdeFragment
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
                is BlockPartitionConfig -> BlockPartitioning(0, 1, config.partitioning.blockSize, nodeEncoder)
                else -> throw IllegalArgumentException("Unsupported partitioning: ${config.partitioning}")
            }

            val comm = EmptyCommunicator()
            val tokens = CommunicatorTokenMessenger(comm.id, comm.size)
            tokens.comm = comm
            comm.addListener(Token::class.java) { m -> tokens.invoke(m) }
            val terminator = Terminator.Factory(tokens)

            fun <T: Colors<T>> runModelChecking(fragment: KripkeFragment<IDNode, T>) {
                val queues = when (config.jobQueue) {
                    is BlockingJobQueueConfig -> createSingleThreadJobQueues<IDNode, T>(
                            1, listOf(partition), listOf(comm), listOf(terminator), logger
                    )
                    is BlockingJobQueueConfig -> createMergeQueues<IDNode, T>(
                            1, listOf(partition), listOf(comm), listOf(terminator), logger
                    )
                    else -> throw IllegalArgumentException("Unsupported job queue ${config.jobQueue}")
                }
                val checker = ModelChecker(fragment, queues.first(), Logger.getLogger("$rootPackage.checker").apply {
                    level = config.checker.logLevel
                })

                val properties = yamlConfig.loadPropertyList()
                for (i in properties.indices) {
                    val result = verify(properties[i], ctlParser, checker, logger)
                    processResults(
                            0, taskRoot, "query-$i", result,
                            checker, nodeEncoder, model, properties[i].results, logger)
                    clearStats(checker, fragment is SMTOdeFragment)
                }
            }

            when (config.colors) {
                is RectangularColorsConfig -> {
                    runModelChecking(RectangleOdeFragment(model, partition))
                }
                is SMTColorsConfig -> {
                    runModelChecking(SMTOdeFragment(model, partition))
                }
                else -> throw IllegalArgumentException("Unsupported colors ${config.colors}")
            }

            tokens.close()
            comm.close()
        }
    }

}

fun <N: Node, C: Colors<C>> createSingleThreadJobQueues(
        processCount: Int,
        partitioning: List<PartitionFunction<N>> = (1..processCount).map { UniformPartitionFunction<N>(it - 1) },
        communicators: List<Communicator>,
        terminators: List<Terminator.Factory>,
        logger: Logger
): List<JobQueue.Factory<N, C>> {
    return (0..(processCount-1)).map { i ->
        object : JobQueue.Factory<N, C> {
            override fun createNew(initial: List<Job<N, C>>, onTask: JobQueue<N, C>.(Job<N, C>) -> Unit): JobQueue<N, C> {
                return SingleThreadQueue(initial, communicators[i], terminators[i], partitioning[i], onTask, logger)
            }
        }
    }
}

fun <N: Node, C: Colors<C>> createMergeQueues(
        processCount: Int,
        partitioning: List<PartitionFunction<N>> = (1..processCount).map { UniformPartitionFunction<N>(it - 1) },
        communicators: List<Communicator>,
        terminators: List<Terminator.Factory>,
        logger: Logger
): List<JobQueue.Factory<N, C>> {
    return (0..(processCount-1)).map { i ->
        object : JobQueue.Factory<N, C> {
            override fun createNew(initial: List<Job<N, C>>, onTask: JobQueue<N, C>.(Job<N, C>) -> Unit): JobQueue<N, C> {
                return MergeQueue(initial, communicators[i], terminators[i], partitioning[i], onTask, logger)
            }
        }
    }
}