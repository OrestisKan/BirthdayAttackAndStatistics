package nl.tudelft.birthdayattack.utils

import android.net.TrafficStats
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import kotlin.coroutines.CoroutineContext

class NetworkStatisticsManager(private val context: android.content.Context) : CoroutineScope {

    private val statsFile: File by lazy {
        File(context.getExternalFilesDir(null), "network_stats.txt")
    }

    private var job: Job? = null

    init {
        // Initialize the file and write header if the file doesn't exist
        if (!statsFile.exists()) {
            statsFile.createNewFile()
            writeHeader()
        }
    }

    private fun writeHeader() {
        PrintWriter(FileWriter(statsFile, true)).use { writer ->
            writer.println("Timestamp,TotalRxBytes,TotalTxBytes,MobileRxBytes,MobileTxBytes,UidRxBytes,UidTxBytes")
        }
    }

    private fun getCurrentTimestamp(): Long {
        return System.currentTimeMillis()
    }

    private fun getNetworkStatistics(): String {
        val timestamp = getCurrentTimestamp()
        val totalRxBytes = TrafficStats.getTotalRxBytes()
        val totalTxBytes = TrafficStats.getTotalTxBytes()
        val mobileRxBytes = TrafficStats.getMobileRxBytes()
        val mobileTxBytes = TrafficStats.getMobileTxBytes()
        val uid = android.os.Process.myUid()
        val uidRxBytes = TrafficStats.getUidRxBytes(uid)
        val uidTxBytes = TrafficStats.getUidTxBytes(uid)

        return "$timestamp,$totalRxBytes,$totalTxBytes,$mobileRxBytes,$mobileTxBytes,$uidRxBytes,$uidTxBytes"
    }

    fun startStatisticsCollection() {
        job = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(5000) // Adjust the delay as needed (every 5 seconds in this example)
                exportNetworkStatistics()
            }
        }
    }

    fun stopStatisticsCollection() {
        job?.cancel()
    }

    fun exportNetworkStatistics() {
        PrintWriter(FileWriter(statsFile, true)).use { writer ->
            writer.println(getNetworkStatistics())
        }
        Log.d("NetworkStatistics", "Network statistics exported")
    }

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main + job!!
}
