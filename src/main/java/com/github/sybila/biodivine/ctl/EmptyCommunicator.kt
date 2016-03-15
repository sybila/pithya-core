package com.github.sybila.biodivine.ctl

import com.github.sybila.checker.Communicator

class EmptyCommunicator : Communicator {

    override val id: Int = 0
    override val size: Int = 1

    override fun <M : Any> addListener(messageClass: Class<M>, onTask: (M) -> Unit) {
        //nothing
    }

    override fun close() {
        //nothing
    }

    override fun removeListener(messageClass: Class<*>) {
        //nothing
    }

    override fun send(dest: Int, message: Any) {
        //shouldn't happen
        throw UnsupportedOperationException()
    }

}