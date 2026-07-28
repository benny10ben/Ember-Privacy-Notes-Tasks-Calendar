package com.ben.inly.presentation.sync

import android.content.Context
import android.widget.Toast
import org.koin.core.context.GlobalContext

actual fun showSyncToast(message: String) {
    val context = GlobalContext.get().get<Context>()
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
