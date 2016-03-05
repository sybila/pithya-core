package com.github.sybila.biodivine

import java.io.File
import java.util.concurrent.TimeUnit
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

fun guardedProcess(args: Array<String>, logger: Logger, timeout: Int = -1): Int {
    val process = Runtime.getRuntime().exec(args)
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