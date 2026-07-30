package com.ben.inly.domain.media

expect object LocalMediaGcLog {
    fun d(message: String)
    fun e(message: String, throwable: Throwable? = null)
}
