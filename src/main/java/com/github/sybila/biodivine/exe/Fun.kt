package com.github.sybila.biodivine.exe

import java.io.File
import java.net.ServerSocket
import java.net.SocketException
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    startShiny(args) { args ->
        println("Shiny app running")
    }
}

fun startShiny(args: Array<String>, main: (Array<String>) -> Unit) {
    val port = args[0].toInt()
    File(args[1]).bufferedWriter().use { notificationFile ->
        ServerSocket(port).use { killer ->
            Thread {
                while (!killer.isClosed) {
                    //accept multiple connections
                    try {
                        killer.accept().use { connection ->
                            connection.inputStream.bufferedReader().useLines {
                                if (it.any { it == "kill" }) {
                                    notificationFile.write("Killed\n")
                                    notificationFile.flush()
                                    exitProcess(0)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        //fail silently
                        if (e !is SocketException) {
                            println("Kill switch error: $e")
                        }
                    }
                }
            }.apply { this.isDaemon = true }.start()
            val app = Thread {
                try {
                    main(args.toList().drop(2).toTypedArray())
                    notificationFile.write("Success\n")
                    notificationFile.flush()
                } catch (e: Exception) {
                    notificationFile.write("Error\n")
                    notificationFile.write(e.message ?: e.javaClass.canonicalName)
                    notificationFile.write("\n")
                    notificationFile.flush()
                }
            }
            app.start()
            app.join()  //keep the socket open while app is running
        }
    }
}