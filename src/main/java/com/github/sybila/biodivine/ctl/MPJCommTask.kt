package com.github.sybila.biodivine.ctl

import com.github.daemontus.jafra.Terminator
import com.github.sybila.biodivine.*
import com.github.sybila.checker.*
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ode.generator.*
import mpi.MPI
import java.io.File
import java.util.*
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

/**
 * This is the main function which should execute a shared memory verification task.
 */
fun main(args: Array<String>) {
    println("Process started with args: ${Arrays.toString(args)}")
    MPI.Init(args)
    val consoleLogLevel = args[args.size - 4].toLogLevel()
    val name = args[args.size - 3]
    val taskRoot = File(args[args.size - 2])
    taskRoot.mkdirs()
    val yamlConfig = args[args.size - 1].toYamlMap()

    val workerCount = MPI.COMM_WORLD.Size()
    val id = MPI.COMM_WORLD.Rank()

    val logger = Logger.getLogger("$rootPackage.$id")
    logger.useParentHandlers = false
    logger.addHandler(ConsoleHandler().apply {
        this.level = consoleLogLevel
        this.formatter = CleanFormatter("$id: ")
    })
    logger.addHandler(FileHandler("${taskRoot.absolutePath}/worker.$id.log").apply {
        this.formatter = SimpleFormatter()  //NO XML!
    })

    val config = CTLParameterEstimationConfig(yamlConfig)

    val ctlParser = CTLParser(config.parser)

    when (config.model) {
        is ODEModelConfig -> {
            val model = loadModel(config.model, taskRoot, logger)
            val nodeEncoder = NodeEncoder(model)

            val partition = when(config.partitioning) {
                is UniformPartitionConfig -> {
                    logger.warning("WARNING: Using a uniform partitioning in MPI configuration!")
                    UniformPartitionFunction<IDNode>()
                }
                is SlicePartitionConfig -> SlicePartitioning(id, workerCount, nodeEncoder)
                is HashPartitionConfig -> HashPartitioning(id, workerCount, nodeEncoder)
                is BlockPartitionConfig -> ChequerPartitioning(id, workerCount, config.partitioning.blockSize, nodeEncoder)
                else -> throw IllegalArgumentException("Unsupported partitioning: ${config.partitioning}")
            }

            val commConfig = config.communicator as MPJLocalCommunicatorConfig

            val comm = MPJCommunicator(id, workerCount, model.parameters.size, MPJComm(MPI.COMM_WORLD), Logger.getLogger("$rootPackage.$id.comm").apply {
                level = commConfig.logLevel
            })
            val token = CommunicatorTokenMessenger(comm)
            val terminator = Terminator.Factory(token)
            val fragment = RectangleOdeFragment(model, partition)

            fun <T: Colors<T>> runModelChecking(queue: JobQueue.Factory<IDNode, T>, fragment: KripkeFragment<IDNode, T>) {
                val checker = ModelChecker(fragment, queue, Logger.getLogger("$rootPackage.checker").apply {
                    level = config.checker.logLevel
                })

                val properties = yamlConfig.loadPropertyList()
                for (i in properties.indices) {
                    val result = verify(properties[i], ctlParser, checker, logger)
                    processResults(id, taskRoot, "query-$i", result, checker.getStats(), properties[i].results, logger)
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
            logger.info("Tokens closed")
            comm.close()
            logger.info("Comm closed")
        }
    }

    MPI.Finalize()
}
