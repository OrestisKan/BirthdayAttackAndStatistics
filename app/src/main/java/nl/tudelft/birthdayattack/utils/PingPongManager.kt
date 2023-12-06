package nl.tudelft.birthdayattack.utils

import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.Executors

class PingPongManager(private val listener: PingPongListener) {

    interface PingPongListener {
        fun onPingReceived(peer: Peer)
    }

    private val executor = Executors.newFixedThreadPool(2)

    fun startServer() {
        executor.execute {

                val datagramSocket = DatagramSocket()
                Log.d(TAG, "Server started")

                while (true) {
                    try {
                        val buffer = ByteArray(1024)
                        val packet = DatagramPacket(buffer, buffer.size)
                        datagramSocket.receive(packet)
                        val clientAddress = packet.address.hostAddress
                        val clientPort = packet.port

                        executor.execute {
                            handleClient(clientAddress!!, clientPort)
                        }
                    } catch (e: IOException) {
                        Log.e(TAG, "Error in server: ${e.message}")
                    }
                }

        }
    }

    private fun handleClient(address: String, port: Int) {
        try {
            val socket = DatagramSocket()
            val clientAddress = InetAddress.getByName(address)
            val buffer = ByteArray(1024)

            while (true) {
                val packet = DatagramPacket(buffer, buffer.size, clientAddress, port)
                socket.receive(packet)
                val message = String(buffer, 0, packet.length)
                Log.d(TAG, "Received message: $message from $address:$port")

                if (message == "PING") {
                    listener.onPingReceived(Peer(address, port))
                    sendPong(socket, clientAddress, port)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error handling client: ${e.message}")
        }
    }

    fun sendPing(peer: Peer) {
        executor.execute {
            try {
                val socket = DatagramSocket()
                val message = "PING\n"
                val buffer = message.toByteArray()
                val packet = DatagramPacket(buffer, buffer.size, InetAddress.getByName(peer.ipAddress), peer.port)
                socket.send(packet)
                Log.d(TAG, "Sent PING to ${peer.ipAddress}:${peer.port}")
                socket.close()
            } catch (e: IOException) {
                Log.e(TAG, "Error sending PING: ${e.message}")
            }
        }
    }

    private fun sendPong(socket: DatagramSocket, address: InetAddress, port: Int) {
        try {
            val message = "PONG\n"
            val buffer = message.toByteArray()
            val packet = DatagramPacket(buffer, buffer.size, address, port)
            socket.send(packet)
        } catch (e: IOException) {
            Log.e(TAG, "Error sending PONG: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "PingPongManager"

        fun createPingPongListener(): PingPongListener {
            return object : PingPongListener {
                override fun onPingReceived(peer: Peer) {
                    Handler(Looper.getMainLooper()).post {
                        println("Ping received from ${peer.ipAddress}:${peer.port}\n")
                    }
                }
            }
        }
    }
}

data class Peer(val ipAddress: String, val port: Int)
