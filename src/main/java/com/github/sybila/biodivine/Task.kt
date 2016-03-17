package com.github.sybila.biodivine

import com.github.sybila.biodivine.ctl.*
import com.github.sybila.checker.guardedThread
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

fun executeTask(config: YamlMap, name: String?, root: File, consoleLogLevel: Level): Boolean {
    val logger = Logger.getLogger(rootPackage)
    val taskRoot = if (name == null) root else File(root, name)
    return when (config.getString(c.type, c.ctlParameterEstimation)) {

        c.ctlParameterEstimation -> {
            val taskConfig = CTLParameterEstimationConfig(config)

            when (taskConfig.communicator) {
                is MPJClusterCommunicatorConfig -> {
                    val mpjConfigFile = createMPJConfigFile(root, taskConfig.communicator)
                    val mpjHome = taskConfig.communicator.mpjHome ?: File(System.getenv("MPJ_HOME"))
                    if (!mpjHome.isDirectory) {
                        error("Invalid mpj home directory: $mpjHome")
                    }
                    logger.info("Starting a cluster MPJ verification process... ${taskConfig.communicator.hosts}")
                    var shouldDie = false
                    taskConfig.communicator.hosts.mapIndexed { rank, host -> guardedThread {
                        logger.info("Starting process $rank")
                        val code = guardedRemoteProcess(host, arrayOf(
                                getJavaLocation(),
                                "-cp", System.getProperty("java.class.path"),
                                "-Xmx${taskConfig.maxMemory}M",
                                "com.github.sybila.biodivine.ctl.MPJCommTaskKt",
                                rank.toString(),
                                mpjConfigFile.absolutePath,
                                "niodev",
                                consoleLogLevel.toString().toLowerCase(),
                                name ?: "task",
                                taskRoot.absolutePath,
                                config.toString()
                        ), arrayOf(
                                "MPJ_HOME=${mpjHome.absolutePath}",
                                "PATH=${mpjHome.absolutePath}/bin:${System.getenv("PATH")}"
                        ), logger, taskConfig.timeout) {
                            shouldDie
                        }
                        if (code != 0) {
                            shouldDie = true    //notify other processes
                        }
                        logger.info("Local MPJ verification finished with exit code: $code")
                    } }.map { it.join() }
                    !shouldDie
                }
                is MPJLocalCommunicatorConfig -> {
                    val mpjHome = taskConfig.communicator.mpjHome ?: File(System.getenv("MPJ_HOME"))
                    if (!mpjHome.isDirectory) {
                        error("Invalid mpj home directory: $mpjHome")
                    }
                    val workers = taskConfig.communicator.workers
                    logger.info("Starting a local MPJ verification process...")
                    val code = guardedProcess(arrayOf(
                            getJavaLocation(),
                            "-jar", "${mpjHome.absolutePath}/lib/starter.jar",
                            "-np", workers.toString(),
                            "-cp", System.getProperty("java.class.path"),
                            "-Xmx${taskConfig.maxMemory}M",
                            "com.github.sybila.biodivine.ctl.MPJCommTaskKt",
                            consoleLogLevel.toString().toLowerCase(),
                            name ?: "task",
                            taskRoot.absolutePath,
                            config.toString()
                    ), arrayOf(
                            "MPJ_HOME=${mpjHome.absolutePath}",
                            "PATH=${mpjHome.absolutePath}/bin:${System.getenv("PATH")}"
                    ), logger, taskConfig.timeout)
                    logger.info("Local MPJ verification finished with exit code: $code")
                    code == 0
                }
                is SharedCommunicatorConfig -> {
                    val workers = taskConfig.communicator.workers
                    logger.info("Starting a shared memory verification process...")
                    val code = guardedProcess(arrayOf(
                        getJavaLocation(),
                        "-cp", System.getProperty("java.class.path"),           //mirror current classpath (libraries, binaries)
                        "-Xmx${taskConfig.maxMemory * workers}M",               //set memory limit
                        "com.github.sybila.biodivine.ctl.SharedMemoryTaskKt",   //hardcoded main, no great, but will do for now
                        consoleLogLevel.toString().toLowerCase(),               //global log level
                        name ?: "task",                                         //task name
                        taskRoot.absolutePath,                                  //task path
                        config.toString()                                       //copy of yaml config
                    ), null, logger, taskConfig.timeout)
                    logger.info("Shared memory verification finished with exit code: $code")
                    code == 0
                }
                is NoCommunicatorConfig -> {    //the same as for shared comm
                    logger.info("Starting a sequential verification process...")
                    val code = guardedProcess(arrayOf(
                            getJavaLocation(),
                            "-cp", System.getProperty("java.class.path"),           //mirror current classpath (libraries, binaries)
                            "-Xmx${taskConfig.maxMemory}M",                         //set memory limit
                            "com.github.sybila.biodivine.ctl.EmptyCommTaskKt",      //hardcoded main, no great, but will do for now
                            consoleLogLevel.toString().toLowerCase(),               //global log level
                            name ?: "task",                                         //task name
                            taskRoot.absolutePath,                                  //task path
                            config.toString()                                       //copy of yaml config
                    ), null, logger, taskConfig.timeout)
                    logger.info("Sequential verification finished with exit code: $code")
                    code == 0
                }
                else -> error("Unsupported communication method: ${taskConfig.communicator}")
            }
        }
        else -> error("Unsupported task type: ${config.getString(c.type, c.ctlParameterEstimation)}")
    }
}

private fun createMPJConfigFile(root: File, config: MPJClusterCommunicatorConfig): File {
    val configFile = File(root, "mpj.config")
    val ports = config.portRange.split("-")
    if (ports.size != 2) error("Invalid port range: ${config.portRange}")
    val lowerPort = ports[0].toInt()
    val upperPort = ports[1].toInt()
    configFile.printWriter().use { file ->
        file.println(config.hosts.size.toString())
        file.println("131072")  //protocol switch limit, whatever that means
        var usedPort = lowerPort
        for ((rank, host) in config.hosts.withIndex()) {
            file.println("$host@$usedPort@${usedPort+1}@$rank$0")
            usedPort += 2
            if (usedPort > upperPort) error("Ran out of ports when creating mpj config file!")
        }
    }
    return configFile
}