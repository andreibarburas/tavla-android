package com.brbrs.tavla.solidtime

/** Human-readable messages for Solidtime failures, mirroring Deck's friendlyError style. */
fun friendlyStError(e: Throwable): String = when {
    e is TimerBusyException -> "A timer is already running. Stop it first."
    e is SolidtimeApiException && e.code == 0 -> "Can't reach Solidtime. Queued for when you're back online."
    e is SolidtimeApiException && e.code == 401 -> "Solidtime rejected the token. Check it and reconnect."
    e is SolidtimeApiException && e.code == 403 -> "That token isn't allowed to do this."
    e is SolidtimeApiException && e.code == 404 -> "Not found on Solidtime. It may have been deleted."
    e is SolidtimeApiException && e.code == 422 -> "Solidtime rejected the data: ${e.message}"
    e is SolidtimeApiException -> e.message ?: "Solidtime returned an error (${e.code})."
    e.message?.contains("CLEARTEXT") == true -> "This server needs HTTPS (or allow HTTP for a private network)."
    e.message?.contains("Unable to resolve host") == true -> "Can't find that server. Check the URL."
    !e.message.isNullOrBlank() -> e.message!!
    else -> "Something went wrong (${e.javaClass.simpleName})."
}
