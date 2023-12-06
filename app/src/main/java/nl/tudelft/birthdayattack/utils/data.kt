@file:UseContextualSerialization(InetSocketAddress::class)
package nl.tudelft.birthdayattack.utils

import kotlinx.serialization.*
import java.net.InetSocketAddress

@Serializable
data class TimeoutMeasurementJSON(
    val inetSocketAddress: InetSocketAddress
)