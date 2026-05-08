package com.kstream.core.network.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.Serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

// Serializer to handle ISO 8601 strings and convert them to Long (epoch milliseconds)
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class) // Added opt-in annotation
object DateTimeAsLongSerializer : KSerializer<Long?> {
    // A formatter that can parse the ISO 8601 format with timezone offset
    private val formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("Long?", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Long?) {
        // We don't need to serialize from Long to String in this case,
        // as we are primarily concerned with deserialization from API response.
        // If needed, this could be implemented to convert Long to String.
        if (value == null) {
            encoder.encodeNull()
        } else {
            // Convert Long (epoch millis) to Instant, then format to ISO string if needed for sending
            // For now, focus on deserialization
            encoder.encodeLong(value)
        }
    }

    override fun deserialize(decoder: Decoder): Long? {
        val stringValue = decoder.decodeString() // Decode as String first
        return try {
            // Parse the ISO 8601 string to Instant
            val instant = Instant.parse(stringValue)
            // Convert Instant to epoch milliseconds (Long)
            instant.toEpochMilli()
        } catch (e: DateTimeParseException) {
            // Handle cases where the string might not be a valid ISO 8601 format
            // or if it's an unexpected format. Log error or return null.
            // For simplicity, we return null on error.
            println("DateTimeAsLongSerializer: Failed to parse date string '$stringValue': ${e.message}")
            null
        } catch (e: Exception) {
            println("DateTimeAsLongSerializer: Unexpected error parsing date string '$stringValue': ${e.message}")
            null
        }
    }
}
