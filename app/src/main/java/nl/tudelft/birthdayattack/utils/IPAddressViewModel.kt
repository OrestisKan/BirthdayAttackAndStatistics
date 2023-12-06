package nl.tudelft.birthdayattack.utils

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class IPAddressViewModel : ViewModel() {

    val ipLiveData = MutableLiveData<String>()

    @OptIn(DelicateCoroutinesApi::class)
    fun retrieveIpAddress() {
        // Use the IO dispatcher since network operations are IO-bound
        GlobalScope.launch(Dispatchers.IO) {
            val ipAddress = getIpAddress()
            // Switch to the main dispatcher to update the UI
            withContext(Dispatchers.Main) {
                ipLiveData.value = ipAddress
            }
        }
    }

    private fun getIpAddress(): String {
        return try {
            val url = URL("https://httpbin.org/ip")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            // Read the response
            val bufferedReader = BufferedReader(InputStreamReader(connection.inputStream))
            val response = bufferedReader.readText()

            // Extract the IP address from the response
            val jsonObject = JSONObject(response)
            val ipAddress = jsonObject.getString("origin")

            bufferedReader.close()
            ipAddress
        } catch (e: Exception) {
            println("Error getting the IP address $e")
            "Error getting IP address"
        }
    }
}