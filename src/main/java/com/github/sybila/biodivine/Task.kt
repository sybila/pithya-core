package com.github.sybila.biodivine

import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.concurrent.thread

data class DistributionSettings(
        val workers: Int = 1,
        val maxMemory: Int = 1024,
        val jobQueue: String = "SingleThreadJobQueue",
        val communicator: String = "SharedMemoryCommunicator",
        val partitioning: String = "uniform"
) {
    constructor(config: Map<*, *>) : this(
            (config["workers"] as Int?) ?: 1,
            (config["maxMemory"] as Int?) ?: 1024,
            (config["jobQueue"] as String?) ?: "SingleThreadJobQueue",
            (config["communicator"] as String?) ?: "SharedMemoryCommunicator",
            (config["partitioning"] as String?) ?: "uniform"
    )
}

fun executeTask(config: Map<*, *>, name: String?, root: File, consoleLogLevel: String) {
    val logger = Logger.getLogger("com.github.sybila")
    val type = (config["type"] as String?) ?: "ctlParamEstimation"
    val taskRoot = if (name == null) root else File(root, name)
    if (type != "ctlParamEstimation") {
        error("Unsupported task type: $type")
    }

    //first thing, get distribution settings. Based on that, we will have to start a new process
    val distConfig = config["distribution"] as Map<*, *>?
    val timeout = config["timeout"] as Int? ?: -1
    val distribution = if (distConfig == null) DistributionSettings() else DistributionSettings(distConfig)
    when (distribution.communicator) {
        "SharedMemoryCommunicator" -> {
            logger.info("Starting a shared memory verification process...")
            val process = Runtime.getRuntime().exec(arrayOf(
                    getJavaLocation(),
                    "-cp", System.getProperty("java.class.path"),
                    "-Xmx${distribution.maxMemory*distribution.workers}M",
                    "com.github.sybila.biodivine.SharedMemoryTaskKt",
                    consoleLogLevel,
                    name ?: "task",
                    taskRoot.absolutePath,
                    "${Yaml().dump(config)}"
            ))
            if (timeout > 0) {  //start timeout if necessary
                thread {
                    if (!process.waitFor(timeout.toLong(), TimeUnit.SECONDS)) {
                        logger.info("Task was killed after exceeding timeout ${timeout}s")
                        process.destroy()
                    }
                }
            }
            thread {    //start standard output reader
                process.inputStream.bufferedReader().use { cout ->
                    var line = cout.readLine()
                    while (line != null) {
                        println(line)   //info/debug is just printed, no need to don't log it...
                        line = cout.readLine()
                    }
                }
            }
            thread {    //start error reader
                process.errorStream.bufferedReader().use { cout ->
                    var line = cout.readLine()
                    while (line != null) {
                        logger.severe(line)
                        line = cout.readLine()
                    }
                }
            }
            process.waitFor()
            logger.info("Shared memory verification finished with exit code: ${process.exitValue()}")
        }
        else -> error("Unsupported communication method: ${distribution.communicator}")
    }
}

fun getJavaLocation(): String {
    return System.getProperties().getProperty("java.home") + File.separator + "bin" + File.separator +
            if (System.getProperty("os.name").startsWith("Win")) {
                "java.exe"
            } else "java"
}