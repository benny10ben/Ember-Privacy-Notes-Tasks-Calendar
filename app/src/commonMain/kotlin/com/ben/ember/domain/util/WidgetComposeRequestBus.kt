package com.ben.ember.domain.util

import kotlinx.coroutines.flow.MutableStateFlow

enum class WidgetComposeRequest { NEW_TASK, NEW_NOTE }

object WidgetComposeRequestBus {
    private val pendingRequest = MutableStateFlow<WidgetComposeRequest?>(null)

    fun request(request: WidgetComposeRequest) {
        pendingRequest.value = request
    }

    fun consume(request: WidgetComposeRequest): Boolean =
        pendingRequest.compareAndSet(request, null)
}
