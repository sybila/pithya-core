package com.github.sybila.biodivine

import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.SimpleFormatter

class ConsoleHandler : Handler() {

    override fun publish(p0: LogRecord) {
        if (formatter == null) {
            formatter = SimpleFormatter()
        }

        val message = formatter.format(p0)
        if (p0.level.intValue() >= Level.WARNING.intValue()) {
            System.err.print(message)
        } else {
            System.out.print(message)
        }
    }

    override fun flush() {
        System.out.flush()
    }

    override fun close() {
        //Nothing
    }

}