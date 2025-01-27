/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.internal

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import kotlinx.io.core.*
import libcurl.*
import kotlin.coroutines.*

internal suspend fun HttpRequestData.toCurlRequest(config: HttpClientEngineConfig): CurlRequestData = CurlRequestData(
    url = url.toString(),
    method = method.value,
    headers = headersToCurl(),
    proxy = config.proxy,
    content = body.toCurlByteArray()
)

internal class CurlRequestData(
    val url: String,
    val method: String,
    val headers: CPointer<curl_slist>,
    val proxy: ProxyConfig?,
    val content: ByteArray?
) {
    override fun toString(): String =
        "CurlRequestData(url='$url', method='$method', content: ${content?.size ?: 0} bytes)"
}

internal class CurlResponseBuilder(val request: CurlRequestData) {
    val headersBytes = BytePacketBuilder()
    val bodyBytes = BytePacketBuilder()
}

internal sealed class CurlResponseData(
    val request: CurlRequestData
)

internal class CurlSuccess(
    request: CurlRequestData,
    val status: Int,
    val version: UInt,
    val headersBytes: ByteArray,
    val bodyBytes: ByteArray
) : CurlResponseData(request) {
    override fun toString(): String = "CurlSuccess(${HttpStatusCode.fromValue(status)})"
}

internal class CurlFail(
    request: CurlRequestData,
    val cause: Throwable
) : CurlResponseData(request) {
    override fun toString(): String = "CurlFail($cause)"
}

internal suspend fun OutgoingContent.toCurlByteArray(): ByteArray? = when (this@toCurlByteArray) {
    is OutgoingContent.ByteArrayContent -> bytes()
    is OutgoingContent.WriteChannelContent -> GlobalScope.writer(coroutineContext) {
        writeTo(channel)
    }.channel.readRemaining().readBytes()
    is OutgoingContent.ReadChannelContent -> readFrom().readRemaining().readBytes()
    is OutgoingContent.NoContent -> null
    else -> throw UnsupportedContentTypeException(this@toCurlByteArray)
}
