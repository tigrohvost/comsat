package com.comsat.audio.data.api

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class CallAwaitTest {
    @Test
    fun `cancel closes the call and a response that arrives too late`() = runBlocking {
        val call = DeferredCall()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            call.awaitResponse().use { error("A cancelled selection must not resume") }
        }
        job.cancelAndJoin()
        assertTrue(call.isCanceled())
        val body = TrackingBody()
        call.callback.onResponse(call, call.response(body))
        assertTrue(body.closed)
    }

    @Test
    fun `successful response is owned and closed by the caller`() = runBlocking {
        val call = DeferredCall()
        val result = async(start = CoroutineStart.UNDISPATCHED) { call.awaitResponse() }
        val body = TrackingBody()
        call.callback.onResponse(call, call.response(body))
        result.await().use { assertEquals("audio", it.body.string()) }
        assertTrue(body.closed)
    }

    @Test
    fun `network failure reaches the caller`() = runBlocking {
        val call = DeferredCall()
        val result = async(start = CoroutineStart.UNDISPATCHED) {
            runCatching { call.awaitResponse() }
        }
        val error = IOException("Connection closed")
        call.callback.onFailure(call, error)
        val received = result.await().exceptionOrNull()
        assertTrue(received is IOException)
        assertEquals(error.message, received?.message)
    }

    private class TrackingBody : ResponseBody() {
        var closed = false
        private val buffer = object : ForwardingSource(Buffer().writeUtf8("audio")) {
            override fun close() {
                closed = true
                super.close()
            }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = 5L
        override fun source() = buffer
    }

    private class DeferredCall : Call by OkHttpClient().newCall(
        Request.Builder().url("https://example.invalid/feed").build()
    ) {
        lateinit var callback: Callback
        private var cancelled = false
        override fun request() = Request.Builder().url("https://example.invalid/feed").build()
        override fun execute(): Response = error("Only asynchronous calls are expected")
        override fun enqueue(responseCallback: Callback) { callback = responseCallback }
        override fun cancel() { cancelled = true }
        override fun isExecuted() = ::callback.isInitialized
        override fun isCanceled() = cancelled
        override fun clone(): Call = DeferredCall()
        override fun timeout() = Timeout.NONE
        fun response(body: ResponseBody) = Response.Builder()
            .request(request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body(body).build()
    }
}
