package nl.tudelft.birthdayattack.utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

data class Packet(
    val senderId: String,
    val packageId: String,
    val sentTimestamp: Long,
    val packageType: PackageType,
    val isResponse: Boolean,
    val count: Int?,
    val receivedTimestamp: Long?,
    val data: String?
) : Serializable {
    companion object {
        fun serialize(pack: Packet): ByteArray {
            val byteArrayOutputStream = ByteArrayOutputStream()
            val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
            objectOutputStream.writeObject(pack)
            return byteArrayOutputStream.toByteArray()
        }

        fun deserialize(data: ByteArray): Packet {
            val byteArrayInputStream = ByteArrayInputStream(data)
            val objectInputStream = ObjectInputStream(byteArrayInputStream)
            return objectInputStream.readObject() as Packet
        }
    }
}