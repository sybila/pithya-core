package com.github.sybila.biodivine

import java.util.logging.Formatter
import java.util.logging.LogRecord

class CleanFormatter(
        private val prefix: String = ""
) : Formatter() {

    override fun format(log: LogRecord): String {
        return prefix+log.message+"\n"
    }

}