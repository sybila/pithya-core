package com.github.sybila.biodivine

import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

data class TaskConfig(
        val type: String,
        val workers: Int,
        val maxMemory: Int,
        val timeout: Int,
        val jobQueue: String,
        val communicator: String,
        val partitioning: String
) {
    constructor(config: YamlMap) : this(
            config.getString("type", "ctlParamEstimation"),
            config.getInt("workers", 1),
            config.getInt("maxMemory", 1024),
            config.getInt("timeout", -1),
            config.getString("jobQueue", "SingleThreadJobQueue"),
            config.getString("communicator", "SharedMemoryCommunicator"),
            config.getString("partitioning", "uniform")
    )
}

fun executeTask(config: YamlMap, name: String?, root: File, consoleLogLevel: Level): Boolean {
    val logger = Logger.getLogger(rootPackage)
    val taskRoot = if (name == null) root else File(root, name)
    val taskConfig = TaskConfig(config)
    return when (taskConfig.type) {

        "ctlParamEstimation" -> {
            when (taskConfig.communicator) {

                "SharedMemoryCommunicator" -> {
                    logger.info("Starting a shared memory verification process...")
                    val code = guardedProcess(arrayOf(
                        getJavaLocation(),
                        "-cp", System.getProperty("java.class.path"),
                        "-Xmx${taskConfig.maxMemory * taskConfig.workers}M",
                        "com.github.sybila.biodivine.SharedMemoryTaskKt",
                        consoleLogLevel.toString().toLowerCase(),
                        name ?: "task",
                        taskRoot.absolutePath,
                        config.toString()
                    ), logger, taskConfig.timeout)
                    logger.info("Shared memory verification finished with exit code: $code")
                    code == 0
                }

                else -> error("Unsupported communication method: ${taskConfig.communicator}")
            }
        }
        else -> error("Unsupported task type: ${taskConfig.type}")
    }
}