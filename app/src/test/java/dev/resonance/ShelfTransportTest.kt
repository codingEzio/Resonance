package dev.resonance

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.ServerSocket
import java.net.SocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Test

class ShelfTransportTest {
    @Test
    fun catalogUsesDirectConnectionWhenSystemProxyIsConfigured() {
        val previous = ProxySelector.getDefault()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val authorization = AtomicReference("")
            val receiver =
                thread(isDaemon = true) {
                    server.accept().use { socket ->
                        val input = socket.getInputStream().bufferedReader()
                        while (true) {
                            val line = input.readLine() ?: break
                            if (line.isEmpty()) break
                            if (line.startsWith("Authorization:", ignoreCase = true))
                                authorization.set(line)
                        }
                        socket
                            .getOutputStream()
                            .write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\n{}".toByteArray())
                    }
                }
            try {
                ProxySelector.setDefault(
                    object : ProxySelector() {
                        override fun select(uri: URI?): List<Proxy> =
                            listOf(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 1)))

                        override fun connectFailed(
                            uri: URI?,
                            address: SocketAddress?,
                            error: java.io.IOException?,
                        ) {}
                    }
                )
                assertEquals(
                    "{}",
                    HttpShelfTransport().catalog("http://127.0.0.1:${server.localPort}", "test-key"),
                )
                receiver.join(5000)
                assertEquals("Authorization: Bearer test-key", authorization.get())
            } finally {
                ProxySelector.setDefault(previous)
            }
        }
    }
}
