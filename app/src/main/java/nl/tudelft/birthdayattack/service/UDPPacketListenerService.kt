package nl.tudelft.birthdayattack.service
//
//import android.app.Service
//import android.content.Intent
//import android.os.Binder
//import android.os.IBinder
//import java.net.DatagramPacket
//import java.net.DatagramSocket
//import java.net.InetSocketAddress
//
//class UDPPacketListenerService : Service() {
//    private var completed = false
//
//    private val binder = LocalBinder()
//
//    inner class LocalBinder : Binder() {
//        // Return this instance of LocalService so clients can call public methods.
//        fun getService(): UDPPacketListenerService = this@UDPPacketListenerService
//    }
//
//    override fun onBind(intent: Intent): IBinder {
//        return binder
//    }
//
//    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
//        // Start a separate thread for UDP packet listening
//        Thread {
//            val udpSocket = DatagramSocket()
//            val receiveBuffer = ByteArray(1024)
//            val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
//            udpSocket.soTimeout = 1000 // Adjust the timeout as needed
//            try {
//                udpSocket.receive(receivePacket)
//                val receivedIP = receivePacket.address.hostAddress
//                val receivedPort = receivePacket.port
//
//                udpHandler.post {
//                    val s = "Received from $receivedIP:$receivedPort"
//                    statusTextView.text = s
//                }
//
//                // Stop sending packets and start connection maintenance
//                println("Packet received from $receivedIP:$receivedPort")
//                succesfullConnections.add(InetSocketAddress(receivedIP, receivedPort))
//                mService.setCompleted(true)
//
//            } catch (e: Exception) {
//                // No response received, increment port and continue
//
//            }
//        }.start()
//
//        return START_STICKY
//    }
//
//    fun isCompleted(): Boolean {
//        return completed
//    }
//
//    fun setCompleted(value: Boolean) {
//        completed = value
//    }
//}
