package com.github.sybila.biodivine

import com.github.sybila.biodivine.ctl.CTLParameterEstimationConfig
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
                    ), logger, taskConfig.timeout)
                    logger.info("Shared memory verification finished with exit code: $code")
                    code == 0
                }

                else -> error("Unsupported communication method: ${taskConfig.communicator}")
            }
        }
        else -> error("Unsupported task type: ${config.getString(c.type, c.ctlParameterEstimation)}")
    }
}