package com.github.sybila.biodivine.ctl

/**
 * This is the main function which should execute a shared memory verification task.
 */
fun main(args: Array<String>) {
/*
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
                    is BlockPartitionConfig -> BlockPartitioning(id, commConfig.workers, config.partitioning.blockSize, nodeEncoder)
                    else -> throw IllegalArgumentException("Unsupported partitioning: ${config.partitioning}")
                }
            }

            val comms = createSharedMemoryCommunicators(commConfig.workers)
            val tokens = comms.map { CommunicatorTokenMessenger(it.id, it.size) }
            tokens.zip(comms).forEach {
                it.first.comm = it.second
                it.second.addListener(Token::class.java) { m -> it.first.invoke(m) }
            }
            val terminators = tokens.toFactories()

            fun <T: Colors<T>> runModelChecking(fragments: List<KripkeFragment<IDNode, T>>) {
                val queues = when (config.jobQueue) {
                    is BlockingJobQueueConfig -> createSingleThreadJobQueues<IDNode, T>(
                            commConfig.workers, partitions, comms, terminators, logger
                    )
                    is MergeQueueConfig -> createMergeQueues<IDNode, T>(
                            commConfig.workers, partitions, comms, terminators, logger
                    )
                    else -> throw IllegalArgumentException("Unsupported job queue ${config.jobQueue}")
                }
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
                            val fragment = pair.second
                            processResults(
                                    id, taskRoot, "query-$i", result,
                                    checker, nodeEncoder, model, properties[i].results, localLogger)
                            clearStats(checker, fragment is SMTOdeFragment)
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
                    runModelChecking(partitions.map { p -> RectangleOdeFragment(model, p, config.model.selfLoops) })
                }
                is SMTColorsConfig -> {
                    runModelChecking(partitions.map { p -> SMTOdeFragment(
                            model, p,
                            config.model.selfLoops
                    ) })
                }
                else -> throw IllegalArgumentException("Unsupported colors ${config.colors}")
            }

            tokens.map { it.close() }
            comms.map { it.close() }
        }
    }
*/
}


