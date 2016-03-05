package com.github.sybila.biodivine

import java.io.File
import java.lang.management.ManagementFactory
import java.net.InetAddress
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*


fun setupExperiment(root: File, config: Map<*, *>) {
    val printBuildInfo = (config["printBuildInfo"] as Boolean?) ?: true
    if (printBuildInfo) {
        val buildInfoFile = File(root, "build-info.txt")
        // We have to print all modules by hand, because they are static :/
        buildInfoFile.bufferedWriter().use { out ->
            out.write("CTL Parser:\n")
            out.write("\tVERSION: ${com.github.sybila.ctl.BuildInfo.VERSION}\n")
            out.write("\tGIT_COMMIT: ${com.github.sybila.ctl.BuildInfo.GIT_COMMIT}\n")
            out.write("\tTIMESTAMP: ${com.github.sybila.ctl.BuildInfo.TIMESTAMP}\n")
            out.write("\tURL: ${com.github.sybila.ctl.BuildInfo.URL}\n")
            out.write("CTL Model Checker:\n")
            out.write("\tVERSION: ${com.github.sybila.checker.BuildInfo.VERSION}\n")
            out.write("\tGIT_COMMIT: ${com.github.sybila.checker.BuildInfo.GIT_COMMIT}\n")
            out.write("\tTIMESTAMP: ${com.github.sybila.checker.BuildInfo.TIMESTAMP}\n")
            out.write("\tURL: ${com.github.sybila.checker.BuildInfo.URL}\n")
            out.write("ODE State Space Generator:\n")
            out.write("\tVERSION: ${com.github.sybila.ode.BuildInfo.VERSION}\n")
            out.write("\tGIT_COMMIT: ${com.github.sybila.ode.BuildInfo.GIT_COMMIT}\n")
            out.write("\tTIMESTAMP: ${com.github.sybila.ode.BuildInfo.TIMESTAMP}\n")
            out.write("\tURL: ${com.github.sybila.ode.BuildInfo.URL}\n")
        }
    }
    val printEnvInfo = (config["printEnvironmentInfo"] as Boolean?) ?: true
    if (printEnvInfo) {
        File(root, "environment-info.txt").bufferedWriter().use { out ->
            out.write("Computer name: ${getComputerName()}\n")
            out.write("Operating system: ${System.getProperty("os.name")}\n")
            out.write("System time: ${SimpleDateFormat.getDateTimeInstance().format(Calendar.getInstance().time)}\n")
            out.write("Number of processors: ${Runtime.getRuntime().availableProcessors()}\n")
            out.write("Memory: ${maxRAM()}\n")
            out.write("Java version: ${System.getProperty("java.version")}\n")
        }
    }
}

fun createUniqueExperimentName(base: String): File {
    var dir = File(base)
    var counter = 0
    while (dir.exists()) {
        counter += 1
        dir = File("$base-$counter")
    }
    if (!dir.mkdir()) {
        error("Can't create experiment output directory.")
    } else return dir
}

fun String.trimExtension(): String {
    return this.substring(0, this.lastIndexOf("."))
}

fun getComputerName(): String {
    val env = System.getenv()
    return env["COMPUTERNAME"] ?: env["HOSTNAME"] ?: run {
        try {
            InetAddress.getLocalHost().hostName
        } catch (ex: UnknownHostException) {
            "Unknown"
        }
    }
}

fun maxRAM(): String {
    try {
        val MB = 1024 * 1024;
        val bean = ManagementFactory.getOperatingSystemMXBean() as com.sun.management.OperatingSystemMXBean
        return "physical ${bean.totalPhysicalMemorySize/MB}MB / swap ${bean.totalSwapSpaceSize/MB}MB / free ${bean.freePhysicalMemorySize/MB}MB"
    } catch (e: Exception) {
        return "Not available"
    }
}