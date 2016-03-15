package com.github.sybila.biodivine

/*
    Here you can define all the literals and names used in the config files
    so that you can use them safely in the code.
 */

val c = C()

class C {
    //global configuration
    val experiment = "experiment"
    val printBuildInfo = "printBuildInfo"
    val printEnvironmentInfo = "printEnvironmentInfo"
    val consoleLogLevel = "consoleLogLevel"
    val tasks = "tasks"

    //task types
    val type = "type"
    val ctlParameterEstimation = "CTLParameterEstimation"

    //ctl task configuration
    val timeout = "timeout"
    val maxMemory = "maxMemory"
    val communicator = "communicator"
        val noCommunicator = "none"
        val sharedMemory = "sharedMemory"
            val workers = "workers"
        val mpjLocal = "mpjLocal"
            val mpjHome = "mpjHome"
        val mpjCluster = "mpjCluster"
    val jobQueue = "jobQueue"
        val blockingQueue = "blockingQueue"
    val ctlParser = "ctlParser"
        val normalForm = "normalForm"
            val until = "until"
            val none = "none"
        val optimize = "optimize"
    val partitioning = "partitioning"
        val uniform = "uniform"
        val hash = "hash"
        val slice = "slice"
        val block = "block"
            val blockSize = "blockSize"
    val model = "model"
        val ODE = "ODE"
            val file = "file"
            val fastApproximation = "fastApproximation"
            val cutToRange = "cutToRange"
    val checker = "checker"
    val colors = "colors"
        val rectangular = "rectangular"
        val smt = "smt"
    val properties = "properties"
        val formula = "formula"
        val verify = "verify"
        val results = "results"
            val size = "size"
            val stats = "stats"
            val human = "human"


    val logLevel = "logLevel"

}

