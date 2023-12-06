package nl.tudelft.birthdayattack.stun

import java.net.*
import javax.xml.bind.DatatypeConverter
import java.nio.charset.StandardCharsets

/**
 *
 * Adapted to Kotlin from Python from https://github.com/talkiq/pystun3/blob/master/stun/__init__.py
 *
 */

val STUN_SERVERS = arrayOf(
    "stun.ekiga.net",
    "stun.ideasip.com",
    "stun.voiparound.com",
    "stun.voipbuster.com",
    "stun.voipstunt.com",
    "stun.voxgratia.org"
)

const val MappedAddress = "0001"
const val ChangeRequest = "0003"
const val SourceAddress = "0004"
const val ChangedAddress = "0005"


const val BindRequestMsg = "0001"
const val BindResponseMsg = "0101"
const val BindErrorResponseMsg = "0111"
const val SharedSecretRequestMsg = "0002"
const val SharedSecretResponseMsg = "0102"
const val SharedSecretErrorResponseMsg = "0112"


val dictMsgTypeToVal = mapOf(
    "BindRequestMsg" to BindRequestMsg,
    "BindResponseMsg" to BindResponseMsg,
    "BindErrorResponseMsg" to BindErrorResponseMsg,
    "SharedSecretRequestMsg" to SharedSecretRequestMsg,
    "SharedSecretResponseMsg" to SharedSecretResponseMsg,
    "SharedSecretErrorResponseMsg" to SharedSecretErrorResponseMsg
)

val dictValToMsgType = dictMsgTypeToVal.entries.associateBy({ it.value }) { it.key }

const val Blocked = "Blocked"
const val OpenInternet = "Open Internet"
const val FullCone = "Full Cone"
const val SymmetricUDPFirewall = "Symmetric UDP Firewall"
const val RestrictNAT = "Restrict NAT"
const val RestrictPortNAT = "Restrict Port NAT"
const val SymmetricNAT = "Symmetric NAT"
const val ChangedAddressError = "Meet an error, when do Test1 on Changed IP and Port"


fun b2aHexstr(abytes: ByteArray): String {
    val hexBytes = DatatypeConverter.printHexBinary(abytes)
    return String(hexBytes.toByteArray(), StandardCharsets.US_ASCII)
}

fun genTranId(): String {
    return (1..32)
        .map { listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F').random() }
        .joinToString("")
}

fun stunTest(
    sock: DatagramSocket,
    host: String?,
    port: Int,
    sendData: String = ""
): Map<String, Any?> {
    val retVal = mutableMapOf<String, Any?>(
        "Resp" to false,
        "ExternalIP" to null,
        "ExternalPort" to null,
        "SourceIP" to null,
        "SourcePort" to null,
        "ChangedIP" to null,
        "ChangedPort" to null
    )
    println(sendData.length/2)
    val strLen = String.format("%04d", sendData.length / 2)
    val tranid = genTranId()
    val strData = listOf(BindRequestMsg, strLen, tranid, sendData).joinToString("")
    val data = DatatypeConverter.parseHexBinary(strData)
    var recvCorr = false
    var buf = ByteArray(2048)
    while (!recvCorr) {
        var received = false
        var count = 3
        while (!received) {
            println("send to: $host, $port")
            try {
                val packet = DatagramPacket(data, data.size, InetAddress.getByName(host), port)
                sock.send(packet)
            } catch (e: SocketException) {
                retVal["Resp"] = false
                return retVal
            }

            try {
                buf = ByteArray(2048)
                val packet = DatagramPacket(buf, buf.size)
                sock.receive(packet)
                println("receive from: ${packet.address.hostAddress}:${packet.port}")
                received = true
            } catch (e: Exception) {
                received = false
                if (count > 0) {
                    count--
                } else {
                    retVal["Resp"] = false
                    return retVal
                }
            }
        }

        val msgtype = b2aHexstr(buf.sliceArray(0 until 2))

        val bindRespMsg = dictValToMsgType[msgtype] == "BindResponseMsg"
        val tranidMatch = tranid.uppercase() == b2aHexstr(buf.sliceArray(4 until 20)).uppercase()
        if (bindRespMsg && tranidMatch) {
            recvCorr = true
            retVal["Resp"] = true
            val lenMessage = Integer.parseInt(b2aHexstr(buf.sliceArray(2 until 4)), 16)
            var lenRemain = lenMessage
            var base = 20
            var port1: Int
            while (lenRemain > 0) {
                val attrType = b2aHexstr(buf.sliceArray(base until (base + 2)))
                val attrLen = Integer.parseInt(b2aHexstr(buf.sliceArray((base + 2) until (base + 4))), 16)

                if (attrType == MappedAddress) {
                    port1 = Integer.parseInt(b2aHexstr(buf.sliceArray((base + 6) until (base + 8))), 16)
                    val ip = buf.sliceArray((base + 8) until (base + 12))
                        .joinToString(".") { Integer.parseInt(b2aHexstr(byteArrayOf(it)), 16).toString() }
                    retVal["ExternalIP"] = ip
                    retVal["ExternalPort"] = port1
                }

                if (attrType == SourceAddress) {
                    port1 = Integer.parseInt(b2aHexstr(buf.sliceArray((base + 6) until (base + 8))), 16)
                    val ip = buf.sliceArray((base + 8) until (base + 12))
                        .joinToString(".") { Integer.parseInt(b2aHexstr(byteArrayOf(it)), 16).toString() }
                    retVal["SourceIP"] = ip
                    retVal["SourcePort"] = port1
                }

                if (attrType == ChangedAddress) {
                    port1 = Integer.parseInt(b2aHexstr(buf.sliceArray((base + 6) until (base + 8))), 16)
                    val ip = buf.sliceArray((base + 8) until (base + 12))
                        .joinToString(".") { Integer.parseInt(b2aHexstr(byteArrayOf(it)), 16).toString() }
                    retVal["ChangedIP"] = ip
                    retVal["ChangedPort"] = port1
                }

                base += 4 + attrLen
                lenRemain -= (4 + attrLen)
            }
        }
    }
    return retVal
}

fun getNatType(
    s: DatagramSocket,
    sourceIp: String,
    stunHost: String? = null,
    stunPort: Int = 3478
): Pair<String, Map<String, Any?>> {
    println("Do Test1")
    var resp = false
    var ret = mapOf<String, Any?>()
    var stunHost1 = stunHost
    if (stunHost1 != null) {
        ret = stunTest(s, stunHost1, stunPort)
        resp = ret["Resp"] as Boolean
    } else {
        for (stunServer in STUN_SERVERS) {
            println("Trying STUN host: $stunServer")
            ret = stunTest(s, stunServer, stunPort)
            resp = ret["Resp"] as Boolean
            if (resp) {
                stunHost1 = stunServer
                break
            }
        }

    }

    if (!resp) {
        return Pair(Blocked, ret)
    }

    println("Result: $ret")
    val exIP = ret["ExternalIP"] as String
    val exPort = ret["ExternalPort"] as Int
    val changedIP = ret["ChangedIP"] as String
    val changedPort = ret["ChangedPort"] as Int

    return if (exIP == sourceIp) {
        val changeRequest = "${ChangeRequest}0004" + "00000006"
        ret = stunTest(s, stunHost1, stunPort, changeRequest)
        if (ret["Resp"] as Boolean) {
            Pair(OpenInternet, ret)
        } else {
            Pair(SymmetricUDPFirewall, ret)
        }
    } else {
        val changeRequest = "${ChangeRequest}0004" + "00000006"
        println("Do Test2")
        ret = stunTest(s, stunHost1, stunPort, changeRequest)
        println("Result: $ret")
        if (ret["Resp"] as Boolean) {
            Pair(FullCone, ret)
        } else {
            println("Do Test1")
            ret = stunTest(s, changedIP, changedPort)
            println("Result: $ret")
            if (!(ret["Resp"] as Boolean)) {
                Pair(ChangedAddressError, ret)
            } else {
                if (exIP == ret["ExternalIP"] && exPort == ret["ExternalPort"]) {
                    val changePortRequest = "${ChangeRequest}0004" + "00000002"
                    println("Do Test3")
                    ret = stunTest(s, changedIP, stunPort, changePortRequest)
                    println("Result: $ret")
                    if (ret["Resp"] as Boolean) {
                        Pair(RestrictNAT, ret)
                    } else {
                        Pair(RestrictPortNAT, ret)
                    }
                } else {
                    Pair(SymmetricNAT, ret)
                }
            }
        }
    }
}

fun getIpInfo(sourceIp: String = "0.0.0.0", sourcePort: Int = 54320, stunHost: String? = null, stunPort: Int = 3478): Triple<String?, String?, Int?> {
    val s = DatagramSocket(sourcePort, InetAddress.getByName(sourceIp))
    s.soTimeout = 2000

    val (natType, nat) = getNatType(s, sourceIp, stunHost, stunPort)
    println(nat)
    println(natType)
    val externalIp = nat["ExternalIP"] as String?
    val externalPort = nat["ExternalPort"] as Int?

    s.close()

    return Triple(natType, externalIp, externalPort)
}

fun main() {
    val res: Triple<String?, String?, Int?> = getIpInfo()
    print("${res.first} ${res.second} ${res.third}")
}
