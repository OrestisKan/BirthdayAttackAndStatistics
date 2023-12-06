package nl.tudelft.birthdayattack.utils

import java.util.Random

fun isValidIpAddress(ipAddress: String): Boolean {
    val ipv4Regex =
        """^(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)\.(25[0-5]|2[0-4][0-9]|[0-1]?[0-9][0-9]?)$""".toRegex()

    val ipv6Regex =
        """^(([0-9a-fA-F]{1,4}:){7,7}[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,7}:|([0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|([0-9a-fA-F]{1,4}:){1,5}(:[0-9a-fA-F]{1,4}){1,2}|([0-9a-fA-F]{1,4}:){1,4}(:[0-9a-fA-F]{1,4}){1,3}|([0-9a-fA-F]{1,4}:){1,3}(:[0-9a-fA-F]{1,4}){1,4}|([0-9a-fA-F]{1,4}:){1,2}(:[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:((:[0-9a-fA-F]{1,4}){1,6})|:((:[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(:[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(ffff(:0{1,4}){0,1}:){0,1}((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])|([0-9a-fA-F]{1,4}:){1,4}:((25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9])\.){3,3}(25[0-5]|(2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$""".toRegex()

    return ipAddress.matches(ipv4Regex) || ipAddress.matches(ipv6Regex)
}


fun isValidTimeoutMeasurementResponse(packetSend: Packet, packetReceived: Packet): Boolean {
    //todo maybe do something with packageID?
    return packetReceived.isResponse && packetReceived.receivedTimestamp == packetSend.sentTimestamp && packetReceived.packageType == PackageType.TIMEOUT_MEASUREMENT
}

fun generateRandomString(length: Int): String {
    val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9') // Add more characters as needed
    val random = Random()
    return (1..length)
        .map { charPool[random.nextInt(charPool.size)] }
        .joinToString("")
}

fun getNextPortToTry(portsAttempted:MutableSet<Int>): Int { //Theoretically can be done incrementally but then loses the randomness??
    val rand = Random()
    val range = 65535

    var random: Int = rand.nextInt(range) + 1
//    println(portsAttempted.size)
    while (portsAttempted.contains(random)) { //todo this runs out at some point or its just stack in the loop ?
        random = rand.nextInt(range) + 1
    }
    return random
}