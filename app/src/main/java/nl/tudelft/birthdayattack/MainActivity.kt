package nl.tudelft.birthdayattack

import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import nl.tudelft.birthdayattack.utils.ConnectivityObserver
import nl.tudelft.birthdayattack.utils.IPAddressViewModel
import nl.tudelft.birthdayattack.utils.NetworkStatisticsManager
import nl.tudelft.birthdayattack.utils.PackageType
import nl.tudelft.birthdayattack.utils.Packet
import nl.tudelft.birthdayattack.utils.Peer
import nl.tudelft.birthdayattack.utils.PingPongManager
import nl.tudelft.birthdayattack.utils.logTime
import nl.tudelft.birthdayattack.utils.measurePerformanceInMS
import nl.tudelft.birthdayattack.utils.generateRandomString
import nl.tudelft.birthdayattack.utils.getNextPortToTry
import nl.tudelft.birthdayattack.utils.isValidIpAddress
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.function.Consumer

class MainActivity : AppCompatActivity() {
    private lateinit var ipAddressEditText: EditText
    private lateinit var startButton: Button
    private lateinit var statusTextView: TextView
    private lateinit var connectivityStatusTextView: TextView
    private lateinit var testingOptionsSpinner: Spinner
    private lateinit var toggleTestingButton: ToggleButton
    private lateinit var isMainTesterToggle: ToggleButton
    private lateinit var testingOptionsLayout: LinearLayout
    private lateinit var startTestingButton: Button
    private lateinit var startCollectingNetworkStatisticsButton: Button
    private lateinit var exportNetworkStatisticsButton: Button
    private lateinit var ipAddressTextView: TextView
    private lateinit var ipAddressViewModel: IPAddressViewModel
    private lateinit var testResultTextView: TextView


    private lateinit var selectedTest: String
    private var isMainTester: Boolean = false


    private val udpSocket = DatagramSocket()
    private val udpExecutor = Executors.newSingleThreadExecutor()

    private var portsAttempted = mutableSetOf(0)
    private var succesfullConnections = mutableSetOf<InetSocketAddress>()
    private val senderId = "Orestis1999"

    @Volatile
    private var isConnected = false
    private lateinit var keepConnectionsJob: Job
    private lateinit var messageReceiverJob: Job

    private lateinit var connectivityObserver: ConnectivityObserver

    private lateinit var networkStatisticsManager: NetworkStatisticsManager

    private lateinit var pingPongManager: PingPongManager

    private lateinit var stunResultJob: Deferred<Triple<String?, String?, Int?>>
    private lateinit var stunResult: Triple<String?, String?, Int?>

    private val messageReceivedQueue = ArrayBlockingQueue<DatagramPacket>(10)

    @Volatile
    private var lastUDPReceived: Triple<String, Int, Packet>? = null
    private var bandwidthMeasurementDataStructure: Triple<Long, Long, Long>? = null



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeUIElements()

        initializeComponents()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun initializeUIElements() {
        ipAddressEditText = findViewById(R.id.ipAddressEditText)
        startButton = findViewById(R.id.startButton)
        statusTextView = findViewById(R.id.statusTextView)
        connectivityStatusTextView = findViewById(R.id.connectivityStatus)
        testingOptionsSpinner = findViewById(R.id.testingOptionsSpinner)
        testingOptionsLayout = findViewById(R.id.testingOptionsLayout)
        toggleTestingButton = findViewById(R.id.toggleTestingButton)
        isMainTesterToggle = findViewById(R.id.isMainTesterToggleButton)
        startTestingButton = findViewById(R.id.startTestingButton)
        startCollectingNetworkStatisticsButton = findViewById(R.id.gatherStatisticsButton)
        exportNetworkStatisticsButton = findViewById(R.id.exportStatisticsButton)
        ipAddressTextView = findViewById(R.id.ipAddressTextView)
        testResultTextView = findViewById(R.id.testResult)

        startCollectingNetworkStatisticsButton.setOnClickListener {
            startCollectingNetworkStatistics()
        }

        exportNetworkStatisticsButton.setOnClickListener {
            stopCollectingNetworkStatistics()
            exportNetworkStatistics()
        }

        messageReceiverJob = GlobalScope.launchPeriodicAsync(0 , CoroutineStart.DEFAULT) {
            bundle()
        }


        startButton.setOnClickListener {

            //todo not 100% if these 3 should be kept here
            stopConnectionMaintenanceJob()
            stopMessageReceiverJob()

            keepConnectionsJob = GlobalScope.launchPeriodicAsync(TimeUnit.MINUTES.toMillis(1), CoroutineStart.LAZY) {
                succesfullConnections.parallelStream().forEach {startConnectionMaintenance(it)}
                println("Connection maintenance messages are sent!")
            }
            keepConnectionsJob.start()


            val ipAddress = ipAddressEditText.text.toString()
            if (!isValidIpAddress(ipAddress)) Toast.makeText(this@MainActivity, "Please enter a valid IP Address", Toast.LENGTH_SHORT).show()
            connect(ipAddress)
        }

        val testingOptions = resources.getStringArray(R.array.testing_options)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, testingOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        testingOptionsSpinner.adapter = adapter

        toggleTestingButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                testingOptionsLayout.visibility = View.VISIBLE
                startButton.visibility = View.GONE
            } else {
                testingOptionsLayout.visibility = View.GONE
                startButton.visibility = View.VISIBLE

            }
        }

        isMainTesterToggle.setOnCheckedChangeListener {_, isChecked ->
            isMainTester = isChecked
            testResultTextView.visibility = if (isChecked) View.VISIBLE else View.GONE //todo validate
        }

        testingOptionsSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTest = testingOptions[position]
                Toast.makeText(this@MainActivity, "Selected option: $selectedTest", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedTest = testingOptions[0]
            }
        }


        startTestingButton.setOnClickListener {
            val ipAddress = ipAddressEditText.text.toString()
            if (!isValidIpAddress(ipAddress)) {
                Toast.makeText(this@MainActivity, "Please enter a valid IP Address", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            println("didn't stop !!!!")

            if(isMainTester) {
                when(selectedTest) {
                    testingOptions[0] -> GlobalScope.launch {calculateResetTime(ipAddress)}
                    testingOptions[1] -> GlobalScope.launch {performUDPBandwidthTest(ipAddress ,  60)}
                    testingOptions[2] -> {} //measureBirthdayAttackTime(ipAddress)
                    testingOptions[3] -> {} //startPingPong(connectedSocketAddress!!.address.hostAddress!!, connectedSocketAddress!!.port)
                }
            }else {
                when(selectedTest) {
                    testingOptions[0] -> calculateResetTimeReceiver(ipAddress)
                    testingOptions[1] -> performUDPBandwidthTestReceiver(ipAddress)
                    testingOptions[2] -> {}
                    testingOptions[3] -> {}
                }
            }
        }

        ipAddressViewModel = ViewModelProvider(this)[IPAddressViewModel::class.java]
        ipAddressViewModel.ipLiveData.observe(this) { ip ->
            val str = "IP Address: $ip"
            ipAddressTextView.text = str
        }

        ipAddressViewModel.retrieveIpAddress()


    }
    private fun bundle() {
        checkMessageReceived()
        messageProcessor()
    }

    private fun initializeComponents() {
        //todo these are crushing it
//        stunResultJob = GlobalScope.async { getIpInfo() }
//
//        connectivityObserver = NetworkConnectivityObserver(applicationContext)
//        connectivityObserver.observe().onEach {
//            println("Status is $it")
//        }.launchIn(lifecycleScope)
//        setContent {
//            val status by connectivityObserver.observe().collectAsState(
//                initial = ConnectivityObserver.Status.Unavailable
//            )
//            val statusString = "Network Status = $status"
//            connectivityStatusTextView.text = statusString
//        }
        networkStatisticsManager = NetworkStatisticsManager(applicationContext)
        pingPongManager = PingPongManager(PingPongManager.createPingPongListener())
    }

    private fun startBirthdayAttack(ipAddress: String, packageType: PackageType = PackageType.CONNECTION_INITIATION) {
            isConnected = false
            udpExecutor.execute {
                try {
                    var counter = 1
                    val address = InetAddress.getByName(ipAddress)
                    while (!isConnected && counter < 170000) {
                        val packetId = generateRandomString(10)
                        val packet = Packet(senderId, packetId, System.currentTimeMillis(), packageType, false, counter, null, null)
                        val udpBuffer = Packet.serialize(packet)
                        val port = getNextPortToTry(portsAttempted)
                        val udpPacket = DatagramPacket(udpBuffer, udpBuffer.size, address, port)
                        println("Sending connection establishment packet to $address:$port   Attempt #$counter")
                        udpSocket.send(udpPacket)
                        portsAttempted.add(port)
                        if (portsAttempted.size == 65536) portsAttempted = mutableSetOf(0)
                        counter++
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
//            }
        }
    }

    override fun onStop() {
        super.onStop()
        stopConnectionMaintenanceJob()
        stopMessageReceiverJob()
        stopCollectingNetworkStatistics()
    }




    private fun connect(ipAddress: String){
        // todo Figure out if it requires bdayattack etc
        //  consider that some NATs has port preservation  x1 => x1 or something like x1 + 1

//        if (!this::stunResult.isInitialized){
//            GlobalScope.launch{
//                stunResult = stunResultJob.await()
//            }
//        }
        //todo use stunresult


        startBirthdayAttack(ipAddress)
    }

    //todo move ?
    private fun sendPacket(ipAddress: String, port: Int, packet: Packet): Int {
        isConnected = false
        var size = 0
        udpExecutor.execute {
            try{
                val udpBuffer = Packet.serialize(packet)
                val address = InetAddress.getByName(ipAddress)
                val udpPacket = DatagramPacket(udpBuffer, udpBuffer.size, address, port)
                size = udpBuffer.size
                println("Sending packet to $address:$port")
                udpSocket.send(udpPacket)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return size
    }


    //todo move this somewhere else
    private fun startConnectionMaintenance(inetSocketAddress: InetSocketAddress): Consumer<in InetSocketAddress>? {
        val ipAddress = inetSocketAddress.address.toString().split("/")[1]
        val port = inetSocketAddress.port
        udpExecutor.execute {
            try {
                val packetId = generateRandomString(12)
                val packet = Packet(senderId, packetId, System.currentTimeMillis(), PackageType.CONNECTION_MAINTENANCE, false,null, null, null)
                val udpMaintenanceBuffer = Packet.serialize(packet)
                val address = InetAddress.getByName(ipAddress)
                val udpPacket = DatagramPacket(udpMaintenanceBuffer, udpMaintenanceBuffer.size, address, port)
                udpSocket.send(udpPacket)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return null
    }





    private fun checkMessageReceived() {
        val receiveBuffer = ByteArray(1024)
        val receivePacket = DatagramPacket(receiveBuffer, receiveBuffer.size)
        udpSocket.soTimeout = 1000 // todo optimize
        try {
            udpSocket.receive(receivePacket)
            messageReceivedQueue.put(receivePacket)
        } catch (e: Exception) {
            // No response received, increment port and continue
        }
    }

    private fun messageProcessor() {
        // Retrieve a packet from the queue and notify the listener
        if (messageReceivedQueue.isEmpty()) return
        val datagramReceived = messageReceivedQueue.take()
        val packet = Packet.deserialize(datagramReceived.data)
        val receivedIP = datagramReceived.address
        val receivedPort = datagramReceived.port
        lastUDPReceived = Triple(receivedIP.address.toString(), receivedPort, packet)

        when (packet.packageType) {
            PackageType.CONNECTION_INITIATION -> {
                isConnected = true
                succesfullConnections.add(InetSocketAddress(receivedIP, receivedPort))
                val maintenancePacket = Packet(senderId, packet.packageId, System.currentTimeMillis(), PackageType.CONNECTION_ESTABLISHED, true, packet.count, null, null)

                sendPacket(receivedIP.address.toString(), receivedPort, maintenancePacket)


            }
            PackageType.CONNECTION_MAINTENANCE -> {
                //todo? update some connections list??
            }
            PackageType.TIMEOUT_MEASUREMENT -> {timeoutMeasurementPacketReceived(receivedIP.hostAddress!!.toString(), receivedPort, packet)}

            PackageType.BANDWIDTH_MEASUREMENT -> {
                bandwidthMeasurementDataStructure = if(bandwidthMeasurementDataStructure == null) {
                    Triple(System.currentTimeMillis(), System.currentTimeMillis(), Packet.serialize(packet).size.toLong())
                }else{
                    Triple(bandwidthMeasurementDataStructure!!.first, System.currentTimeMillis(), bandwidthMeasurementDataStructure!!.third + Packet.serialize(packet).size)
                }
                println("Bandwidth Measurement Result: \n  " +
                        "First Package Received at: ${bandwidthMeasurementDataStructure!!.first}, last at: ${bandwidthMeasurementDataStructure!!.second} and total size received is ${bandwidthMeasurementDataStructure!!.third}")
            }

            PackageType.CONNECTION_ESTABLISHED -> {
                isConnected = true
                succesfullConnections.add(InetSocketAddress(receivedIP, receivedPort))
                //TODO STOP RUNNING BIRTHDAY ATTACK ?
            }
        }

        runOnUiThread {
            val s = "Received and processed packet from $receivedIP:$receivedPort"
            statusTextView.text = s
        }
        println("Packet received from $receivedIP:$receivedPort with contents $packet is processed!")
    }

    private fun stopConnectionMaintenanceJob() {
        if (this::keepConnectionsJob.isInitialized && keepConnectionsJob.isActive) {
            keepConnectionsJob.cancel()
        }
    }

    private fun stopMessageReceiverJob() {
        if (this::messageReceiverJob.isInitialized && messageReceiverJob.isActive) {
            messageReceiverJob.cancel()
        }
    }

    @OptIn(DelicateCoroutinesApi::class) //todo remove global scope
    fun GlobalScope.launchPeriodicAsync(repeatMillis: Long,  coroutineStart: CoroutineStart, action: () -> Unit) = async(start= coroutineStart) {
        while (isActive) {
            action()
            delay(repeatMillis)

        }
    }

    private suspend fun calculateResetTime(ipAddress: String) {
        val stepSize = 20 * 1000L
        var currentTime = 30 * 1000L
        val packageId = generateRandomString(16)
        var count = 1
        val packet = Packet(senderId, packageId, System.currentTimeMillis(), PackageType.TIMEOUT_MEASUREMENT, false, count, null, null) //todo this is wrong

        startBirthdayAttack(ipAddress) //todo maybe randomly restart?
        try {
            withTimeout(60000) {
                waitToReceivePacket()
            }
        }catch (_: TimeoutCancellationException) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Failed to connect using Birthday Attack", Toast.LENGTH_SHORT).show()
            }
            return
        }

        println("Successful birthday Attack!")
        //todo sendan initial packagethere, cause the other is not stopping the birthday attack ???

        var isConverged = false
        while (currentTime < 15 * 60 * 1000) {
            println("Sleeping for $currentTime milis")
            delay(currentTime)
            sendPacket(lastUDPReceived!!.first, lastUDPReceived!!.second, packet)
            lastUDPReceived = null
            try {
                withTimeout(5000) {
                    waitToReceivePacket()
                }
            }catch (_: TimeoutCancellationException) {
                if(count == 1) {
                    println("Timeout is less than 30 seconds!")
                    return
                }
                isConverged = true
                break
            }
            currentTime += stepSize
            count ++
        }

        val resultText = if (isConverged) {
            "Converged! The reset time is [${currentTime - stepSize}, $currentTime] milliseconds"
        }else {
            "Did not converge! The reset time is more than 15 minutes!"
        }

        println(resultText)
        runOnUiThread {
            testResultTextView.text = resultText
        }

    }

    private suspend fun waitToReceivePacket() {
        while (lastUDPReceived == null) {
            println("Waiting for response")
            delay(100) // Simulating some delay in your logic
        }
    }

    private fun calculateResetTimeReceiver(ipAddress: String) {
        startBirthdayAttack(ipAddress) //  todo maybe run multiple times?
    }

    private fun timeoutMeasurementPacketReceived(ipAddress: String, port: Int, packetReceived: Packet) {
        if(!isMainTester){
            val packageId = generateRandomString(16)
            val packet = Packet(senderId, packageId, System.currentTimeMillis(), PackageType.TIMEOUT_MEASUREMENT, true, packetReceived.count, packetReceived.sentTimestamp, null)
            sendPacket(ipAddress, port, packet)

        }
    }



    //End methods for calculating the reset time

    //Start Bandwidth Measurement Functions

    private suspend fun performUDPBandwidthTest(ipAddress: String, durationSec: Int) {

        //todo Send the first packet as bandwidth measurement to prepare them and then
        // wait a sec or smth and send eveything else

        var counter =0
        val packageId = generateRandomString(16)
        var totalSize = 0

        startBirthdayAttack(ipAddress) //todo maybe randomly restart?
        try {
            withTimeout(60000) {
                waitToReceivePacket()
            }
        }catch (_: TimeoutCancellationException) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Failed to connect using Birthday Attack", Toast.LENGTH_SHORT).show()
            }
            return
        }



        val endTime = System.currentTimeMillis() + (durationSec * 1000)
        while (System.currentTimeMillis() < endTime) {
            val packet = Packet(senderId, packageId, System.currentTimeMillis(), PackageType.BANDWIDTH_MEASUREMENT, false, counter, null, null)
            totalSize += sendPacket(lastUDPReceived!!.first, lastUDPReceived!!.second, packet)
            counter ++
        }


        val resultText = "Managed to send $counter in $durationSec secs. Total of ${totalSize*counter} bytes where transferred"
        println(resultText)
        runOnUiThread {testResultTextView.text = resultText}

    }

    //Measure time to perform a successful birthday attack
    private fun measureBirthdayAttackTime(ipAddress: String) {
        measurePerformanceInMS({time -> logTime(time) }){
            startBirthdayAttack(ipAddress)
        }
    }

    private  fun performUDPBandwidthTestReceiver(ipAddress: String) {
        startBirthdayAttack(ipAddress)
    }



        //Network statistics
    private fun startCollectingNetworkStatistics() {
        networkStatisticsManager.startStatisticsCollection()
    }

    private fun stopCollectingNetworkStatistics() {
        networkStatisticsManager.stopStatisticsCollection()
    }

    private fun exportNetworkStatistics() {
        networkStatisticsManager.exportNetworkStatistics()
    }

    // PingPongManager
    private fun startPingPong(ipAddress: String, port:Int) {
        pingPongManager.startServer()
        val peer = Peer(ipAddress, port)
        pingPongManager.sendPing(peer)
    }
}
