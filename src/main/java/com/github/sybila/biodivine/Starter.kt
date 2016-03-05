package com.github.sybila.biodivine

import com.github.sybila.checker.lInfo
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.util.*
import java.util.logging.*


/**
 * This is "The Main" main. The purpose of this is to load the config file, parse it,
 * prepare experiment environment and then call the model checker, or otherwise initiate the
 * computation (in case of mpj, we will probably have to start a new process)
 **/

fun main(args: Array<String>) {
    /*if (args.size != 1) {
        error("Expecting one argument that is a readable configuration file. ${args.size} arguments given.")
    }*/
    //val configFile = File(args[0])
    val configFile = File("/Users/daemontus/Workspace/Sybila/experiments/config.yaml")
    if (!configFile.isFile) {
        error("Expecting one argument that is a readable configuration file. ${args[0]} is not a file.")
    }
    if (!configFile.canRead()) {
        error("Expecting one argument that is a readable configuration file. ${args[0]} is not readable.")
    }

    val config = Yaml().load(configFile.inputStream()) as Map<*,*>

    val name = (config["experiment"] as String?) ?: configFile.name.trimExtension()
    val root = createUniqueExperimentName(name)

    setupExperiment(root, config)

    val tasks = (config["tasks"] as List<*>?) ?: ArrayList<Any>()
    val consoleLogLevel = config["consoleLogLevel"] as String? ?: "info"

    //Configure default logger
    val logger = Logger.getLogger("com.github.sybila")
    logger.useParentHandlers = false    //disable default top level logger
    logger.addHandler(ConsoleHandler().apply {
        this.level = consoleLogLevel.toLogLevel()
        this.formatter = CleanFormatter()
    })
    logger.addHandler(FileHandler("${root.name}/global-log.log").apply {
        this.formatter = SimpleFormatter()  //NO XML!
    })

    logger.lInfo { "Experiment is prepared. Executing tasks..." }

    if (tasks.size == 0) {
        logger.warning("No tasks specified!")
    }
    if (tasks.size == 1) {
        //if we have only one task, we don't need extra folders, just dump it into root
        val task = tasks[0]
        if (task !is MutableMap<*,*>) {
            error("Invalid task in config file!")
        } else {
            executeTask(task, null, root, consoleLogLevel)
        }
    } else {
        tasks.forEachIndexed { i, task ->
            if (task !is Map<*,*>) {
                error("Invalid task in config file!")
            } else {
                executeTask(task, "task-$i", root, consoleLogLevel)
            }
        }
    }
}

fun String.toLogLevel(): Level = when (this) {
    "off" -> Level.OFF
    "error" -> Level.SEVERE
    "warning" -> Level.WARNING
    "info" -> Level.INFO
    "fine" -> Level.FINE
    "finer" -> Level.FINER
    "finest" -> Level.FINEST
    "all" -> Level.ALL
    else -> error("Unsupported log level: $this")
}