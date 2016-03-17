package com.github.sybila.biodivine

import com.github.sybila.checker.Colors
import com.github.sybila.checker.Node
import com.github.sybila.checker.Nodes
import java.io.File
import java.io.PrintStream
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.concurrent.thread


fun String.trimExtension(): String {
    return this.substring(0, this.lastIndexOf("."))
}

fun String.toLogLevel(): Level {
    return when (this) {
        "off" -> Level.OFF
        "error", "severe" -> Level.SEVERE
        "warning" -> Level.WARNING
        "info" -> Level.INFO
        "fine" -> Level.FINE
        "finer" -> Level.FINER
        "finest" -> Level.FINEST
        "all" -> Level.ALL
        else -> error("Unknown log level: $this")
    }
}

fun getJavaLocation(): String {
    return System.getProperties().getProperty("java.home") + File.separator + "bin" + File.separator +
            if (System.getProperty("os.name").startsWith("Win")) {
                "java.exe"
            } else "java"
}


fun <N: Node, C: Colors<C>> processResults(
        id: Int,
        taskRoot: File,
        queryName: String,
        results: Nodes<N, C>,
        stats: Map<String, Any>,
        printConfig: Set<String>,
        logger: Logger
) {
    for (printType in printConfig) {
        when (printType) {
            c.size -> logger.info("Results size: ${results.entries.count()}")
            c.stats -> logger.info("Statistics: $stats")
            c.human -> {
                File(taskRoot, "$queryName.human.$id.txt").bufferedWriter().use {
                    for (entry in results.entries) {
                        it.write("${entry.key} - ${entry.value}\n")
                    }
                }
            }
            else -> error("Unknown print type: $printType")
        }
    }
}

fun guardedRemoteProcess(
        host: String,
        args: Array<String>,
        vars: Array<String>?,
        logger: Logger, timeout: Int = -1, shouldDie: () -> Boolean): Int {
    val command = "cd ${System.getProperty("user.dir")}; " +    //move to the right directory
            (vars?.map { " export $it; " }?.joinToString(separator = " ") ?: "") + //add environmental variables
            args.map { "\\\"$it\\\"" }.joinToString(separator = " ")    //escape arguments
    val sshProcess = Runtime.getRuntime().exec(arrayOf("ssh", host, command))
    val timeoutThread = if (timeout > 0) {
        thread {
            val start = System.currentTimeMillis()
            try {
                while (System.currentTimeMillis() - start < timeout * 1000L || !shouldDie()) {
                    Thread.sleep(1000L)
                }
                try {
                    sshProcess.exitValue()
                    //Process terminated fine
                } catch (e: IllegalThreadStateException) {
                    //Process is still alive!
                    sshProcess.destroy()
                    if (shouldDie()) {
                        logger.info("Task was killed due to an error in other process")
                    } else {
                        logger.info("Task was killed after exceeding timeout ${timeout}s")
                    }
                }
            } catch (e: InterruptedException) {
                //it's ok
            }
        }
    } else null
    val stdReader = thread {
        sshProcess.inputStream.bufferedReader().use { std ->
            if (std.ready()) {  //in case we finished before this thread even starts
                var line = std.readLine()
                while (line != null) {
                    println(line)   //standard output is not logged, only printed
                    line = std.readLine()
                }
            }
        }
    }
    val errReader = thread {
        sshProcess.errorStream.bufferedReader().use { err ->
            if (err.ready()) {
                //in case we finished before this thread even starts
                var line = err.readLine()
                while (line != null) {
                    logger.severe(line)
                    line = err.readLine()
                }
            }
        }
    }
    sshProcess.waitFor()
    timeoutThread?.interrupt()
    timeoutThread?.join()
    stdReader.join()
    errReader.join()
    return sshProcess.exitValue()
}

fun guardedProcess(args: Array<String>, vars: Array<String>?, logger: Logger, timeout: Int = -1): Int {
    val process = Runtime.getRuntime().exec(args, vars)
    val timeoutThread = if (timeout > 0) {
        thread {
            try {
                Thread.sleep(timeout * 1000L)
                try {
                    process.exitValue()
                    //Process terminated fine
                } catch (e: IllegalThreadStateException) {
                    //Process is still alive!
                    process.destroy()
                    logger.info("Task was killed after exceeding timeout ${timeout}s")
                }
            } catch (e: InterruptedException) {
                //it's ok
            }
        }
    } else null
    val stdReader = thread {
        process.inputStream.bufferedReader().use { std ->
            var line = std.readLine()
            while (line != null) {
                println(line)   //standard output is not logged, only printed
                line = std.readLine()
            }
        }
    }
    val errReader = thread {
        process.errorStream.bufferedReader().use { err ->
            var line = err.readLine()
            while (line != null) {
                logger.severe(line)
                line = err.readLine()
            }
        }
    }
    process.waitFor()
    timeoutThread?.interrupt()
    timeoutThread?.join()
    stdReader.join()
    errReader.join()
    return process.exitValue()
}