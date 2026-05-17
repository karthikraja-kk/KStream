package com.kstream.core.common

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps raw exceptions to user-friendly error messages.
 * Used across all ViewModels to ensure consistent, non-technical error display.
 */
fun Throwable.toUserMessage(): String = when {
    this is UnknownHostException ->
        "No internet connection. Please check your network and try again."
    this is SocketTimeoutException ->
        "Connection timed out. Please try again."
    this is ConnectException ->
        "Unable to connect to server. Please try again later."
    this is SSLException ->
        "Secure connection failed. Please try again."
    this is IOException ->
        "Network error. Please check your connection and try again."
    message?.contains("403") == true ->
        "Access denied. Please try again later."
    message?.contains("404") == true ->
        "Content not found. It may have been removed."
    message?.contains("500") == true || message?.contains("502") == true ||
        message?.contains("503") == true ->
        "Server is temporarily unavailable. Please try again later."
    else ->
        "Something went wrong. Please try again."
}
