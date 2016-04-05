package com.github.sybila.biodivine.ctl

import com.github.daemontus.jafra.Terminator
import com.github.sybila.biodivine.*
import com.github.sybila.checker.*
import com.github.sybila.ctl.CTLParser
import com.github.sybila.ode.generator.MPJComm
import com.github.sybila.ode.generator.NodeEncoder
import com.github.sybila.ode.generator.partitioning.BlockPartitioning
import com.github.sybila.ode.generator.partitioning.HashPartitioning
import com.github.sybila.ode.generator.partitioning.SlicePartitioning
import com.github.sybila.ode.generator.rect.RectangleMPJCommunicator
import com.github.sybila.ode.generator.rect.RectangleOdeFragment
import com.github.sybila.ode.generator.smt.SMTMPJCommunicator
import com.github.sybila.ode.generator.smt.SMTOdeFragment
import mpi.MPI
import java.io.File
import java.util.*
import java.util.logging.FileHandler
import java.util.logging.Logger
import java.util.logging.SimpleFormatter
import kotlin.system.exitProcess

/**
 * This is the main function which should execute a shared memory verification task.
 */
fun main(args: Array<String>) {
    Thread.setDefaultUncaughtExceptionHandler({ thread, throwable ->
        //TODO: This is not working. Why?
        throwable.printStackTrace()
        println("Uncaught exception in $thread, exiting...")
        exitProcess(-1)
    })
    //println("Process started with args: ${Arrays.toString(args)}")
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
                is BlockPartitionConfig -> BlockPartitioning(id, workerCount, config.partitioning.blockSize, nodeEncoder)
                else -> throw IllegalArgumentException("Unsupported partitioning: ${config.partitioning}")
            }

            val commConfig = config.communicator
            val commLogLevel = when {
                commConfig is MPJClusterCommunicatorConfig -> commConfig.logLevel
                commConfig is MPJLocalCommunicatorConfig -> commConfig.logLevel
                else -> throw IllegalStateException("Unsupported comm: $commConfig")
            }

            fun <T: Colors<T>> runModelChecking(comm: Communicator, terminator: Terminator.Factory, fragment: KripkeFragment<IDNode, T>) {
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
                            id, taskRoot, "query-$i", result,
                            checker, nodeEncoder, model, properties[i].results, logger,
                            if (fragment is SMTOdeFragment) fragment.order else null)
                    clearStats(checker, fragment is SMTOdeFragment)
                }
            }

            when (config.colors) {
                is RectangularColorsConfig -> {
                    val fragment = RectangleOdeFragment(model, partition)
                    val token = CommunicatorTokenMessenger(id, workerCount)
                    val comm = RectangleMPJCommunicator(id, workerCount, model.parameters.size, MPJComm(MPI.COMM_WORLD), Logger.getLogger("$rootPackage.$id.comm").apply {
                        level = commLogLevel
                    }, { m -> token.invoke(m) })
                    token.comm = comm
                    val terminator = Terminator.Factory(token)
                    runModelChecking(comm, terminator, fragment)
                    token.close()
                    logger.info("Tokens closed")
                    comm.close()
                    logger.info("Comm closed")
                }
                is SMTColorsConfig -> {
                    val fragment = SMTOdeFragment(model, partition)
                    val token = CommunicatorTokenMessenger(id, workerCount)
                    val comm = SMTMPJCommunicator(
                            id, workerCount, fragment.order, MPJComm(MPI.COMM_WORLD), Logger.getLogger("$rootPackage.$id.comm").apply {
                        level = commLogLevel
                    }, { m -> token.invoke(m) })
                    token.comm = comm
                    val terminator = Terminator.Factory(token)
                    runModelChecking(comm, terminator, fragment)
                    token.close()
                    logger.info("Tokens closed")
                    comm.close()
                    logger.info("Comm closed")
                }
                else -> throw IllegalArgumentException("Unsupported colors ${config.colors}")
            }
        }
    }

    MPI.Finalize()
}
