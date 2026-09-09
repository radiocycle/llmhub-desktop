package dev.radiocycle.llmhub.core

object Log {
    fun d(tag: String, msg: String) {
        println("[DEBUG] [$tag] $msg")
    }

    fun i(tag: String, msg: String) {
        println("[INFO]  [$tag] $msg")
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        println("[WARN]  [$tag] $msg")
        t?.printStackTrace()
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        System.err.println("[ERROR] [$tag] $msg")
        t?.printStackTrace()
    }
}
