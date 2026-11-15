package com.onikiri.open.data

import android.content.Context
import androidx.annotation.StringRes
import com.onikiri.open.R
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Exception whose message is a localized string resource, resolved only when shown to the user. */
class UiException(@param:StringRes val resId: Int, vararg val args: Any?) : Exception("string resource $resId")

fun Throwable.userMessage(context: Context): String = when (this) {
    is UiException -> context.getString(resId, *args)
    is UnknownHostException,
    is ConnectException,
    is SocketTimeoutException -> context.getString(
        R.string.err_network,
        localizedMessage ?: javaClass.simpleName
    )
    else -> localizedMessage ?: javaClass.simpleName
}
