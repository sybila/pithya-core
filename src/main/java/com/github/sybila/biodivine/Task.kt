package com.github.sybila.biodivine

import com.github.sybila.biodivine.ctl.CTLParameterEstimationConfig
import com.github.sybila.biodivine.ctl.MPJLocalCommunicatorConfig
import com.github.sybila.biodivine.ctl.NoCommunicatorConfig
import com.github.sybila.biodivine.ctl.SharedCommunicatorConfig
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