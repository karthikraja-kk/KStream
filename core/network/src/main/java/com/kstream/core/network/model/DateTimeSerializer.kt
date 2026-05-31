package com.kstream.core.network.model

import android.util.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// Serializer to handle ISO 8601 strings and convert them to Long (epoch milliseconds)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
object DateTimeAsLongSerializer : KSerializer<Long?> {
    private val isoFormats = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss'Z'"
    )

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Long?", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeLong(value)
        }
    }

    override fun deserialize(decoder: Decoder): Long? {
        val stringValue = decoder.decodeString()
        return parseIso8601(stringValue)
    }

    private fun parseIso8601(value: String): Long? {
        for (pattern in isoFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(value)?.time
            } catch (_: Exception) { }
        }
        Log.d("DateTimeSerializer", "Failed to parse date string '$value'")
        return null
    }
}
